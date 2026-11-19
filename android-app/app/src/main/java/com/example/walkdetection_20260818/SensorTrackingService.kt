package com.example.walkdetection_20260818

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/**
 * センサー処理を画面オフ時にも継続するための Foreground Service。
 *
 * 状態の2階層:
 *  1. サービス稼働 (ACTION_START / ACTION_STOP): センサー監視・歩行判定・AWS送信
 *  2. ロギング (ACTION_START_LOGGING / ACTION_STOP_LOGGING): CSVファイルへの書き込み
 *
 * LoggerScreen から ACTION_START_LOGGING を受け取るとロギングを開始し、
 * サービスが未起動の場合は自動的に起動する。
 * これにより MainScreen の「歩行判定」と LoggerScreen の「CSV記録」が独立して動作する。
 */
class SensorTrackingService : Service() {

    private lateinit var sensorManager: SensorManager
    private var sensorEventListener: SensorEventListener? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var writer: BufferedWriter? = null
    private val okHttpClient = OkHttpClient()

    // スライディングウィンドウ用フィールド (センサーコールバックから排他的にアクセス)
    private val stepTimestamps = mutableListOf<Long>()
    private var walkingTimeoutJob: Job? = null
    private var stepCount = 0

    // 二重初期化防止フラグ (START / START_LOGGING が重複しても安全)
    private var isInitialized = false

    // DETECT が発生した時刻。RELEASE 時に履歴を生成するために保持する。
    private var detectStartMs: Long? = null
    // DETECT が発生した時点の累計歩数。歩数差分を計算するために保持する。
    private var detectStartStepCount: Int? = null

    // SYSTEM_ALERT_WINDOW オーバーレイ View の参照 (表示中は非 null)
    private var overlayView: android.view.View? = null

    private val awsApiUrl = "https://hv9doulbs3.execute-api.ap-northeast-1.amazonaws.com/prod/events"

