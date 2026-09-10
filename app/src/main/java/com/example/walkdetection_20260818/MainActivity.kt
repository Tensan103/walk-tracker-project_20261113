package com.example.walkdetection_20260818  // ← ★この1行（ご自身のプロジェクトのパッケージ名）を一番上に追加！
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AccelerometerScreen()
                }
            }
        }
    }
}

@Composable
fun AccelerometerScreen() {
    val context = LocalContext.current

    // センサーの値を保持する状態（State）。値が変わると自動で画面が更新されます
    var sensorValues by remember { mutableStateOf(FloatArray(3) { 0f }) }

    // 画面が表示されている間だけセンサーを監視するための仕組み
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // センサーの値が変化したときに呼ばれるリスナー
        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                    // 取得したX, Y, Zの値をStateにコピーする
                    sensorValues = event.values.clone()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // 今回は精度変化のハンドリングは省略
            }
        }

        // リスナーを登録して監視スタート
        sensorManager.registerListener(
            sensorEventListener,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI // 画面描画向けの更新頻度
        )

        // 画面が破棄されるとき（バックグラウンドに回った時など）に監視をストップ
        // ※これを忘れるとバッテリーを激しく消耗します
        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }

    // UI部分の構築
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "加速度センサー", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        // %.2f で小数点第2位まで表示
        Text(text = "X軸: ${String.format("%.2f", sensorValues[0])}", style = MaterialTheme.typography.bodyLarge)
        Text(text = "Y軸: ${String.format("%.2f", sensorValues[1])}", style = MaterialTheme.typography.bodyLarge)
        Text(text = "Z軸: ${String.format("%.2f", sensorValues[2])}", style = MaterialTheme.typography.bodyLarge)
    }
}