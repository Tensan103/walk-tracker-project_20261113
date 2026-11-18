package com.example.walkdetection_20260818

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
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

    private val awsApiUrl = "https://4ss98ipt4m.execute-api.ap-northeast-3.amazonaws.com/prod/events"

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

        serviceScope.cancel()

        SensorStateRepository.updateServiceRunning(false)
        SensorStateRepository.updateLoggingEnabled(false)
        SensorStateRepository.updateIsWalking(false)

        super.onDestroy()
        Log.d(TAG, "SensorTrackingService が停止しました")
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
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "歩行検知サービス",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "歩行検知システムがバックグラウンドで稼働中です"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
                serviceScope.launch { sendEventToAWS("DETECT") }
            }
        }

        // 解除タイムアウト: 最後の歩行から rSec 秒後に「監視中」へ戻す
        walkingTimeoutJob?.cancel()
        walkingTimeoutJob = serviceScope.launch {
            delay(rSec.seconds)   // ← tSec ではなく rSec を使用
            SensorStateRepository.updateIsWalking(false)
            stepTimestamps.clear()
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
