package com.sub2api.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.data.ProgressWindow
import com.sub2api.mobile.data.SubscriptionProgress
import com.sub2api.mobile.data.UserSubscription
import com.sub2api.mobile.data.safeProgressFraction
import com.sub2api.mobile.ui.components.ContinuousLinearProgressIndicator
import com.sub2api.mobile.ui.components.AppCardShape
import com.sub2api.mobile.ui.components.EmptyState
import com.sub2api.mobile.ui.components.SectionHeader
import com.sub2api.mobile.ui.components.appCardBorder
import com.sub2api.mobile.ui.components.formatDate
import com.sub2api.mobile.ui.components.formatMoney
import com.sub2api.mobile.ui.icons.Icon
import com.sub2api.mobile.ui.icons.Icons

@Composable
fun SubscriptionsScreen(state: AppUiState, onRedeem: (String, () -> Unit) -> Unit) {
    var showRedeem by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedButton(onClick = { showRedeem = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Outlined.Redeem, null)
                Text("  兑换码")
            }
        }
        item { SectionHeader("我的订阅", "${state.subscriptions.count { it.status == "active" }} 个生效中") }
        if (state.subscriptions.isEmpty()) {
            item { EmptyState("暂无订阅") }
        } else {
            itemsIndexed(
                state.subscriptions,
                key = { index, subscription -> "subscription-${subscription.id}-$index" },
            ) { index, subscription ->
                val orderedProgress = state.subscriptionProgress.getOrNull(index)
                val progress = orderedProgress?.takeIf {
                    subscription.id == 0L || it.subscriptionId == subscription.id
                } ?: state.subscriptionProgress.firstOrNull { it.subscriptionId == subscription.id }
                SubscriptionCard(subscription, progress)
            }
        }
        item { SectionHeader("兑换记录", "${state.redeemHistory.size} 条") }
        if (state.redeemHistory.isEmpty()) {
            item { EmptyState("暂无兑换记录") }
        } else {
            itemsIndexed(
                state.redeemHistory,
                key = { index, item -> "redeem-${item.id}-$index" },
            ) { _, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Redeem, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(item.group?.name ?: redeemTypeLabel(item.type), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatDate(item.usedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(redeemValue(item.type, item.value, item.validityDays), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showRedeem) {
        RedeemDialog(
            loading = state.loading,
            onDismiss = { if (!state.loading) showRedeem = false },
            onRedeem = { code -> onRedeem(code) { showRedeem = false } },
        )
    }
}

@Composable
private fun SubscriptionCard(subscription: UserSubscription, progress: SubscriptionProgress?) {
    val active = subscription.status == "active"
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = appCardBorder(),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(subscription.group?.name ?: "订阅 #${subscription.id}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subscription.group?.platform?.uppercase() ?: "SUB2API", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (active) "生效中" else statusLabel(subscription.status), color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
            progress?.daily?.let { ProgressLine("每日", it) }
            progress?.weekly?.let { ProgressLine("每周", it) }
            progress?.monthly?.let { ProgressLine("每月", it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("到期时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(progress?.daysRemaining?.let { "$it 天后" } ?: formatDate(subscription.expiresAt), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ProgressLine(label: String, window: ProgressWindow) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                if (window.limit != null) "${formatMoney(window.used)} / ${formatMoney(window.limit)}" else "${formatMoney(window.used)} / 不限",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ContinuousLinearProgressIndicator(
            progress = { safeProgressFraction(window.percentage) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (window.percentage >= 90) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RedeemDialog(loading: Boolean, onDismiss: () -> Unit, onRedeem: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("兑换码") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("输入兑换码") },
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { onRedeem(code) }, enabled = code.isNotBlank() && !loading) { Text(if (loading) "兑换中" else "兑换") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } },
    )
}

private fun statusLabel(status: String): String = when (status) {
    "expired" -> "已过期"
    "revoked" -> "已撤销"
    "suspended" -> "已暂停"
    else -> status
}

private fun redeemTypeLabel(type: String): String = when (type) {
    "balance" -> "余额"
    "concurrency" -> "并发额度"
    "subscription" -> "订阅"
    else -> type
}

private fun redeemValue(type: String, value: Double, days: Int?): String = when (type) {
    "balance" -> "+${formatMoney(value)}"
    "concurrency" -> "+${value.toInt()} 并发"
    "subscription" -> days?.let { "$it 天" } ?: "已兑换"
    else -> value.toString()
}
