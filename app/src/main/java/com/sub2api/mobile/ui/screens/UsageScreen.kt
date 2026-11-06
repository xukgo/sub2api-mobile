package com.sub2api.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.data.UsageLog
import com.sub2api.mobile.ui.components.EmptyState
import com.sub2api.mobile.ui.components.AppCardShape
import com.sub2api.mobile.ui.components.MetricCard
import com.sub2api.mobile.ui.components.SectionHeader
import com.sub2api.mobile.ui.components.appCardBorder
import com.sub2api.mobile.ui.components.formatCount
import com.sub2api.mobile.ui.components.formatDate
import com.sub2api.mobile.ui.components.formatMoney
import com.sub2api.mobile.ui.icons.Icon
import com.sub2api.mobile.ui.icons.Icons
import com.sub2api.mobile.ui.theme.Amber
import com.sub2api.mobile.ui.theme.Coral

private data class PeriodOption(val value: String, val label: String)
private val periods = listOf(PeriodOption("today", "今日"), PeriodOption("week", "本周"), PeriodOption("month", "本月"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(state: AppUiState, onPeriodChange: (String) -> Unit) {
    val stats = state.usageStats
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                periods.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = state.usagePeriod == period.value,
                        onClick = { onPeriodChange(period.value) },
                        shape = SegmentedButtonDefaults.itemShape(index, periods.size),
                        enabled = !state.refreshing,
                    ) { Text(period.label) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                MetricCard("请求", formatCount(stats?.totalRequests ?: 0), Modifier.weight(1f))
                MetricCard("Tokens", formatCount(stats?.totalTokens ?: 0), Modifier.weight(1f), Amber)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("实际消费", formatMoney(stats?.totalActualCost ?: 0.0), Modifier.weight(1f), Coral)
                MetricCard("平均延迟", "${(stats?.averageDurationMs ?: 0.0).toInt()} ms", Modifier.weight(1f))
            }
        }
        item { SectionHeader("最近请求", "${state.usageLogs.size} 条") }
        if (state.usageLogs.isEmpty()) {
            item { EmptyState("当前时段没有用量记录") }
        } else {
            items(state.usageLogs, key = { it.id }) { log -> UsageLogCard(log) }
        }
    }
}

@Composable
private fun UsageLogCard(log: UsageLog) {
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = appCardBorder(),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(log.model.ifBlank { "未知模型" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(log.apiKey?.name ?: "Key #${log.apiKeyId}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Text(formatMoney(log.actualCost), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.Token, null, modifier = Modifier.padding(1.dp), tint = MaterialTheme.colorScheme.outline)
                    Text(formatCount(log.totalTokens), style = MaterialTheme.typography.labelMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.Schedule, null, modifier = Modifier.padding(1.dp), tint = MaterialTheme.colorScheme.outline)
                    Text(log.durationMs?.let { "$it ms" } ?: "-", style = MaterialTheme.typography.labelMedium)
                }
                Text(if (log.stream) "Stream" else "Sync", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatDate(log.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
