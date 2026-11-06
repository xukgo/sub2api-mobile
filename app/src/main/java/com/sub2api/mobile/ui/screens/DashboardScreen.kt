package com.sub2api.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.data.safePercentage
import com.sub2api.mobile.data.safeProgressFraction
import com.sub2api.mobile.ui.components.ContinuousLinearProgressIndicator
import com.sub2api.mobile.ui.components.AppCardShape
import com.sub2api.mobile.ui.components.EmptyState
import com.sub2api.mobile.ui.components.FullScreenLoading
import com.sub2api.mobile.ui.components.MetricCard
import com.sub2api.mobile.ui.components.SectionHeader
import com.sub2api.mobile.ui.components.appCardBorder
import com.sub2api.mobile.ui.components.formatCount
import com.sub2api.mobile.ui.components.formatDate
import com.sub2api.mobile.ui.components.formatMoney
import com.sub2api.mobile.ui.components.maskKey
import com.sub2api.mobile.ui.icons.Icon
import com.sub2api.mobile.ui.icons.Icons
import com.sub2api.mobile.ui.theme.Amber
import com.sub2api.mobile.ui.theme.Coral

@Composable
fun DashboardScreen(state: AppUiState) {
    val user = state.user
    val stats = state.dashboard
    if (user == null && state.loading) {
        FullScreenLoading()
        return
    }
    if (user == null) {
        EmptyState("账户信息不可用")
        return
    }

    LazyColumn(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = AppCardShape,
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("${greeting()}，${user.username.ifBlank { user.email.substringBefore('@') }}", style = MaterialTheme.typography.titleMedium)
                    Text(formatMoney(user.balance), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("可用余额", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                    Row(modifier = Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        MiniMetric("并发", user.concurrency.toString())
                        MiniMetric("RPM", if (user.rpmLimit > 0) user.rpmLimit.toString() else "不限")
                        MiniMetric("状态", if (user.status == "active") "正常" else user.status)
                    }
                }
            }
        }
        item {
            SectionHeader("今日")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("请求", formatCount(stats?.todayRequests ?: 0), Modifier.weight(1f))
                MetricCard("Tokens", formatCount(stats?.todayTokens ?: 0), Modifier.weight(1f), Amber)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("实际消费", formatMoney(stats?.todayActualCost ?: 0.0), Modifier.weight(1f), Coral)
                MetricCard("平均延迟", "${(stats?.averageDurationMs ?: 0.0).toInt()} ms", Modifier.weight(1f), detail = "RPM ${(stats?.rpm ?: 0.0).toInt()}")
            }
        }
        item { SectionHeader("订阅", "${state.subscriptionSummary?.activeCount ?: 0} 个生效中") }
        if (state.subscriptionSummary?.subscriptions.isNullOrEmpty()) {
            item { CompactEmpty("暂无有效订阅") }
        } else {
            itemsIndexed(
                state.subscriptionSummary!!.subscriptions.take(3),
                key = { index, subscription -> "summary-${subscription.id}-$index" },
            ) { _, subscription ->
                Card(
                    shape = AppCardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = appCardBorder(),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(18.dp))
                                Text(subscription.groupName, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                subscription.daysRemaining?.let { "$it 天" }
                                    ?: subscription.expiresAt?.let { formatDate(it).substringBefore(' ') }
                                    ?: "长期",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        subscription.resolvedDailyProgress?.let { progress ->
                            ContinuousLinearProgressIndicator(
                                progress = { safeProgressFraction(progress) },
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(6.dp),
                            )
                            Text("今日 ${safePercentage(progress).toInt()}%", modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { SectionHeader("最近的 API Key", "共 ${state.keys.size} 个") }
        if (state.keys.isEmpty()) {
            item { CompactEmpty("还没有 API Key") }
        } else {
            itemsIndexed(
                state.keys.take(4),
                key = { index, key -> "dashboard-key-${key.id}-$index" },
            ) { _, key ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppCardShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = appCardBorder(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.height(38.dp).weight(0.14f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.Key, null, modifier = Modifier.height(18.dp)) }
                        Column(Modifier.weight(0.86f)) {
                            Text(key.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(maskKey(key.key), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Text(if (key.status == "active") "可用" else "停用", style = MaterialTheme.typography.labelMedium, color = if (key.status == "active") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item { Box(Modifier.height(12.dp)) }
    }
}

@Composable
private fun MiniMetric(label: String, value: String) {
    Column {
        Text(value, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
    }
}

@Composable
private fun CompactEmpty(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCardShape,
        color = MaterialTheme.colorScheme.surface,
        border = appCardBorder(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Bolt, null, tint = MaterialTheme.colorScheme.outline)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..10 -> "早上好"
    in 11..13 -> "中午好"
    in 14..17 -> "下午好"
    else -> "晚上好"
}
