package com.example.walkdetection_20260818

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// SettingsRepository はセンサーの検知パラメータ (T秒, S歩) を SharedPreferences で管理する。
// SensorTrackingService からも参照するため、このファイルに定義し続ける。
object SettingsRepository {
    private const val PREF_NAME = "walk_detection_prefs"
    private const val KEY_T_SEC = "t_sec"
    private const val KEY_S_STEPS = "s_steps"
    const val DEFAULT_T_SEC = 5
    const val DEFAULT_S_STEPS = 3

    fun getTSec(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_T_SEC, DEFAULT_T_SEC)
    }

    fun getSSteps(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_S_STEPS, DEFAULT_S_STEPS)
    }

    fun saveSettings(context: Context, tSec: Int, sSteps: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_T_SEC, tSec)
            .putInt(KEY_S_STEPS, sSteps)
            .apply()
    }
}

enum class AppScreen {
    Main,
    Settings
}

class MainActivity : ComponentActivity() {

    // ACTIVITY_RECOGNITION (Android 10+) のランタイム権限リクエスト
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MainActivity", "権限が許可されました")
            } else {
                Toast.makeText(this, "権限が拒否されました。歩数計が利用できません。", Toast.LENGTH_LONG).show()
            }
        }

    // POST_NOTIFICATIONS (Android 13+) のランタイム権限リクエスト
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            Log.d("MainActivity", "通知権限: $isGranted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ACTIVITY_RECOGNITION 権限リクエスト (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        // POST_NOTIFICATIONS 権限リクエスト (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
                }
            }
        }
    }
}

@Composable
fun MainApp() {
    var currentScreen by remember { mutableStateOf(AppScreen.Main) }
    val context = LocalContext.current

    var tSec by remember { mutableIntStateOf(SettingsRepository.getTSec(context)) }
    var sSteps by remember { mutableIntStateOf(SettingsRepository.getSSteps(context)) }

    when (currentScreen) {
        AppScreen.Main -> {
            SensorLoggerScreen(
                tSec = tSec,
                sSteps = sSteps,
                onNavigateToSettings = { currentScreen = AppScreen.Settings }
            )
        }
        AppScreen.Settings -> {
            SettingsScreen(
                initialTSec = tSec,
                initialSSteps = sSteps,
                onSaveSettings = { newTSec, newSSteps ->
                    tSec = newTSec
                    sSteps = newSSteps
                    SettingsRepository.saveSettings(context, newTSec, newSSteps)
                },
                onNavigateBack = { currentScreen = AppScreen.Main }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorLoggerScreen(
    tSec: Int,
    sSteps: Int,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current

    // SensorTrackingService が更新する StateFlow を収集して UI に反映する
    val isServiceRunning by SensorStateRepository.isServiceRunning.collectAsState()
    val stepCount by SensorStateRepository.stepCount.collectAsState()
    val isWalking by SensorStateRepository.isWalking.collectAsState()
    val accelValues by SensorStateRepository.accelValues.collectAsState()

    fun startService() {
        val intent = Intent(context, SensorTrackingService::class.java).apply {
            action = SensorTrackingService.ACTION_START
        }
        context.startForegroundService(intent)
        Toast.makeText(context, "記録を開始しました", Toast.LENGTH_SHORT).show()
    }

    fun stopService() {
        val intent = Intent(context, SensorTrackingService::class.java).apply {
            action = SensorTrackingService.ACTION_STOP
        }
        context.startService(intent)
        Toast.makeText(context, "記録を終了しました", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ヘッダー行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "歩行検知デモ", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onNavigateToSettings) {
                Text("⚙ 設定")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 判定条件カード
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "判定条件: ${tSec}秒間に ${sSteps}歩",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 歩行検知アラート表示
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(if (isWalking) Color.Red else Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isWalking) "歩行検知！！" else "待機中...",
                color = if (isWalking) Color.White else Color.DarkGray,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 累計歩数カード
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "累計歩数 (STEP_DETECTOR)", style = MaterialTheme.typography.titleMedium)
                Text(text = "$stepCount 歩", style = MaterialTheme.typography.headlineSmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 加速度センサー値カード (サービスから StateFlow 経由でリアルタイム表示)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "加速度センサー (ACCELEROMETER)", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "X: ${"%.2f".format(accelValues[0])}  " +
                            "Y: ${"%.2f".format(accelValues[1])}  " +
                            "Z: ${"%.2f".format(accelValues[2])}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // サービス開始 / 停止ボタン
        Button(
            onClick = { if (isServiceRunning) stopService() else startService() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = if (isServiceRunning) "■ 記録を停止" else "▶ 記録を開始",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialTSec: Int,
    initialSSteps: Int,
    onSaveSettings: (tSec: Int, sSteps: Int) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var tSecText by remember { mutableStateOf(initialTSec.toString()) }
    var sStepsText by remember { mutableStateOf(initialSSteps.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun resetToDefault() {
        val defaultT = SettingsRepository.DEFAULT_T_SEC
        val defaultS = SettingsRepository.DEFAULT_S_STEPS
        tSecText = defaultT.toString()
        sStepsText = defaultS.toString()
        errorMessage = null
        onSaveSettings(defaultT, defaultS)
        Toast.makeText(context, "デフォルト設定に戻しました", Toast.LENGTH_SHORT).show()
    }

    fun save() {
        val parsedTSec = tSecText.toIntOrNull()
        val parsedSSteps = sStepsText.toIntOrNull()

        if (parsedTSec == null || parsedTSec <= 0) {
            errorMessage = "判定時間(T)には1以上の正の整数を入力してください"
            return
        }
        if (parsedSSteps == null || parsedSSteps <= 0) {
            errorMessage = "必要歩数(S)には1以上の正の整数を入力してください"
            return
        }

        errorMessage = null
        onSaveSettings(parsedTSec, parsedSSteps)
        Toast.makeText(context, "設定を保存しました", Toast.LENGTH_SHORT).show()
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歩行検知 パラメータ設定") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("← 戻る")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "歩行判定に使用する秒数(T)と歩数(S)を設定できます。",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = tSecText,
                onValueChange = {
                    tSecText = it
                    errorMessage = null
                },
                label = { Text("判定保持時間 T (秒)") },
                supportingText = { Text("デフォルト: ${SettingsRepository.DEFAULT_T_SEC} 秒") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = sStepsText,
                onValueChange = {
                    sStepsText = it
                    errorMessage = null
                },
                label = { Text("必要歩数 S (歩)") },
                supportingText = { Text("デフォルト: ${SettingsRepository.DEFAULT_S_STEPS} 歩") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("設定を保存")
            }

            OutlinedButton(
                onClick = { resetToDefault() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("デフォルトに戻す")
            }
        }
    }
}
