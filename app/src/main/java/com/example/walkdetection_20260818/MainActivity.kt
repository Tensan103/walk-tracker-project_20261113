package com.example.walkdetection_20260818  // ← ★この1行（ご自身のプロジェクトのパッケージ名）を一番上に追加！
// ★ご注意：1行目の package com.example.ご自身のプロジェクト名 は残してください！

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    // 権限リクエスト用のランチャー
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("SensorApp", "歩行検知権限が許可されました")
            } else {
                Toast.makeText(this, "歩行検知センサーを利用するには権限が必要です", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 10以降の場合、歩行検知(ACTIVITY_RECOGNITION)の権限を要求
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SensorLoggerScreen()
                }
            }
        }
    }
}

@Composable
fun SensorLoggerScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --- 状態管理（画面表示用） ---
    var isRecording by remember { mutableStateOf(false) }
    var accelValues by remember { mutableStateOf(FloatArray(3) { 0f }) }
    var stepCount by remember { mutableIntStateOf(0) }
    var logFilePath by remember { mutableStateOf("") }

    // --- ファイル書き込み管理 ---
    // BufferedWriterを保持する変数。nullなら書き込み停止状態
    var writer by remember { mutableStateOf<BufferedWriter?>(null) }

    // ログを書き込む関数
    fun writeLog(type: String, eventTimestampNs: Long, values: FloatArray) {
        if (writer == null) return

        // タイムスタンプ, センサー種別, X/歩数, Y, Z
        val csvLine = "$eventTimestampNs,$type,${values.joinToString(",")}\n"

        coroutineScope.launch(Dispatchers.IO) { // UIスレッドをブロックしないよう非同期で書き込み
            try {
                writer?.write(csvLine)
            } catch (e: IOException) {
                Log.e("SensorApp", "ログ書き込みエラー: ${e.message}")
            }
        }
    }

    // --- センサー監視と記録のトグル処理 ---
    fun toggleRecording() {
        if (!isRecording) {
            // ★記録開始の処理
            try {
                // ファイル名の作成（例: SensorLog_20260911_123456.csv）
                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val fileName = "SensorLog_${sdf.format(Date())}.csv"

                // アプリ固有のディレクトリに保存（PCからアクセス可能）
                val dir = context.getExternalFilesDir(null)
                val file = File(dir, fileName)

                writer = BufferedWriter(FileWriter(file, true)) // 追記モードで開く

                // ヘッダー行を書き込む
                writer?.write("timestamp_ns,sensor_type,val_1,val_2,val_3\n")

                logFilePath = file.absolutePath
                stepCount = 0 // 歩数をリセット
                isRecording = true
                Toast.makeText(context, "記録を開始しました", Toast.LENGTH_SHORT).show()

                // 「手動操作：開始」のログ
                writeLog("TOGGLE_ON", System.nanoTime(), floatArrayOf(0f, 0f, 0f))

            } catch (e: IOException) {
                Toast.makeText(context, "ファイルの作成に失敗しました", Toast.LENGTH_SHORT).show()
                Log.e("SensorApp", "ファイル作成エラー: ${e.message}")
            }
        } else {
            // ★記録終了の処理
            isRecording = false
            // 「手動操作：終了」のログ
            writeLog("TOGGLE_OFF", System.nanoTime(), floatArrayOf(0f, 0f, 0f))

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    writer?.flush()
                    writer?.close()
                    writer = null
                } catch (e: IOException) {
                    Log.e("SensorApp", "ファイルクローズエラー: ${e.message}")
                }
            }
            Toast.makeText(context, "記録を終了しました\n$logFilePath", Toast.LENGTH_LONG).show()
        }
    }

    // --- センサーリスナーの登録 ---
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return

                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        // 画面表示用のState更新
                        accelValues = event.values.clone()
                        // 記録中ならCSVへ書き込み
                        if (isRecording) {
                            writeLog("ACCEL", event.timestamp, event.values)
                        }
                    }
                    Sensor.TYPE_STEP_DETECTOR -> {
                        // DETECTORは1歩検知で値「1.0」が返る
                        if (event.values[0] == 1.0f) {
                            stepCount += 1 // 画面表示用にカウントアップ
                            if (isRecording) {
                                // 歩行ログは val_1 に1を記録し、残りは0とする
                                writeLog("STEP", event.timestamp, floatArrayOf(1f, 0f, 0f))
                            }
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // リスナーの登録
        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI)

        // 歩行検知センサーが存在する場合のみ登録
        if (stepDetector != null) {
            sensorManager.registerListener(sensorEventListener, stepDetector, SensorManager.SENSOR_DELAY_UI)
        } else {
            Toast.makeText(context, "この端末には歩行検知センサーがありません", Toast.LENGTH_LONG).show()
        }

        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
            // アプリ終了時にファイルを確実に閉じる
            if (isRecording) {
                try {
                    writer?.flush()
                    writer?.close()
                } catch (e: IOException) {}
            }
        }
    }

    // --- UI部分の構築 ---
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "センサーロガー", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(32.dp))

        // 加速度センサーの表示
        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "加速度センサー", style = MaterialTheme.typography.titleMedium)
                Text(text = "X軸: ${String.format("%.2f", accelValues[0])}")
                Text(text = "Y軸: ${String.format("%.2f", accelValues[1])}")
                Text(text = "Z軸: ${String.format("%.2f", accelValues[2])}")
            }
        }

        // 歩行検知の表示
        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "歩行検知 (STEP_DETECTOR)", style = MaterialTheme.typography.titleMedium)
                Text(text = "検知回数: $stepCount 歩", style = MaterialTheme.typography.headlineSmall)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 記録開始/終了ボタン
        Button(
            onClick = { toggleRecording() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text(text = if (isRecording) "■ 記録を停止" else "▶ 記録を開始")
        }

        if (logFilePath.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "保存先: \n$logFilePath", style = MaterialTheme.typography.bodySmall)
        }
    }
}