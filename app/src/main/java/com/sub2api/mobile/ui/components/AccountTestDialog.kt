package com.sub2api.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.data.AiAccount

@Composable
fun AccountTestDialog(
    account: AiAccount,
    state: AppUiState,
    onLoadModels: (AiAccount) -> Unit,
    onRun: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var model by remember(account.id) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val models = state.accountModels[account.id].orEmpty()
    val result = state.accountTestResults[account.id]
    LaunchedEffect(account.id) { onLoadModels(account) }
    AlertDialog(
        onDismissRequest = { if (!state.accountsLoading) onDismiss() },
        title = { Text("测试 ${account.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("将通过该账号向所选模型发起测试请求。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("模型 ID（留空使用服务默认模型）") }, singleLine = true, enabled = !state.accountsLoading)
                Box {
                    TextButton(onClick = { menu = true }, enabled = models.isNotEmpty() && !state.accountsLoading) { Text("从账号可用模型中选择") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                        DropdownMenuItem(text = { Text("服务默认模型") }, onClick = { model = ""; menu = false })
                        models.forEach { item ->
                            DropdownMenuItem(text = { Text(if (item.displayName.isNotBlank() && item.displayName != item.modelId)
                                "${item.displayName} (${item.modelId})" else item.modelId) }, onClick = { model = item.modelId; menu = false })
                        }
                    }
                }
                if (account.id in state.accountModelsLoading) Text("正在加载可用模型…")
                state.accountModelErrors[account.id]?.let { Text("模型列表加载失败：$it；仍可手动输入模型 ID。", color = MaterialTheme.colorScheme.error) }
                if (models.isEmpty() && account.id !in state.accountModelsLoading && account.id !in state.accountModelErrors) {
                    Text("后端未返回模型列表，可手动输入或使用默认模型。", style = MaterialTheme.typography.bodySmall)
                }
                if (state.accountsLoading) FullScreenLoading("正在测试连接")
                result?.let {
                    Text(if (it.success) "测试通过" else "测试失败", color = if (it.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    it.model?.let { actual -> Text("测试模型：$actual") }
                    Text(it.message)
                    it.latencyMs?.let { elapsed -> Text("总耗时：$elapsed ms") }
                }
            }
        },
        confirmButton = { Button(onClick = { onRun(model) }, enabled = !state.accountsLoading) { Text("开始测试") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.accountsLoading) { Text("关闭") } },
    )
}
