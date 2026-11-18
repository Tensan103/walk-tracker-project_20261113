package com.example.walkdetection_20260818

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
 * サービスの開始/停止は画面ON/OFFによって自動制御されるため、
 * このUIにはボタンを持たない。
 *
 * 表示内容:
 *  - 中央の円形インジケーター: サービス停止中/監視中/歩行検知！ の3状態
 *  - 累計歩数 (動作確認用)
 *  - 判定条件 (T秒/S歩)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current

    val isWalking by SensorStateRepository.isWalking.collectAsState()
    val isServiceRunning by SensorStateRepository.isServiceRunning.collectAsState()
    val stepCount by SensorStateRepository.stepCount.collectAsState()

    // 設定値の読み取り (表示用のみ)
    val tSec = remember { SettingsRepository.getTSec(context) }
    val sSteps = remember { SettingsRepository.getSSteps(context) }
    val rSec = remember { SettingsRepository.getRSec(context) }

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

            // ── 大きな円形ステータスインジケーター ──────────────────────
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

            Spacer(modifier = Modifier.height(28.dp))

            // ── 累計歩数 (動作確認用) ──────────────────────────────────
            Text(
                text = "$stepCount 歩",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "累計歩数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── 判定条件の補足表示 ─────────────────────────────────────
            Text(
                text = "検知: ${tSec}秒間に ${sSteps}歩  /  解除: ${rSec}秒",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 自動制御の説明ラベル ───────────────────────────────────
            Text(
                text = "画面ON/OFFで自動的に開始/停止します",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
