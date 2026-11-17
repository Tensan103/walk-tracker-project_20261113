package com.example.walkdetection_20260818  // ← ★この1行（ご自身のプロジェクトのパッケージ名）を一番上に追加！
// ★ご注意：1行目の package com.example.ご自身のプロジェクト名 は残してください！

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) Log.d("SensorApp", "歩行検知権限が許可されました")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorLoggerScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --- パラメータ設定 ---
    val T_SEC = 5 // T秒
    val S_STEPS = 3 // S歩

    // --- 状態管理 ---
    var isRecording by remember { mutableStateOf(false) }
    var accelValues by remember { mutableStateOf(FloatArray(3) { 0f }) }
    var stepCount by remember { mutableIntStateOf(0) }
    var logFilePath by remember { mutableStateOf("") }

    // R2: ファイル名に追加する任意の文字列
    var fileSuffixText by remember { mutableStateOf("") }

    // R3: 歩行検知ロジック用の状態
    var isWalkingAlert by remember { mutableStateOf(false) } // 画面アラート表示フラグ
    val stepTimestamps = remember { mutableListOf<Long>() } // 歩行検知した時刻のリスト
    var walkingTimeoutJob by remember { mutableStateOf<Job?>(null) } // アラート解除タイマー用ジョブ

    var writer by remember { mutableStateOf<BufferedWriter?>(null) }

    // ログを書き込む関数（R1: 引数に currentSteps を追加）
    fun writeLog(type: String, eventTimestampNs: Long, values: FloatArray, currentSteps: Int) {
        if (writer == null) return

        // タイムスタンプ, センサー種別, X/歩数, Y, Z, 累計歩数
        val csvLine = "$eventTimestampNs,$type,${values.joinToString(",")},$currentSteps\n"

        coroutineScope.launch(Dispatchers.IO) {
            try {
                writer?.write(csvLine)
            } catch (e: IOException) {
                Log.e("SensorApp", "ログ書き込みエラー: ${e.message}")
            }
        }
    }

    fun toggleRecording() {
        if (!isRecording) {
            try {
                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val timeStr = sdf.format(Date())

                // R2: テキストボックスの値をファイル名に反映（ファイル名に使えない記号はアンダーバーに置換）
                val safeSuffix = fileSuffixText.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val suffixStr = if (safeSuffix.isNotBlank()) "_$safeSuffix" else ""
                val fileName = "SensorLog_${timeStr}${suffixStr}.csv"

                val dir = context.getExternalFilesDir(null)
                val file = File(dir, fileName)
                writer = BufferedWriter(FileWriter(file, true))

                // ヘッダーに total_steps を追加
                writer?.write("timestamp_ns,sensor_type,val_1,val_2,val_3,total_steps\n")

                logFilePath = file.absolutePath
                stepCount = 0
                stepTimestamps.clear()
                isWalkingAlert = false
                isRecording = true
                Toast.makeText(context, "記録を開始しました", Toast.LENGTH_SHORT).show()

                writeLog("TOGGLE_ON", System.nanoTime(), floatArrayOf(0f, 0f, 0f), stepCount)

            } catch (e: IOException) {
                Toast.makeText(context, "ファイルの作成に失敗しました", Toast.LENGTH_SHORT).show()
            }
        } else {
            isRecording = false
            writeLog("TOGGLE_OFF", System.nanoTime(), floatArrayOf(0f, 0f, 0f), stepCount)

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    writer?.flush()
                    writer?.close()
                    writer = null
                } catch (e: IOException) {}
            }
            Toast.makeText(context, "記録を終了しました", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return

                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        accelValues = event.values.clone()
                        if (isRecording) {
                            writeLog("ACCEL", event.timestamp, event.values, stepCount)
                        }
                    }
                    Sensor.TYPE_STEP_DETECTOR -> {
                        if (event.values[0] == 1.0f) {
                            stepCount += 1
                            if (isRecording) {
                                writeLog("STEP", event.timestamp, floatArrayOf(1f, 0f, 0f), stepCount)
                            }

                            // --- R3: 歩行検知ロジック ---
                            val currentTimeMs = System.currentTimeMillis()
                            stepTimestamps.add(currentTimeMs)

                            // T秒(5000ミリ秒)より古いデータをリストから削除
                            stepTimestamps.removeAll { currentTimeMs - it > (T_SEC * 1000) }

                            // S歩(3歩)以上カウントされたか判定
                            if (stepTimestamps.size >= S_STEPS) {
                                if (!isWalkingAlert) {
                                    isWalkingAlert = true
                                    Toast.makeText(context, "歩行検知", Toast.LENGTH_SHORT).show()
                                }
                            }

                            // 既存の解除タイマーがあればキャンセル
                            walkingTimeoutJob?.cancel()

                            // 新しくT秒後にアラートを解除するタイマーをセット
                            walkingTimeoutJob = coroutineScope.launch {
                                delay(T_SEC * 1000L) // 5秒待機
                                isWalkingAlert = false // 5秒間新しい歩行がなければアラート消去
                                stepTimestamps.clear() // リストもリセット
                            }
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        if (stepDetector != null) {
            sensorManager.registerListener(sensorEventListener, stepDetector, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
            if (isRecording) {
                try {
                    writer?.flush()
                    writer?.close()
                } catch (e: IOException) {}
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "歩行検知デモ", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // --- R3: 歩行検知アラート表示 ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(if (isWalkingAlert) Color.Red else Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isWalkingAlert) "歩行検知！！" else "待機中...",
                color = if (isWalkingAlert) Color.White else Color.DarkGray,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
/*
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "加速度センサー", style = MaterialTheme.typography.titleMedium)
                Text(text = "X軸: ${String.format("%.2f", accelValues[0])}")
                Text(text = "Y軸: ${String.format("%.2f", accelValues[1])}")
                Text(text = "Z軸: ${String.format("%.2f", accelValues[2])}")
            }
        }
*/
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "累計歩数 (STEP_DETECTOR)", style = MaterialTheme.typography.titleMedium)
                Text(text = "$stepCount 歩", style = MaterialTheme.typography.headlineSmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
/*
        // --- R2: ファイル名用の任意の文字列入力 ---
        OutlinedTextField(
            value = fileSuffixText,
            onValueChange = { fileSuffixText = it },
            label = { Text("検証メモ（ファイル名に追加）") },
            placeholder = { Text("例: カバンの中_歩行") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRecording // 記録中は変更不可にする
        )

        Spacer(modifier = Modifier.height(16.dp))

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
 */
    }
}