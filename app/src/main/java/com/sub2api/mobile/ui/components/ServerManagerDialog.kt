package com.sub2api.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.data.ServerSummary

@Composable
fun ServerManagerDialog(
    state: AppUiState,
    onSelect: (String) -> Unit,
    onSave: (String?, String, String) -> Boolean,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ServerSummary?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) { if (editingId == null) "添加服务" else "编辑服务" } else "服务管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (editing) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("服务名称") }, singleLine = true)
                    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("服务器地址") }, singleLine = true)
                    if (editingId != null) Text("修改地址后需要重新登录该服务。", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("每个服务独立登录，切换会保留其他服务的登录状态。", style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.servers.isEmpty()) item { Text("还没有添加服务") }
                        items(state.servers, key = { it.id }) { server ->
                            Column(Modifier.fillMaxWidth().clickable { onSelect(server.id) }.padding(vertical = 8.dp)) {
                                Text(server.name + if (server.id == state.activeServerId) " · 当前" else "", fontWeight = FontWeight.SemiBold)
                                Text(server.serverUrl.removeSuffix("/api/v1/"), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    (if (server.isAuthenticated) "已保存登录" else "未登录") + server.email.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row {
                                    TextButton(onClick = { onSelect(server.id) }) { Text(if (server.id == state.activeServerId) "进入" else "切换") }
                                    TextButton(onClick = { editingId = server.id; name = server.name; url = server.serverUrl; editing = true }) { Text("编辑") }
                                    TextButton(onClick = { deleting = server }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
                state.serverManagerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = state.storageError == null && (!editing || name.isNotBlank() && url.isNotBlank()),
                onClick = {
                    if (editing) {
                        if (onSave(editingId, name, url)) editing = false
                    } else {
                        editingId = null; name = ""; url = ""; editing = true
                    }
                },
            ) { Text(if (editing) "保存" else "添加服务") }
        },
        dismissButton = {
            TextButton(onClick = { if (editing) editing = false else onDismiss() }) { Text(if (editing) "返回列表" else "完成") }
        },
    )
    deleting?.let { server ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除服务") },
            text = { Text("删除“${server.name}”及其本地登录信息？服务端数据不会被删除。") },
            confirmButton = { TextButton(onClick = { onRemove(server.id); deleting = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}
