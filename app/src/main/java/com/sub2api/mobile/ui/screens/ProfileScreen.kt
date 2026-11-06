package com.sub2api.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.BuildConfig
import com.sub2api.mobile.data.ServerUrl
import com.sub2api.mobile.ui.components.LabeledRow
import com.sub2api.mobile.ui.components.SectionHeader
import com.sub2api.mobile.ui.components.AppCardShape
import com.sub2api.mobile.ui.components.appCardBorder
import com.sub2api.mobile.ui.components.formatDate
import com.sub2api.mobile.ui.components.formatMoney
import com.sub2api.mobile.ui.icons.AppIcon
import com.sub2api.mobile.ui.icons.Icon
import com.sub2api.mobile.ui.icons.Icons

@Composable
fun ProfileScreen(
    state: AppUiState,
    onUpdateUsername: (String, () -> Unit) -> Unit,
    onLogout: () -> Unit,
    onChangeServer: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenSubscriptions: (() -> Unit)? = null,
    onOpenChannelStatus: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onOpenKeys: (() -> Unit)? = null,
) {
    val user = state.user ?: return
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var showLogout by rememberSaveable { mutableStateOf(false) }
    val webRoot = remember(state.serverInput) {
        runCatching { ServerUrl.webRoot(ServerUrl.normalize(state.serverInput)) }.getOrDefault(state.serverInput)
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppCardShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                border = appCardBorder(),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(user.username.ifBlank { user.email }.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(user.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { showEdit = true }) { Icon(Icons.Outlined.Edit, "编辑资料") }
                }
            }
        }
        item { SectionHeader("账户") }
        item {
            Surface(modifier = Modifier.fillMaxWidth(), shape = AppCardShape, color = MaterialTheme.colorScheme.surface, border = appCardBorder()) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    LabeledRow("余额", formatMoney(user.balance))
                    LabeledRow("并发额度", user.concurrency.toString())
                    LabeledRow("账户状态", if (user.status == "active") "正常" else user.status)
                    LabeledRow("注册时间", formatDate(user.createdAt))
                }
            }
        }
        item { SectionHeader("服务") }
        item {
            Surface(modifier = Modifier.fillMaxWidth(), shape = AppCardShape, color = MaterialTheme.colorScheme.surface, border = appCardBorder()) {
                Column {
                    ActionRow(Icons.Outlined.NetworkCheck, "渠道状态", onClick = onOpenChannelStatus)
                    onOpenKeys?.let { ActionRow(Icons.Outlined.Key, "API 密钥", onClick = it) }
                    onOpenSubscriptions?.let { ActionRow(Icons.Outlined.CreditCard, "订阅与兑换", onClick = it) }
                    ActionRow(Icons.Outlined.Language, "打开网页版") { onOpenUrl(webRoot) }
                    if (state.settings?.paymentEnabled == true) ActionRow(Icons.Outlined.Payments, "充值与订单") { onOpenUrl("$webRoot/payment") }
                    state.settings?.docUrl?.takeIf { it.isNotBlank() }?.let { url -> ActionRow(Icons.Outlined.Description, "使用文档") { onOpenUrl(url) } }
                    ActionRow(Icons.Outlined.Dns, "服务管理", state.activeServerName, onClick = onChangeServer)
                }
            }
        }
        item { SectionHeader("应用") }
        item {
            Surface(modifier = Modifier.fillMaxWidth(), shape = AppCardShape, color = MaterialTheme.colorScheme.surface, border = appCardBorder()) {
                Column {
                ActionRow(
                    Icons.Outlined.SystemUpdate,
                    "检查更新",
                    when {
                        state.updateChecking -> "正在检查..."
                        state.availableUpdate != null -> "新版本 ${state.availableUpdate.versionName} 可用"
                        else -> "当前版本 ${BuildConfig.VERSION_NAME}"
                    },
                    onClick = onCheckForUpdate,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showLogout = true }.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error)
                    Text("退出登录", modifier = Modifier.padding(start = 14.dp), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sub2API Mobile", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${BuildConfig.VERSION_NAME} · API ${state.settings?.version.orEmpty()}", color = MaterialTheme.colorScheme.outline)
                }
                }
            }
        }
    }

    if (showEdit) {
        var username by rememberSaveable { mutableStateOf(user.username) }
        AlertDialog(
            onDismissRequest = { if (!state.loading) showEdit = false },
            title = { Text("编辑资料") },
            text = { OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("用户名") }, singleLine = true) },
            confirmButton = { Button(onClick = { onUpdateUsername(username) { showEdit = false } }, enabled = username.isNotBlank() && !state.loading) { Text(if (state.loading) "保存中" else "保存") } },
            dismissButton = { TextButton(onClick = { showEdit = false }, enabled = !state.loading) { Text("取消") } },
        )
    }
    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("退出登录") },
            text = { Text("将退出当前服务，其他服务的登录状态不受影响。") },
            confirmButton = { TextButton(onClick = { showLogout = false; onLogout() }) { Text("退出", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text("取消") } },
        )
    }

}

@Composable
private fun ActionRow(icon: AppIcon, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
