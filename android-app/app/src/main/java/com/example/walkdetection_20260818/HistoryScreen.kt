package com.example.walkdetection_20260818

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PAGE_SIZE = 10

/**
 * 歩行検知の履歴画面。
 *
 * - SensorStateRepository.walkHistory を購読してリアルタイムに更新される。
 * - 1ページ 10件のペジネーションで表示。
 * - 各行に「検知時刻」「解除時刻」「経過時間」を表示。
 * - データ 0件・端数ページ・ページ範囲外などの境界値でクラッシュしない。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onNavigateToSettings: () -> Unit) {
    val history by SensorStateRepository.walkHistory.collectAsState()

    // 時刻フォーマッター (remember でリコンポーズのたびに生成しない)
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    fun Long.toTimeStr(): String = sdf.format(Date(this))

    // ── ページネーション計算 ──────────────────────────────────────────
    var currentPage by remember { mutableIntStateOf(0) }

    val totalPages = if (history.isEmpty()) 1 else (history.size + PAGE_SIZE - 1) / PAGE_SIZE
    // データが減ったときなど、currentPage が範囲を超えないようにする
    val safePage = currentPage.coerceIn(0, maxOf(0, totalPages - 1))
    LaunchedEffect(totalPages) {
        if (currentPage > totalPages - 1) currentPage = maxOf(0, totalPages - 1)
    }

    val pageItems = history.drop(safePage * PAGE_SIZE).take(PAGE_SIZE)

    // ── UI ───────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歩行履歴") },
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
        ) {
            // ── 履歴リスト ─────────────────────────────────────────
            if (history.isEmpty()) {
                // 0件の場合の空状態
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "まだ履歴がありません。\n歩行を検知すると、ここに表示されます。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // ページ内のアイテムインデックスを通し番号に変換
                    items(pageItems) { item ->
                        val globalIndex = history.indexOf(item) + 1  // 1始まりの通し番号
                        WalkHistoryCard(
                            index = globalIndex,
                            item = item,
                            toTimeStr = { it.toTimeStr() }
                        )
                    }
                }
            }

            // ── ページコントローラー ───────────────────────────────
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                    enabled = currentPage > 0
                ) {
                    Text("< 前の${PAGE_SIZE}件へ")
                }

                Text(
                    text = "${safePage + 1} / ${totalPages} ページ",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                OutlinedButton(
                    onClick = { currentPage = (currentPage + 1).coerceAtMost(totalPages - 1) },
                    enabled = currentPage < totalPages - 1
                ) {
                    Text("次の${PAGE_SIZE}件へ >")
                }
            }
        }
    }
}

/** 1件の履歴を表示するカード */
@Composable
private fun WalkHistoryCard(
    index: Int,
    item: WalkHistoryItem,
    toTimeStr: (Long) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 通し番号
            Text(
                text = "#$index",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp)
            )

            // 検知・解除時刻
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "検知",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp)
                    )
                    Text(
                        text = toTimeStr(item.detectTimeMs),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "解除",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp)
                    )
                    Text(
                        text = toTimeStr(item.releaseTimeMs),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 経過時間・歩数
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${item.durationSeconds}秒",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "経過時間",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${item.stepsDuringWalking}歩",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "検知中歩数",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
