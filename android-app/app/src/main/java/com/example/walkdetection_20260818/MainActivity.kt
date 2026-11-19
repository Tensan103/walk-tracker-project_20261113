package com.example.walkdetection_20260818

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

// SettingsRepository はセンサーの検知パラメータ (T秒, S歩) を SharedPreferences で管理する。
// SensorTrackingService からも参照するためトップレベルに定義する。
object SettingsRepository {
    private const val PREF_NAME = "walk_detection_prefs"
    private const val KEY_T_SEC = "t_sec"
    private const val KEY_S_STEPS = "s_steps"
    private const val KEY_R_SEC = "r_sec"
    private const val KEY_ALERT_TYPE = "alert_type"
    private const val KEY_MONITORING_ENABLED = "monitoring_enabled"

    const val DEFAULT_T_SEC = 5
    const val DEFAULT_S_STEPS = 3
    /** 歩行検知後、「監視中」に戻るまでのデフォルト秒数 */
    const val DEFAULT_R_SEC = 5

    fun getTSec(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_T_SEC, DEFAULT_T_SEC)
    }

    fun getSSteps(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_S_STEPS, DEFAULT_S_STEPS)
    }

    /** 歩行検知後、「監視中」に戻るまでの秒数 (Release timeout) */
    fun getRSec(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_R_SEC, DEFAULT_R_SEC)
    }

    fun saveSettings(context: Context, tSec: Int, sSteps: Int, rSec: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_T_SEC, tSec)
            .putInt(KEY_S_STEPS, sSteps)
            .putInt(KEY_R_SEC, rSec)
            .apply()
    }

    fun getAlertType(context: Context): AlertType {
        val name = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ALERT_TYPE, AlertType.NONE.name) ?: AlertType.NONE.name
        return try { AlertType.valueOf(name) } catch (_: IllegalArgumentException) { AlertType.NONE }
    }

    fun saveAlertType(context: Context, type: AlertType) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ALERT_TYPE, type.name)
            .apply()
    }

    fun getMonitoringEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MONITORING_ENABLED, true)

    fun saveMonitoringEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_MONITORING_ENABLED, enabled)
            .apply()
    }
}

/** Bottom Navigation の各タブ定義 */
data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