    companion object {
        private const val TAG = "SensorTrackingService"
        private const val NOTIFICATION_CHANNEL_ID = "walk_tracking_channel"
        private const val NOTIFICATION_ID = 1001

        /** MainScreen からサービスを起動する Action (センサー監視のみ、CSVロギングなし) */
        const val ACTION_START = "com.example.walkdetection_20260818.ACTION_START"

        /** MainScreen / 任意からサービスを完全停止する Action */
        const val ACTION_STOP = "com.example.walkdetection_20260818.ACTION_STOP"

        /** LoggerScreen から CSV ロギングを開始する Action (サービス未起動なら自動起動) */
        const val ACTION_START_LOGGING = "com.example.walkdetection_20260818.ACTION_START_LOGGING"

        /** LoggerScreen から CSV ロギングのみ停止する Action (サービスは継続) */
        const val ACTION_STOP_LOGGING = "com.example.walkdetection_20260818.ACTION_STOP_LOGGING"

        /** ACTION_START_LOGGING に付加するメモ文字列の Extra キー */
        const val EXTRA_MEMO = "extra_memo"

        private const val ALERT_CHANNEL_ID = "walk_alert_channel"
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val ALERT_REQUEST_CODE = 101
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_LOGGING -> {
                stopLogging()
            }
            ACTION_START_LOGGING -> {
                // サービス未起動なら自動起動 (センサー登録など)
                if (!isInitialized) initializeService()
                val memo = intent.getStringExtra(EXTRA_MEMO) ?: ""
                startLogging(memo)
            }
            else -> {
                // ACTION_START または OS による再起動 (START_STICKY)
                if (!isInitialized) initializeService()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // 歩行検知中に停止された場合、RELEASE を強制送信して履歴を記録する
        if (SensorStateRepository.isWalking.value) {
            val releaseMs = System.currentTimeMillis()
            val detectMs = detectStartMs
            if (detectMs != null) {
                val durationSec = ((releaseMs - detectMs) / 1000L).toInt().coerceAtLeast(0)
                val stepsWalked = detectStartStepCount?.let { stepCount - it }?.coerceAtLeast(0) ?: 0
                SensorStateRepository.addWalkHistoryItem(
                    WalkHistoryItem(detectMs, releaseMs, durationSec, stepsWalked)
                )
                detectStartMs = null
                detectStartStepCount = null
            }
            // serviceScope はこの後 cancel するため、プロセス寿命の applicationScope で送信する
            (applicationContext as WalkTrackerApplication).applicationScope.launch {
                sendEventToAWS("RELEASE")
            }
            Log.d(TAG, "onDestroy: 検知中のため RELEASE を強制送信")
        }

        // センサー解除
        sensorEventListener?.let { sensorManager.unregisterListener(it) }
        walkingTimeoutJob?.cancel()

        // ロギング中であれば同期的にファイルを閉じる
        if (SensorStateRepository.isLoggingEnabled.value) {
            try {
                writer?.write("${System.nanoTime()},TOGGLE_OFF,0.0,0.0,0.0,$stepCount\n")
                writer?.flush()
                writer?.close()
            } catch (_: IOException) {}
            writer = null
        }

        removeOverlay()
        serviceScope.cancel()

        SensorStateRepository.updateServiceRunning(false)
        SensorStateRepository.updateLoggingEnabled(false)
        SensorStateRepository.updateIsWalking(false)

        super.onDestroy()
        Log.d(TAG, "SensorTrackingService が停止しました")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // スワイプアウト時もサービスを停止しない。START_STICKY により OS が再起動する。
        Log.d(TAG, "タスクが削除されましたが、サービスは継続します")
        super.onTaskRemoved(rootIntent)
    }

    // -------------------------------------------------------------------------
    // Service initialization
    // -------------------------------------------------------------------------

    private fun initializeService() {
        startAsForeground()
        SensorStateRepository.reset()
        SensorStateRepository.updateServiceRunning(true)
        registerSensors()
        isInitialized = true
        Log.d(TAG, "Service initialized")
    }

    // -------------------------------------------------------------------------
    // Foreground notification
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "歩行検知サービス",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "歩行検知システムがバックグラウンドで稼働中です" }
        )
        // Full-Screen Intent 用の高優先度チャンネル
        nm.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "歩行検知アラート",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "歩行検知時に前面表示するための高優先度通知" }
        )
    }

    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("歩行検知システム稼働中")
            .setContentText("センサーを監視しています")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "Foreground Service 起動")
    }

    // -------------------------------------------------------------------------
    // Sensor registration
    // -------------------------------------------------------------------------

    private fun registerSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                val tSec = SettingsRepository.getTSec(this@SensorTrackingService)
                val sSteps = SettingsRepository.getSSteps(this@SensorTrackingService)
                val rSec = SettingsRepository.getRSec(this@SensorTrackingService)

                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        SensorStateRepository.updateAccel(event.values)
                        // ロギングが有効な場合のみ CSV 書き込み
                        if (SensorStateRepository.isLoggingEnabled.value) {
                            writeLog("ACCEL", event.timestamp, event.values, stepCount)
                        }
                    }
                    Sensor.TYPE_STEP_DETECTOR -> {
                        if (event.values[0] == 1.0f) {
                            handleStep(event.timestamp, tSec, sSteps, rSec)
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        if (stepDetector != null) {
            sensorManager.registerListener(listener, stepDetector, SensorManager.SENSOR_DELAY_UI)
        } else {
            Log.w(TAG, "TYPE_STEP_DETECTOR 非対応デバイス")
        }
        sensorEventListener = listener
    }

    // -------------------------------------------------------------------------
    // Walking detection (スライディングウィンドウ)
    // -------------------------------------------------------------------------

    /**
     * @param tSec  スライディングウィンドウの幅 (秒)。この時間内に sSteps 歩で「検知」と判定する。
     * @param sSteps 検知に必要な歩数。
     * @param rSec  歩行検知後、最後の歩行から何秒経過したら「監視中」に戻すか (解除タイムアウト)。
     */
    private fun handleStep(eventTimestampNs: Long, tSec: Int, sSteps: Int, rSec: Int) {
        stepCount += 1
        SensorStateRepository.updateStepCount(stepCount)

        // ロギングが有効な場合のみ CSV 書き込み
        if (SensorStateRepository.isLoggingEnabled.value) {
            writeLog("STEP", eventTimestampNs, floatArrayOf(1f, 0f, 0f), stepCount)
        }

        val currentTimeMs = System.currentTimeMillis()
        stepTimestamps.add(currentTimeMs)
        // tSec ウィンドウより古いタイムスタンプを除去（検知判定用）
        stepTimestamps.removeAll { currentTimeMs - it > (tSec * 1000L) }

        if (stepTimestamps.size >= sSteps) {
            if (!SensorStateRepository.isWalking.value) {
                SensorStateRepository.updateIsWalking(true)
                detectStartMs = System.currentTimeMillis()   // DETECT 時刻を記録
                detectStartStepCount = stepCount             // DETECT 時の歩数を記録
                triggerAlert()
                serviceScope.launch { sendEventToAWS("DETECT") }
            }
        }

        // 解除タイムアウト: 最後の歩行から rSec 秒後に「監視中」へ戻す
        walkingTimeoutJob?.cancel()
        walkingTimeoutJob = serviceScope.launch {
            delay(rSec.seconds)   // ← tSec ではなく rSec を使用
            SensorStateRepository.updateIsWalking(false)
            stepTimestamps.clear()

            // 履歴を生成して Repository に追加
            val detectMs = detectStartMs
            if (detectMs != null) {
                val releaseMs = System.currentTimeMillis()
                val durationSec = ((releaseMs - detectMs) / 1000L).toInt().coerceAtLeast(0)
                val stepsWalked = detectStartStepCount?.let { stepCount - it }?.coerceAtLeast(0) ?: 0
                SensorStateRepository.addWalkHistoryItem(
                    WalkHistoryItem(detectMs, releaseMs, durationSec, stepsWalked)
                )
                detectStartMs = null
                detectStartStepCount = null
            }

            removeOverlay()
            sendEventToAWS("RELEASE")
        }
    }

    // -------------------------------------------------------------------------
    // CSV Logging
    // -------------------------------------------------------------------------

    /**
     * CSV ロギングを開始する。
     * @param memo ファイル名末尾に付けるメモ文字列 (ファイル名に使えない文字は _ に置換)
     */
    private fun startLogging(memo: String) {
        if (SensorStateRepository.isLoggingEnabled.value) {
            Log.w(TAG, "ロギングは既に開始されています")
            return
        }
        try {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val safeMemo = memo.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val suffix = if (safeMemo.isNotBlank()) "_$safeMemo" else ""
            val fileName = "SensorLog_${sdf.format(Date())}${suffix}.csv"
            val file = File(getExternalFilesDir(null), fileName)
            writer = BufferedWriter(FileWriter(file, true))
            writer?.write("timestamp_ns,sensor_type,val_1,val_2,val_3,total_steps\n")
            writer?.write("${System.nanoTime()},TOGGLE_ON,0.0,0.0,0.0,$stepCount\n")
            SensorStateRepository.updateLoggingEnabled(true)
            Log.d(TAG, "CSV ロギング開始: ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "ログファイル作成失敗: ${e.message}")
        }
    }

    /** CSV ロギングを停止し、ファイルを閉じる。 */
    private fun stopLogging() {
        if (!SensorStateRepository.isLoggingEnabled.value) return
        SensorStateRepository.updateLoggingEnabled(false)
        serviceScope.launch(Dispatchers.IO) {
            try {
                writer?.write("${System.nanoTime()},TOGGLE_OFF,0.0,0.0,0.0,$stepCount\n")
                writer?.flush()
                writer?.close()
            } catch (_: IOException) {}
            writer = null
        }
        Log.d(TAG, "CSV ロギング停止")
    }

    private fun writeLog(type: String, eventTimestampNs: Long, values: FloatArray, currentSteps: Int) {
        if (writer == null) return
        val csvLine = "$eventTimestampNs,$type,${values.joinToString(",")},$currentSteps\n"
        serviceScope.launch(Dispatchers.IO) {
            try {
                writer?.write(csvLine)
            } catch (e: IOException) {
                Log.e(TAG, "ログ書き込みエラー: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Alert dispatch (歩行検知時の警告)
    // -------------------------------------------------------------------------

    /** Repository の alertType に応じてアラートを実行する。センサーコールバック (メインスレッド) から呼ばれる想定。 */
    private fun triggerAlert() {
        when (SensorStateRepository.alertType.value) {
            AlertType.NONE -> { /* 何もしない */ }
            AlertType.TOAST -> showToastAlert()
            AlertType.FULL_SCREEN_INTENT -> showFullScreenAlert()
            AlertType.SYSTEM_ALERT_WINDOW -> showOverlayAlert()
        }
    }

    /** メインスレッドでトーストを表示する。 */
    private fun showToastAlert() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this@SensorTrackingService, "歩行を検知しました", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Full-Screen Intent 付き高優先度通知を発行してアプリを前面に表示する。
     *
     * Android 10+ のバックグラウンド起動制限を回避するために、
     * setFullScreenIntent() を持つ HIGH importance 通知を使う手法。
     * 画面オフ時はロック画面上に表示され、画面オン時はヘッズアップ通知として表示される。
     */
    private fun showFullScreenAlert() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, ALERT_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("歩行を検知しました")
            .setContentText("歩行が開始されました。タップしてアプリを確認してください。")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(ALERT_NOTIFICATION_ID, notification)
    }

    /**
     * SYSTEM_ALERT_WINDOW 権限を使い、最前面に警告 View を表示する。
     * 画面オン/オフ問わず他アプリの上に赤い警告を重ねる。
     * RELEASE 時またはタップ時に removeOverlay() で破棄する。
     */
    private fun showOverlayAlert() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW 権限がないためオーバーレイをスキップ")
            return
        }
        if (overlayView != null) return  // 既に表示中

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, // 画面いっぱいに広げる場合
            WindowManager.LayoutParams.MATCH_PARENT, // 縦はコンテンツの高さ
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // ←★これが必要！
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or // ★裏のアプリの操作を妨げないようにするフラグ
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120
        }

        // 表示位置の設定（例：画面上部に出す）
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

        val view = TextView(this).apply {
            text = "🚶 歩行検知！"
            textSize = 20f
            setBackgroundColor(Color.argb(220, 211, 47, 47))  // 半透明赤
            setTextColor(Color.WHITE)
            setPadding(48, 24, 48, 24)
            setOnClickListener { removeOverlay() }
        }

        overlayView = view
        wm.addView(view, params)
        Log.d(TAG, "オーバーレイ表示")
    }

    /** オーバーレイ View を WindowManager から取り外す (メインスレッドで実行)。 */
    private fun removeOverlay() {
        Handler(Looper.getMainLooper()).post {
            overlayView?.let { view ->
                try {
                    (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view)
                    Log.d(TAG, "オーバーレイ破棄")
                } catch (_: Exception) {}
                overlayView = null
            }
        }
    }

    // -------------------------------------------------------------------------
    // AWS API
    // -------------------------------------------------------------------------

    private suspend fun sendEventToAWS(category: String) {
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("deviceId", "pixel-7a-test")
                    put("category", category)
                    put("timestamp", System.currentTimeMillis())
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(awsApiUrl)
                    .post(body)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "AWS 送信成功: $category")
                    } else {
                        Log.e(TAG, "AWS API エラー: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AWS 通信例外: ${e.message}")
            }
        }
    }
}
