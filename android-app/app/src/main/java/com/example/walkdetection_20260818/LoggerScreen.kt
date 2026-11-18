package com.example.walkdetection_20260818

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * データロガー画面（レガシー / 検証用）。
 *
 * 「記録を開始」ボタンを押した時のみ CSV へのロギング処理が走る。
 * 歩行判定 (DETECT/RELEASE) は MainScreen のサービスが担うため、
 * この画面はロギング状態に関係なく常にセンサー値を表示する。
 *
 * LoggerScreen から起動する流れ:
 *  1. 「記録を開始」→ ACTION_START_LOGGING を送信 (サービス未起動なら自動起動)
 *  2. 「記録を停止」→ ACTION_STOP_LOGGING を送信 (サービスはそのまま継続)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggerScreen(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current

    val stepCount by SensorStateRepository.stepCount.collectAsState()
    val accelValues by SensorStateRepository.accelValues.collectAsState()
    val isLogging by SensorStateRepository.isLoggingEnabled.collectAsState()
    val isServiceRunning by SensorStateRepository.isServiceRunning.collectAsState()

    // 検証メモ: ファイル名のサフィックスに使用 (記録中は変更不可)
    var memoText by remember { mutableStateOf("") }

    fun startLogging() {
        val intent = Intent(context, SensorTrackingService::class.java).apply {
            action = SensorTrackingService.ACTION_START_LOGGING
            putExtra(SensorTrackingService.EXTRA_MEMO, memoText)
        }
        context.startForegroundService(intent)
        Toast.makeText(context, "記録を開始しました", Toast.LENGTH_SHORT).show()
    }

    fun stopLogging() {
        val intent = Intent(context, SensorTrackingService::class.java).apply {
            action = SensorTrackingService.ACTION_STOP_LOGGING
        }
        context.startService(intent)
        Toast.makeText(context, "記録を終了しました", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("データロガー") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "設定")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── サービス停止中の警告バナー ────────────────────────────────
            if (!isServiceRunning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "⚠ センサーサービスが停止中です。\n" +
                               "「記録を開始」を押すとサービスも同時に起動します。",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ── 累計歩数カード ────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "累計歩数 (STEP_DETECTOR)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "$stepCount 歩",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── 加速度センサー値カード ────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "加速度センサー (ACCELEROMETER)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "X: ${"%.2f".format(accelValues[0])}\n" +
                               "Y: ${"%.2f".format(accelValues[1])}\n" +
                               "Z: ${"%.2f".format(accelValues[2])}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // ── 検証メモ テキストボックス ─────────────────────────────────
            OutlinedTextField(
                value = memoText,
                onValueChange = { memoText = it },
                label = { Text("検証メモ (ファイル名サフィックス)") },
                placeholder = { Text("例: 室内歩行テスト") },
                singleLine = true,
                enabled = !isLogging,   // 記録中は変更不可
                modifier = Modifier.fillMaxWidth()
            )

            // ロギング状態の補足テキスト
            if (isLogging) {
                Text(
                    text = "● 記録中",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 記録開始 / 停止ボタン ──────────────────────────────────────
            Button(
                onClick = { if (isLogging) stopLogging() else startLogging() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLogging) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isLogging) "■ 記録を停止" else "▶ 記録を開始",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