/** ナビゲーション用ルート定数 */
object NavRoutes {
    const val HOME = "home"
    const val LOGGER = "logger"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {

    // ACTIVITY_RECOGNITION (Android 10+) のランタイム権限リクエスト
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "権限が拒否されました。歩数計が利用できません。", Toast.LENGTH_LONG).show()
            }
        }

    // POST_NOTIFICATIONS (Android 13+) のランタイム権限リクエスト
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
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

    override fun onResume() {
        super.onResume()
        val powerManager = getSystemService(PowerManager::class.java)
        if (SensorStateRepository.isMonitoringEnabled.value &&
            powerManager.isInteractive &&
            !SensorStateRepository.isServiceRunning.value
        ) {
            Log.d("MainActivity", "onResume: サービス未起動を検出、再起動します")
            val intent = Intent(this, SensorTrackingService::class.java).apply {
                action = SensorTrackingService.ACTION_START
            }
            try {
                ContextCompat.startForegroundService(this, intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "onResume: サービス起動失敗: ${e.message}")
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomNavItems = listOf(
        BottomNavItem(NavRoutes.HOME, "ホーム", Icons.Filled.Home),
        BottomNavItem(NavRoutes.LOGGER, "ロガー", Icons.Filled.BarChart),
        BottomNavItem(NavRoutes.HISTORY, "履歴", Icons.Filled.History),
        BottomNavItem(NavRoutes.SETTINGS, "設定", Icons.Filled.Settings)
    )

    val showBottomBar = currentDestination?.route in listOf(
        NavRoutes.HOME, NavRoutes.LOGGER, NavRoutes.HISTORY, NavRoutes.SETTINGS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    // バックスタックを肥大化させずに切り替え
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavRoutes.HOME) {
                MainScreen(
                    onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) }
                )
            }
            composable(NavRoutes.LOGGER) {
                LoggerScreen(
                    onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) }
                )
            }
            composable(NavRoutes.HISTORY) {
                HistoryScreen(
                    onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) }
                )
            }
            composable(NavRoutes.SETTINGS) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current

    // SharedPreferences から現在値を読み込む (NavHost 経由でパラメータを渡す必要がなくなる)
    var tSecText by remember { mutableStateOf(SettingsRepository.getTSec(context).toString()) }
    var sStepsText by remember { mutableStateOf(SettingsRepository.getSSteps(context).toString()) }
    var rSecText by remember { mutableStateOf(SettingsRepository.getRSec(context).toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val alertType by SensorStateRepository.alertType.collectAsState()
    val isMonitoringEnabled by SensorStateRepository.isMonitoringEnabled.collectAsState()
    val isServiceRunning by SensorStateRepository.isServiceRunning.collectAsState()

    // 権限確認のために OS 設定画面を起動し、戻ってきたときに再チェックする
    var pendingAlertType by remember { mutableStateOf<AlertType?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val pending = pendingAlertType ?: return@rememberLauncherForActivityResult
        val granted = when (pending) {
            AlertType.FULL_SCREEN_INTENT ->
                Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java)
                    ?.canUseFullScreenIntent() == true
            AlertType.SYSTEM_ALERT_WINDOW ->
                AndroidSettings.canDrawOverlays(context)
            else -> true
        }
        if (granted) {
            SensorStateRepository.updateAlertType(pending)
            SettingsRepository.saveAlertType(context, pending)
        }
        pendingAlertType = null
    }

    fun onAlertTypeSelected(type: AlertType) {
        when (type) {
            AlertType.FULL_SCREEN_INTENT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    context.getSystemService(NotificationManager::class.java)
                        ?.canUseFullScreenIntent() == false
                ) {
                    pendingAlertType = type
                    permissionLauncher.launch(
                        Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                    return
                }
                SensorStateRepository.updateAlertType(type)
                SettingsRepository.saveAlertType(context, type)
            }
            AlertType.SYSTEM_ALERT_WINDOW -> {
                if (!AndroidSettings.canDrawOverlays(context)) {
                    pendingAlertType = type
                    permissionLauncher.launch(
                        Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                    return
                }
                SensorStateRepository.updateAlertType(type)
                SettingsRepository.saveAlertType(context, type)
            }
            else -> {
                SensorStateRepository.updateAlertType(type)
                SettingsRepository.saveAlertType(context, type)
            }
        }
    }

    fun save() {
        val parsedTSec = tSecText.toIntOrNull()
        val parsedSSteps = sStepsText.toIntOrNull()
        val parsedRSec = rSecText.toIntOrNull()

        if (parsedTSec == null || parsedTSec <= 0) {
            errorMessage = "判定時間(T)には1以上の正の整数を入力してください"
            return
        }
        if (parsedSSteps == null || parsedSSteps <= 0) {
            errorMessage = "必要歩数(S)には1以上の正の整数を入力してください"
            return
        }
        if (parsedRSec == null || parsedRSec <= 0) {
            errorMessage = "解除時間(R)には1以上の正の整数を入力してください"
            return
        }

        errorMessage = null
        SettingsRepository.saveSettings(context, parsedTSec, parsedSSteps, parsedRSec)
        Toast.makeText(context, "設定を保存しました", Toast.LENGTH_SHORT).show()
        onNavigateBack()
    }

    fun resetToDefault() {
        val defaultT = SettingsRepository.DEFAULT_T_SEC
        val defaultS = SettingsRepository.DEFAULT_S_STEPS
        val defaultR = SettingsRepository.DEFAULT_R_SEC
        tSecText = defaultT.toString()
        sStepsText = defaultS.toString()
        rSecText = defaultR.toString()
        errorMessage = null
        SettingsRepository.saveSettings(context, defaultT, defaultS, defaultR)
        Toast.makeText(context, "デフォルト設定に戻しました", Toast.LENGTH_SHORT).show()
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
        // 外側 Column: コンテンツ(スクロール) + ボタン(固定) の2段構成
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── スクロール可能なコンテンツ領域 ───────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── 監視ON/OFFスイッチ (検証用) ─────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "監視機能を有効にする（検証用）",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "現在: ${if (isServiceRunning) "稼働中" else "待機中"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isServiceRunning)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isMonitoringEnabled,
                        onCheckedChange = { enabled ->
                            SensorStateRepository.updateMonitoringEnabled(enabled)
                            SettingsRepository.saveMonitoringEnabled(context, enabled)
                            if (enabled) {
                                val intent = Intent(context, SensorTrackingService::class.java).apply {
                                    action = SensorTrackingService.ACTION_START
                                }
                                try {
                                    ContextCompat.startForegroundService(context, intent)
                                } catch (e: Exception) {
                                    Log.e("SettingsScreen", "サービス起動失敗: ${e.message}")
                                }
                            } else {
                                context.stopService(Intent(context, SensorTrackingService::class.java))
                            }
                        }
                    )
                }

                HorizontalDivider()

                Text(
                    text = "歩行判定パラメータを設定できます。\n" +
                           "T秒間にS歩で「検知」、最後の歩行からR秒後に「解除」します。",
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

                OutlinedTextField(
                    value = rSecText,
                    onValueChange = {
                        rSecText = it
                        errorMessage = null
                    },
                    label = { Text("解除時間 R (秒)") },
                    supportingText = { Text("歩行検知後、「監視中」に戻るまでの秒数 / デフォルト: ${SettingsRepository.DEFAULT_R_SEC} 秒") },
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

                HorizontalDivider()

                Text(
                    text = "検知時の警告設定",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "歩行を検知したときの動作を選択します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val alertOptions = listOf(
                    AlertType.NONE to "なし",
                    AlertType.TOAST to "トーストで通知",
                    AlertType.FULL_SCREEN_INTENT to "通知から前面強制表示 (Android 14+ は要権限)",
                    AlertType.SYSTEM_ALERT_WINDOW to "オーバーレイ警告を表示 (要権限)"
                )
                Column {
                    alertOptions.forEach { (type, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = alertType == type,
                                    onClick = { onAlertTypeSelected(type) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = alertType == type,
                                onClick = null  // 親 Row が onClick を処理
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            // ── 画面下部に固定されたボタン ──────────────────────────
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { save() },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("設定を保存")
                }
                OutlinedButton(
                    onClick = { resetToDefault() },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("デフォルトに戻す")
                }
            }
        }
    }
}
