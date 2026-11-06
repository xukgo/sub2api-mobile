package com.sub2api.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.data.ChannelMonitorDetail
import com.sub2api.mobile.data.ChannelMonitorModelDetail
import com.sub2api.mobile.data.ChannelMonitorTimelinePoint
import com.sub2api.mobile.data.ChannelMonitorView
import com.sub2api.mobile.ui.components.EmptyState
import com.sub2api.mobile.ui.components.AppCardShape
import com.sub2api.mobile.ui.components.appCardBorder
import com.sub2api.mobile.ui.components.FullScreenLoading
import com.sub2api.mobile.ui.icons.Icon
import com.sub2api.mobile.ui.icons.Icons
import java.text.DecimalFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TIMELINE_POINTS = 24

internal data class ChannelHealthSummary(
    val total: Int,
    val operational: Int,
    val degraded: Int,
    val unavailable: Int,
    val unknown: Int,
)

internal fun summarizeChannelHealth(items: List<ChannelMonitorView>): ChannelHealthSummary {
    val unique = items.distinctBy { it.id }
    var operational = 0
    var degraded = 0
    var unavailable = 0
    var unknown = 0
    unique.forEach { item ->
        when (item.primaryStatus.lowercase()) {
            "operational" -> operational++
            "degraded" -> degraded++
            "failed", "error" -> unavailable++
            else -> unknown++
        }
    }
    return ChannelHealthSummary(unique.size, operational, degraded, unavailable, unknown)
}

@Composable
fun ChannelStatusScreen(
    state: AppUiState,
    onRefresh: () -> Unit,
    onLoadDetail: (Long, Boolean) -> Unit,
) {
    var selectedMonitorId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { onRefresh() }

    when {
        state.channelMonitorsLoading && state.channelMonitors.isEmpty() ->
            FullScreenLoading("正在加载渠道状态")

        state.channelMonitorsError != null && state.channelMonitors.isEmpty() ->
            ChannelStatusError(
                message = state.channelMonitorsError,
                onRetry = onRefresh,
            )

        state.channelMonitors.isEmpty() ->
            EmptyState("暂无可显示的渠道", "管理员尚未配置可监控的渠道")

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ChannelSummaryBand(summarizeChannelHealth(state.channelMonitors))
            }
            state.channelMonitorsError?.let { message ->
                item { ChannelStatusErrorBanner(message, onRefresh) }
            }
            items(state.channelMonitors, key = { it.id }) { monitor ->
                ChannelMonitorCard(
                    monitor = monitor,
                    onClick = {
                        selectedMonitorId = monitor.id
                        onLoadDetail(monitor.id, false)
                    },
                )
            }
        }
    }

    selectedMonitorId?.let { id ->
        val monitor = state.channelMonitors.firstOrNull { it.id == id }
        ChannelMonitorDetailDialog(
            title = monitor?.name.orEmpty().ifBlank { "渠道详情" },
            detail = state.channelMonitorDetails[id],
            loading = id in state.channelMonitorDetailsLoading,
            error = state.channelMonitorDetailErrors[id],
            onRetry = { onLoadDetail(id, true) },
            onDismiss = { selectedMonitorId = null },
        )
    }
}

