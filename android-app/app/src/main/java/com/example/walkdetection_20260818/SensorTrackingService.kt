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
 * SensorStateRepository (StateFlow) を介して MainActivity の Compose UI にリアルタイムで状態を配信する。
 */
class SensorTrackingService : Service() {

    private lateinit var sensorManager: SensorManager
    private var sensorEventListener: SensorEventListener? = null

    // Service 専用の CoroutineScope。onDestroy で cancel する。
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var writer: BufferedWriter? = null
    private val okHttpClient = OkHttpClient()

    // 歩行検知ロジック用のフィールド (SensorEventListener のコールバックから排他的にアクセス)
    private val stepTimestamps = mutableListOf<Long>()
    private var walkingTimeoutJob: Job? = null
    private var stepCount = 0

    // AWS API エンドポイント (CDK Output の ApiUrl + "/events")
    private val awsApiUrl = "https://4ss98ipt4m.execute-api.ap-northeast-3.amazonaws.com/prod/events"

    companion object {
        private const val TAG = "SensorTrackingService"
        private const val NOTIFICATION_CHANNEL_ID = "walk_tracking_channel"
        private const val NOTIFICATION_ID = 1001

        /** MainActivity から Service を起動する際に Intent の action にセットする定数 */
        const val ACTION_START = "com.example.walkdetection_20260818.ACTION_START"

        /** MainActivity から Service を停止する際に Intent の action にセットする定数 */
        const val ACTION_STOP = "com.example.walkdetection_20260818.ACTION_STOP"
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
                // 停止要求: stopSelf() -> onDestroy が呼ばれクリーンアップされる
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // 起動要求 (ACTION_START または再起動)
                startAsForeground()
                SensorStateRepository.reset()
                SensorStateRepository.updateServiceRunning(true)
                initializeLogFile()
                registerSensors()
            }
        }
        // クラッシュや強制終了後に OS がサービスを再起動する
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null  // Bound Service は使用しない

    override fun onDestroy() {
        // 1. センサー解除
        sensorEventListener?.let { sensorManager.unregisterListener(it) }
        walkingTimeoutJob?.cancel()

        // 2. 終了ログを直接書き込み (serviceScope は後でキャンセルするため IO で書く)
        try {
            writer?.write("${System.nanoTime()},TOGGLE_OFF,0.0,0.0,0.0,$stepCount\n")
            writer?.flush()
            writer?.close()
        } catch (_: IOException) {}
        writer = null

        // 3. CoroutineScope をキャンセル
        serviceScope.cancel()

        // 4. UI 状態をリセット
        SensorStateRepository.updateServiceRunning(false)
        SensorStateRepository.updateIsWalking(false)

        super.onDestroy()
        Log.d(TAG, "SensorTrackingService が停止しました")
    }

    // -------------------------------------------------------------------------
    // Foreground notification
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "歩行検知サービス",
            NotificationManager.IMPORTANCE_LOW  // LOW: 通知音・バイブなし、ステータスバーには表示
        ).apply {
            description = "歩行検知システムがバックグラウンドで稼働中です"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("歩行検知システム稼働中")
            .setContentText("センサーを監視しています")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)   // ユーザーがスワイプで消せないようにする
            .build()

        // Android 14 (API 34) 以降は foregroundServiceType の指定が必須
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "Foreground Service を開始しました")
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
                // 設定は毎回 SharedPreferences から読む (UI で変更されても即時反映)
                val tSec = SettingsRepository.getTSec(this@SensorTrackingService)
                val sSteps = SettingsRepository.getSSteps(this@SensorTrackingService)

                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        SensorStateRepository.updateAccel(event.values)
                        writeLog("ACCEL", event.timestamp, event.values, stepCount)
                    }
                    Sensor.TYPE_STEP_DETECTOR -> {
                        if (event.values[0] == 1.0f) {
                            handleStep(event.timestamp, tSec, sSteps)
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
            Log.w(TAG, "このデバイスは TYPE_STEP_DETECTOR をサポートしていません")
        }
        sensorEventListener = listener
    }

    // -------------------------------------------------------------------------
    // Walking detection logic (スライディングウィンドウ)
    // -------------------------------------------------------------------------

    private fun handleStep(eventTimestampNs: Long, tSec: Int, sSteps: Int) {
        stepCount += 1
        SensorStateRepository.updateStepCount(stepCount)
        writeLog("STEP", eventTimestampNs, floatArrayOf(1f, 0f, 0f), stepCount)

        val currentTimeMs = System.currentTimeMillis()
        stepTimestamps.add(currentTimeMs)

        // T秒より古い記録を削除
        stepTimestamps.removeAll { currentTimeMs - it > (tSec * 1000L) }

        // S歩以上で「歩行検知」と判定
        if (stepTimestamps.size >= sSteps) {
            if (!SensorStateRepository.isWalking.value) {
                SensorStateRepository.updateIsWalking(true)
                serviceScope.launch {
                    sendEventToAWS("DETECT")
                }
            }
        }

        // T秒間新たな歩行がなければ「歩行解除」
        walkingTimeoutJob?.cancel()
        walkingTimeoutJob = serviceScope.launch {
            delay(tSec.seconds)
            SensorStateRepository.updateIsWalking(false)
            stepTimestamps.clear()
            sendEventToAWS("RELEASE")
        }
    }

    // -------------------------------------------------------------------------
    // CSV log file
    // -------------------------------------------------------------------------

    private fun initializeLogFile() {
        try {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "SensorLog_${sdf.format(Date())}.csv"
            val file = File(getExternalFilesDir(null), fileName)
            writer = BufferedWriter(FileWriter(file, true))
            writer?.write("timestamp_ns,sensor_type,val_1,val_2,val_3,total_steps\n")
            writer?.write("${System.nanoTime()},TOGGLE_ON,0.0,0.0,0.0,0\n")
            Log.d(TAG, "ログファイルを作成: ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "ログファイルの作成に失敗: ${e.message}")
        }
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
                    put("category", category)   // "DETECT" or "RELEASE"
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
