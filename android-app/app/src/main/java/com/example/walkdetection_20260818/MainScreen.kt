package com.example.walkdetection_20260818

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * メイン画面（本番用 UI）。
 *
 * - 中央に大きく歩行ステータスを表示するシンプルな構成。
 * - SensorStateRepository の StateFlow を監視して自動更新される。
 * - サービスの起動/停止ボタンを持つ。
 * - CSV ロギングはこの画面では一切行わない（LoggerScreen の責務）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current

    val isWalking by SensorStateRepository.isWalking.collectAsState()
    val isServiceRunning by SensorStateRepository.isServiceRunning.collectAsState()

    // 設定値の読み取り (表示用のみ)
    val tSec = remember { SettingsRepository.getTSec(context) }
    val sSteps = remember { SettingsRepository.getSSteps(context) }

    fun startService() {
        val intent = Intent(context, SensorTrackingService::class.java).apply {
            action = SensorTrackingService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    fun stopService() {
        val intent = Intent(context, SensorTrackingService::class.java).apply {
            action = SensorTrackingService.ACTION_STOP
        }
        context.startService(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歩行検知") },
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
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ── 大きな円形ステータスインジケーター ──────────────────────────
            val circleColor = when {
                !isServiceRunning -> Color(0xFF9E9E9E)   // 停止中: グレー
                isWalking         -> Color(0xFFB71C1C)   // 歩行検知: 赤
                else              -> Color(0xFF1565C0)   // 監視中: 青
            }
            val statusText = when {
                !isServiceRunning -> "停止中"
                isWalking         -> "歩行\n検知！"
                else              -> "監視中"
            }

            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(circleColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 40.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 判定条件の補足表示
            Text(
                text = "判定条件: ${tSec}秒間に ${sSteps}歩",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ── サービス開始 / 停止ボタン ──────────────────────────────────
            Button(
                onClick = { if (isServiceRunning) stopService() else startService() },
                modifier = Modifier
                    .width(200.dp)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isServiceRunning) "■ 停止" else "▶ 開始",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