@Composable
private fun ChannelSummaryBand(summary: ChannelHealthSummary) {
    val (headline, headlineColor) = when {
        summary.unavailable > 0 -> "存在故障" to MaterialTheme.colorScheme.error
        summary.degraded > 0 -> "服务降级" to Color(0xFF9A6700)
        summary.unknown > 0 -> "等待检测" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "全部正常" to Color(0xFF217A55)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = AppCardShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        border = appCardBorder(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("系统状态", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = headlineColor)
                }
                Text(
                    "${summary.operational}/${summary.total} 正常",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = headlineColor,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SummaryMetric("正常", summary.operational, Color(0xFF217A55), Modifier.weight(1f))
                SummaryMetric("降级", summary.degraded, Color(0xFF9A6700), Modifier.weight(1f))
                SummaryMetric("故障", summary.unavailable, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                SummaryMetric("未检测", summary.unknown, MaterialTheme.colorScheme.outline, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun ChannelMonitorCard(monitor: ChannelMonitorView, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = AppCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = appCardBorder(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = providerColor(monitor.provider).copy(alpha = 0.13f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Dns, null, tint = providerColor(monitor.provider), modifier = Modifier.size(22.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        monitor.name.ifBlank { "未命名渠道" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            providerLabel(monitor.provider),
                            style = MaterialTheme.typography.labelMedium,
                            color = providerColor(monitor.provider),
                            fontWeight = FontWeight.SemiBold,
                        )
                        monitor.groupName.takeIf { it.isNotBlank() }?.let { group ->
                            Text(" · $group", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                ChannelStatusBadge(monitor.primaryStatus)
            }

            Text(
                monitor.primaryModel.ifBlank { "主模型未配置" },
                modifier = Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChannelMetric("对话延迟", formatLatency(monitor.primaryLatencyMs), Modifier.weight(1f))
                ChannelMetric("端点 PING", formatLatency(monitor.primaryPingLatencyMs), Modifier.weight(1f))
                ChannelMetric("7 天可用率", formatAvailability(monitor.availability7d), Modifier.weight(1f))
            }

            HorizontalDivider(modifier = Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("近期状态", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Text(
                    formatLatestCheck(monitor.timeline.firstOrNull()?.checkedAt),
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            ChannelTimeline(monitor.timeline)

            if (monitor.extraModels.isNotEmpty()) {
                Text(
                    "附加模型 ${monitor.extraModels.size}",
                    modifier = Modifier.padding(top = 12.dp, bottom = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                monitor.extraModels.take(3).forEach { extra ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(7.dp).background(statusColor(extra.status), CircleShape))
                        Text(extra.model, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(statusLabel(extra.status), style = MaterialTheme.typography.labelSmall, color = statusColor(extra.status))
                        Text(" · ${formatLatency(extra.latencyMs)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 58.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 9.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChannelTimeline(points: List<ChannelMonitorTimelinePoint>) {
    val recent = points.take(TIMELINE_POINTS).reversed()
    val slots: List<ChannelMonitorTimelinePoint?> = List((TIMELINE_POINTS - recent.size).coerceAtLeast(0)) { null } + recent
    Row(
        modifier = Modifier.fillMaxWidth().height(22.dp).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        slots.forEach { point ->
            val height = when (point?.status?.lowercase()) {
                "operational" -> 16.dp
                "degraded" -> 11.dp
                "failed", "error" -> 7.dp
                else -> 3.dp
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .background(point?.let { statusColor(it.status) } ?: MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
private fun ChannelStatusBadge(status: String) {
    val color = statusColor(status)
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.13f)) {
        Text(
            statusLabel(status),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChannelStatusError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.error)
        Text("渠道状态加载失败", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(message, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
            Text("重试", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ChannelStatusErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, end = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
            Text(message, modifier = Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun ChannelMonitorDetailDialog(
    title: String,
    detail: ChannelMonitorDetail?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 720.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        detail?.let {
                            Text(
                                listOf(providerLabel(it.provider), it.groupName).filter { value -> value.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                when {
                    loading && detail == null -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                    error != null && detail == null -> Column(
                        modifier = Modifier.fillMaxWidth().height(220.dp).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Text(error, modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onRetry, modifier = Modifier.padding(top = 14.dp)) { Text("重试") }
                    }
                    detail == null || detail.models.isEmpty() -> Box(Modifier.fillMaxWidth().height(220.dp)) {
                        EmptyState("暂无模型状态")
                    }
                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((detail.models.size * 178).coerceIn(190, 560).dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        items(detail.models, key = { it.model }) { model ->
                            ChannelModelDetailRow(model)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelModelDetailRow(model: ChannelMonitorModelDetail) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.model.ifBlank { "未命名模型" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ChannelStatusBadge(model.latestStatus)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DetailMetric("7 天", formatAvailability(model.availability7d), Modifier.weight(1f))
            DetailMetric("15 天", formatAvailability(model.availability15d), Modifier.weight(1f))
            DetailMetric("30 天", formatAvailability(model.availability30d), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 9.dp)) {
            Text("最新延迟 ${formatLatency(model.latestLatencyMs)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("7 天平均 ${formatLatency(model.avgLatency7dMs)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun DetailMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("$label 可用率", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun statusColor(status: String): Color = when (status.lowercase()) {
    "operational" -> Color(0xFF217A55)
    "degraded" -> Color(0xFF9A6700)
    "failed", "error" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.outline
}

private fun statusLabel(status: String): String = when (status.lowercase()) {
    "operational" -> "正常"
    "degraded" -> "降级"
    "failed" -> "失败"
    "error" -> "错误"
    else -> "未检测"
}

private fun providerLabel(provider: String): String = when (provider.lowercase()) {
    "openai" -> "OpenAI"
    "anthropic" -> "Anthropic"
    "gemini" -> "Gemini"
    "grok" -> "Grok"
    else -> provider
}

private fun providerColor(provider: String): Color = when (provider.lowercase()) {
    "openai" -> Color(0xFF176448)
    "anthropic" -> Color(0xFF9A4E2E)
    "gemini" -> Color(0xFF3568B8)
    "grok" -> Color(0xFF424847)
    else -> Color(0xFF5B655F)
}

private fun formatLatency(value: Int?): String = value?.let { "$it ms" } ?: "--"

private fun formatAvailability(value: Double?): String =
    value?.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0)?.let { "${DecimalFormat("0.00").format(it)}%" } ?: "--"

private fun formatLatestCheck(value: String?): String {
    if (value.isNullOrBlank()) return "尚未检测"
    return runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }.getOrElse { value.take(16).replace('T', ' ') }
}
