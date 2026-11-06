package com.sub2api.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.data.ApiKey
import com.sub2api.mobile.ui.components.EmptyState
import com.sub2api.mobile.ui.components.AppCardShape
import com.sub2api.mobile.ui.components.appCardBorder
import com.sub2api.mobile.ui.components.formatDate
import com.sub2api.mobile.ui.components.formatMoney
import com.sub2api.mobile.ui.components.maskKey
import com.sub2api.mobile.ui.icons.Icon
import com.sub2api.mobile.ui.icons.Icons

@Composable
fun KeysScreen(
    state: AppUiState,
    onCreate: (String, Long?, Double?, Int?, () -> Unit) -> Unit,
    onToggle: (ApiKey) -> Unit,
    onDelete: (ApiKey) -> Unit,
) {
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ApiKey?>(null) }
    val clipboard = LocalClipboardManager.current

    Box(Modifier.fillMaxSize()) {
        if (state.keys.isEmpty()) {
            EmptyState("还没有 API Key", "点击右下角按钮创建")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text("${state.keys.count { it.status == "active" }} 个可用 · ${state.keys.size} 个全部", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(state.keys, key = { it.id }) { key ->
                    KeyCard(
                        key = key,
                        onCopy = { clipboard.setText(AnnotatedString(key.key)) },
                        onToggle = { onToggle(key) },
                        onDelete = { deleteTarget = key },
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            shape = AppCardShape,
        ) { Icon(Icons.Outlined.Add, "创建 API Key") }
    }

    if (showCreate) {
        CreateKeyDialog(
            state = state,
            onDismiss = { if (!state.loading) showCreate = false },
            onCreate = { name, groupId, quota, expires -> onCreate(name, groupId, quota, expires) { showCreate = false } },
        )
    }
    deleteTarget?.let { key ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除 API Key") },
            text = { Text("“${key.name}”删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = { onDelete(key); deleteTarget = null }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun KeyCard(key: ApiKey, onCopy: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = appCardBorder(),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(key.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(key.group?.name ?: "默认分组", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = key.status == "active", onCheckedChange = { onToggle() })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(maskKey(key.key), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, "复制") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("额度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (key.quota > 0) "${formatMoney(key.quotaUsed)} / ${formatMoney(key.quota)}" else "不限", style = MaterialTheme.typography.labelMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("到期时间", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDate(key.expiresAt), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun CreateKeyDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onCreate: (String, Long?, Double?, Int?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<com.sub2api.mobile.data.Group?>(null) }
    var groupMenu by remember { mutableStateOf(false) }
    var quota by rememberSaveable { mutableStateOf("") }
    var expires by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建 API Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { groupMenu = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(selectedGroup?.name ?: "默认分组", modifier = Modifier.weight(1f), maxLines = 1)
                        Icon(Icons.Outlined.ExpandMore, null)
                    }
                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                        DropdownMenuItem(text = { Text("默认分组") }, onClick = { selectedGroup = null; groupMenu = false })
                        state.groups.forEach { group ->
                            DropdownMenuItem(text = { Text(group.name) }, onClick = { selectedGroup = group; groupMenu = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = quota,
                    onValueChange = { quota = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("额度 USD（留空为不限）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = expires,
                    onValueChange = { if (it.all(Char::isDigit)) expires = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("有效天数（留空为永久）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, selectedGroup?.id, quota.toDoubleOrNull()?.takeIf { it > 0 }, expires.toIntOrNull()?.takeIf { it > 0 }) },
                enabled = name.isNotBlank() && !state.loading,
            ) { Text(if (state.loading) "创建中" else "创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.loading) { Text("取消") } },
    )
}
