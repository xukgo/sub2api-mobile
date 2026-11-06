package com.sub2api.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.data.AiAccount
import com.sub2api.mobile.data.AiAccountQuota
import com.sub2api.mobile.data.AiQuotaMetric
import com.sub2api.mobile.data.CreateAiAccountRequest
import com.sub2api.mobile.data.Group
import com.sub2api.mobile.data.UpdateAiAccountRequest
import com.sub2api.mobile.data.summarizeAiQuotas
import com.sub2api.mobile.ui.components.AppCardShape
import com.sub2api.mobile.ui.components.ContinuousLinearProgressIndicator
import com.sub2api.mobile.ui.components.EmptyState
import com.sub2api.mobile.ui.components.FullScreenLoading
import com.sub2api.mobile.ui.components.appCardBorder
import com.sub2api.mobile.ui.components.formatDate
import com.sub2api.mobile.ui.icons.Icon
import com.sub2api.mobile.ui.icons.Icons
import java.text.DecimalFormat

private data class Choice(val value: String, val label: String)

private val platformChoices = listOf(
    Choice("", "全部"),
    Choice("anthropic", "Claude"),
    Choice("openai", "OpenAI"),
    Choice("gemini", "Gemini"),
    Choice("antigravity", "Antigravity"),
    Choice("grok", "Grok"),
)

private val accountTypeChoices = listOf(
    Choice("oauth", "OAuth"),
    Choice("setup-token", "Setup Token"),
    Choice("apikey", "API Key"),
    Choice("upstream", "上游中继"),
    Choice("bedrock", "Bedrock"),
    Choice("service_account", "服务账号"),
)

private val credentialChoices = listOf(
    Choice("cookie", "Cookie"),
    Choice("session_key", "Session Key"),
    Choice("api_key", "API Key"),
    Choice("access_token", "Access Token"),
    Choice("refresh_token", "Refresh Token"),
    Choice("base_url", "Base URL"),
    Choice("service_account_json", "Service Account JSON"),
    Choice("aws_access_key_id", "AWS Access Key ID"),
    Choice("aws_secret_access_key", "AWS Secret Access Key"),
    Choice("aws_session_token", "AWS Session Token"),
)

@Composable
fun AccountsScreen(
    state: AppUiState,
    onFilter: (String?, String?, String?) -> Unit,
    onCreate: (CreateAiAccountRequest, String, Boolean, () -> Unit) -> Unit,
    onUpdate: (AiAccount, UpdateAiAccountRequest, () -> Unit) -> Unit,
    onCredential: (AiAccount, String, String, Boolean, () -> Unit) -> Unit,
    onTest: (AiAccount) -> Unit,
    onRefreshCredential: (AiAccount) -> Unit,
    onClearError: (AiAccount) -> Unit,
    onToggle: (AiAccount) -> Unit,
    onDelete: (AiAccount) -> Unit,
    onRefreshQuotas: (String?, String?, String?) -> Unit,
) {
    var platform by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var editorAccount by remember { mutableStateOf<AiAccount?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var credentialAccount by remember { mutableStateOf<AiAccount?>(null) }
    var deleteAccount by remember { mutableStateOf<AiAccount?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    IconButton(onClick = { onFilter(platform, null, search) }) {
                        Icon(Icons.Outlined.Search, "搜索")
                    }
                },
                label = { Text("搜索账号") },
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                platformChoices.forEach { choice ->
                    FilterChip(
                        selected = platform == choice.value,
                        onClick = {
                            platform = choice.value
                            onFilter(choice.value, null, search)
                        },
                        label = { Text(choice.label) },
                    )
                }
            }
            if (state.allAiAccounts.isNotEmpty()) {
                QuotaSummaryBand(
                    accounts = state.allAiAccounts,
                    quotas = state.aiAccountQuotas,
                    loading = state.quotaLoading,
                    onAdd = { editorAccount = null; showEditor = true },
                    onRefresh = { onRefreshQuotas(platform, null, search) },
                )
            }
            when {
                state.accountsLoading && state.aiAccounts.isEmpty() -> FullScreenLoading("正在加载平台账号")
                state.aiAccounts.isEmpty() -> EmptyState("暂无 AI 平台账号", "点击右下角按钮添加")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "${state.aiAccounts.count { it.status == "active" }} 个启用 · ${state.aiAccounts.size} 个全部",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    itemsIndexed(
                        state.aiAccounts,
                        key = { index, account -> "ai-account-${account.id}-$index" },
                    ) { _, account ->
                        AccountCard(
                            account = account,
                            quota = state.aiAccountQuotas[account.id],
                            quotaLoading = state.quotaLoading && account.id !in state.aiAccountQuotas,
                            busy = state.accountsLoading,
                            onTest = { onTest(account) },
                            onCredential = { credentialAccount = account },
                            onRefresh = { onRefreshCredential(account) },
                            onEdit = { editorAccount = account; showEditor = true },
                            onClearError = { onClearError(account) },
                            onToggle = { onToggle(account) },
                            onDelete = { deleteAccount = account },
                        )
                    }
                }
            }
        }
        if (state.allAiAccounts.isEmpty() && !state.accountsLoading) {
            FloatingActionButton(
                onClick = { editorAccount = null; showEditor = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                shape = RoundedCornerShape(8.dp),
            ) { Icon(Icons.Outlined.Add, "添加 AI 平台账号") }
        }
    }

    if (showEditor) {
        AccountEditorDialog(
            account = editorAccount,
            groups = state.adminGroups,
            busy = state.accountsLoading,
            onDismiss = { if (!state.accountsLoading) showEditor = false },
            onCreate = { request, cookie, exchange -> onCreate(request, cookie, exchange) { showEditor = false } },
            onUpdate = { account, request -> onUpdate(account, request) { showEditor = false } },
        )
    }
    credentialAccount?.let { account ->
        CredentialDialog(
            account = account,
            busy = state.accountsLoading,
            onDismiss = { if (!state.accountsLoading) credentialAccount = null },
            onSave = { field, value, exchange ->
                onCredential(account, field, value, exchange) { credentialAccount = null }
            },
        )
    }
    deleteAccount?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteAccount = null },
            title = { Text("删除平台账号") },
            text = { Text("“${account.name}”及其 Cookie/Token 将从服务器删除，此操作无法恢复。") },
            confirmButton = {
                TextButton(onClick = { onDelete(account); deleteAccount = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteAccount = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun QuotaSummaryBand(
    accounts: List<AiAccount>,
    quotas: Map<Long, AiAccountQuota>,
    loading: Boolean,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
) {
    val summary = remember(accounts, quotas) { summarizeAiQuotas(accounts, quotas) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = AppCardShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
        border = appCardBorder(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(MaterialTheme.colorScheme.surface, AppCardShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.AccountBalanceWallet, null, modifier = Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("全部账号剩余额度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (summary.measuredAccounts > 0) "合计 ${formatTotalPercent(summary.totalRemainingPercent)}" else if (loading) "计算中" else "--",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (summary.measuredAccounts > 0) quotaColor(summary.capacityRemainingPercent) else MaterialTheme.colorScheme.onSurface,
                )
                if (summary.measuredAccounts > 0) {
                    ContinuousLinearProgressIndicator(
                        progress = { (summary.capacityRemainingPercent / 100.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 5.dp).height(4.dp),
                        color = quotaColor(summary.capacityRemainingPercent),
                        trackColor = MaterialTheme.colorScheme.surface,
                    )
                }
                Text(
                    when {
                        loading && summary.measuredAccounts > 0 -> "刷新中 · 已合计 ${summary.measuredAccounts}/${summary.totalAccounts} 个账号"
                        summary.measuredAccounts > 0 -> "满额 ${formatTotalPercent(summary.totalCapacityPercent)} · 已合计 ${summary.measuredAccounts}/${summary.totalAccounts} 个账号"
                        loading -> "正在读取 · 已合计 ${summary.measuredAccounts}/${summary.totalAccounts} 个账号"
                        else -> "已合计 ${summary.measuredAccounts}/${summary.totalAccounts} 个账号"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, "添加 AI 平台账号")
            }
            IconButton(onClick = onRefresh, enabled = !loading) {
                if (loading) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Refresh, "刷新剩余额度")
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: AiAccount,
    quota: AiAccountQuota?,
    quotaLoading: Boolean,
    busy: Boolean,
    onTest: () -> Unit,
    onCredential: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onClearError: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val credentials = credentialBadges(account)
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = appCardBorder(),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = platformColor(account.platform).copy(alpha = 0.14f),
                    contentColor = platformColor(account.platform),
                ) { Text(platformLabel(account.platform), modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(account.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(typeLabel(account.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = account.status == "active", onCheckedChange = { onToggle() }, enabled = !busy)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "更多") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("编辑设置") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
                        if (account.type == "oauth" || account.type == "setup-token") {
                            DropdownMenuItem(text = { Text("刷新凭据") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = { menu = false; onRefresh() })
                        }
                        if (account.status == "error") {
                            DropdownMenuItem(text = { Text("清除错误") }, leadingIcon = { Icon(Icons.Outlined.ErrorOutline, null) }, onClick = { menu = false; onClearError() })
                        }
                        DropdownMenuItem(text = { Text("删除账号") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (account.errorMessage?.isNotBlank() == true) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Text(account.errorMessage, modifier = Modifier.padding(start = 7.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (credentials.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    credentials.forEach { credential ->
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(credential, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            AccountQuotaBlock(quota = quota, loading = quotaLoading)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(account.groups.joinToString { it.name }.ifBlank { "未绑定分组" }, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "并发 ${account.currentConcurrency}/${account.concurrency} · 最近 ${formatDate(account.lastUsedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onTest, enabled = !busy) { Icon(Icons.Outlined.NetworkCheck, "测试连接") }
                IconButton(onClick = onCredential, enabled = !busy) { Icon(Icons.Outlined.Cookie, "更新 Cookie 或凭据") }
            }
        }
    }
}

@Composable
private fun AccountQuotaBlock(quota: AiAccountQuota?, loading: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.DataUsage, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                "剩余额度",
                modifier = Modifier.padding(start = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            val remaining = quota?.remainingPercent
            val credits = quota?.metrics?.firstOrNull { it.unit == "credits" }
            Text(
                when {
                    remaining != null -> formatPercent(remaining)
                    credits != null -> formatMetricValue(credits)
                    loading -> "读取中"
                    else -> "--"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (remaining != null) quotaColor(remaining) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        quota?.remainingPercent?.let { remaining ->
            ContinuousLinearProgressIndicator(
                progress = { (remaining / 100.0).toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(5.dp),
                color = quotaColor(remaining),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        val metrics = quota?.metrics.orEmpty()
            .sortedWith(compareBy<AiQuotaMetric> { it.remainingPercent ?: Double.MAX_VALUE }.thenBy { it.label })
            .take(4)
        if (metrics.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                metrics.forEach { metric ->
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            "${shortQuotaLabel(metric.label)} ${formatMetricValue(metric)}",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp).widthIn(max = 180.dp),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else if (!loading) {
            Text(
                if (quota?.error.isNullOrBlank()) "额度未知" else "额度读取失败",
                modifier = Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun AccountEditorDialog(
    account: AiAccount?,
    groups: List<Group>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (CreateAiAccountRequest, String, Boolean) -> Unit,
    onUpdate: (AiAccount, UpdateAiAccountRequest) -> Unit,
) {
    val editing = account != null
    var name by rememberSaveable { mutableStateOf(account?.name.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(account?.notes.orEmpty()) }
    var platform by rememberSaveable { mutableStateOf(account?.platform ?: "anthropic") }
    var type by rememberSaveable { mutableStateOf(account?.type ?: "oauth") }
    var concurrency by rememberSaveable { mutableStateOf((account?.concurrency ?: 1).toString()) }
    var priority by rememberSaveable { mutableStateOf((account?.priority ?: 0).toString()) }
    var rateMultiplier by rememberSaveable { mutableStateOf((account?.rateMultiplier ?: 1.0).toString()) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var cookie by rememberSaveable { mutableStateOf("") }
    var accessToken by rememberSaveable { mutableStateOf("") }
    var refreshToken by rememberSaveable { mutableStateOf("") }
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var extraJson by rememberSaveable { mutableStateOf("") }
    var exchangeCookie by rememberSaveable { mutableStateOf(true) }
    var selectedGroups by remember { mutableStateOf((account?.groupIds?.ifEmpty { account.groups.map { it.id } } ?: emptyList()).toSet()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(horizontal = 12.dp).widthIn(max = 640.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (editing) "编辑 AI 平台账号" else "添加 AI 平台账号", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.Outlined.Close, "关闭") }
                }
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("账号名称") }, singleLine = true) }
                    item { OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2, maxLines = 3) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChoiceMenu("平台", platformChoices.drop(1), platform, { platform = it }, Modifier.weight(1f), enabled = !editing)
                            ChoiceMenu("认证类型", accountTypeChoices, type, { type = it }, Modifier.weight(1f), enabled = !editing)
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(concurrency, { if (it.all(Char::isDigit)) concurrency = it }, Modifier.weight(1f), label = { Text("并发") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                            OutlinedTextField(priority, { if (it.all { c -> c.isDigit() || c == '-' }) priority = it }, Modifier.weight(1f), label = { Text("优先级") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                            OutlinedTextField(rateMultiplier, { rateMultiplier = it }, Modifier.weight(1f), label = { Text("倍率") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        }
                    }
                    if (!editing) {
                        item { Text("认证凭据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
                        item { SecretField(apiKey, { apiKey = it }, "API Key") }
                        item { SecretField(cookie, { cookie = it }, if (platform == "anthropic") "Session Key / Cookie" else "Cookie") }
                        if (platform == "anthropic" && (type == "oauth" || type == "setup-token")) {
                            item {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("自动换取 OAuth 凭据", style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = exchangeCookie, onCheckedChange = { exchangeCookie = it })
                                }
                            }
                        }
                        item { SecretField(accessToken, { accessToken = it }, "Access Token") }
                        item { SecretField(refreshToken, { refreshToken = it }, "Refresh Token") }
                        item { OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("Base URL") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)) }
                        item { SecretField(extraJson, { extraJson = it }, "其他凭据 JSON") }
                    }
                    if (groups.isNotEmpty()) {
                        item { Text("绑定分组", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
                        itemsIndexed(groups, key = { index, group -> "editor-group-${group.id}-$index" }) { _, group ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = group.id in selectedGroups,
                                    onCheckedChange = { checked -> selectedGroups = if (checked) selectedGroups + group.id else selectedGroups - group.id },
                                )
                                Column(Modifier.padding(start = 6.dp)) {
                                    Text(group.name)
                                    Text(platformLabel(group.platform), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 56.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                error = "请输入账号名称"
                                return@Button
                            }
                            if (editing) {
                                onUpdate(
                                    account!!,
                                    UpdateAiAccountRequest(
                                        name = name.trim(),
                                        notes = notes.trim(),
                                        type = type,
                                        concurrency = concurrency.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                        priority = priority.toIntOrNull() ?: 0,
                                        rateMultiplier = rateMultiplier.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 1.0,
                                        groupIds = selectedGroups.toList(),
                                    ),
                                )
                            } else {
                                val credentials = runCatching { buildCredentials(apiKey, accessToken, refreshToken, baseUrl, extraJson) }
                                    .getOrElse { error = it.message ?: "凭据 JSON 格式错误"; return@Button }
                                onCreate(
                                    CreateAiAccountRequest(
                                        name = name.trim(),
                                        notes = notes.trim().takeIf { it.isNotBlank() },
                                        platform = platform,
                                        type = type,
                                        credentials = credentials,
                                        concurrency = concurrency.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                        priority = priority.toIntOrNull() ?: 0,
                                        rateMultiplier = rateMultiplier.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 1.0,
                                        groupIds = selectedGroups.toList(),
                                    ),
                                    cookie,
                                    exchangeCookie && platform == "anthropic" && (type == "oauth" || type == "setup-token"),
                                )
                            }
                        },
                        enabled = !busy,
                    ) { Text(if (busy) "保存中" else "保存") }
                }
            }
        }
    }
}

@Composable
private fun CredentialDialog(account: AiAccount, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, Boolean) -> Unit) {
    val anthropicExchange = account.platform == "anthropic" && (account.type == "oauth" || account.type == "setup-token")
    var field by rememberSaveable { mutableStateOf(if (anthropicExchange) "session_key" else "cookie") }
    var value by rememberSaveable { mutableStateOf("") }
    var exchange by rememberSaveable { mutableStateOf(anthropicExchange) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新 ${account.name} 的凭据") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ChoiceMenu("凭据类型", credentialChoices, field, { field = it }, Modifier.fillMaxWidth(), enabled = !exchange)
                val fieldLabel = credentialChoices.firstOrNull { it.value == field }?.label ?: "凭据"
                if (field == "base_url") {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(fieldLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                    )
                } else {
                    SecretField(value, { value = it }, fieldLabel)
                }
                if (anthropicExchange) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("通过 Cookie 自动换取 OAuth", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = exchange,
                            onCheckedChange = {
                                exchange = it
                                if (it) field = "session_key"
                            },
                        )
                    }
                }
                Text(
                    if (exchange) "Session Key 将由服务器换取完整凭据。" else "仅更新此字段，服务器会保留账号的其他 Cookie/Token。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(field, value, exchange) }, enabled = value.isNotBlank() && !busy) { Text(if (busy) "更新中" else "更新") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } },
    )
}

@Composable
private fun SecretField(value: String, onValueChange: (String) -> Unit, label: String) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "隐藏" else "显示")
            }
        },
        singleLine = true,
    )
}

@Composable
private fun ChoiceMenu(
    label: String,
    choices: List<Choice>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = enabled) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(choices.firstOrNull { it.value == selected }?.label ?: selected, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label) },
                    onClick = { onSelected(choice.value); expanded = false },
                    leadingIcon = if (choice.value == selected) ({ Icon(Icons.Outlined.CheckCircle, null) }) else null,
                )
            }
        }
    }
}

internal fun buildCredentials(
    apiKey: String,
    accessToken: String,
    refreshToken: String,
    baseUrl: String,
    extraJson: String,
): Map<String, Any?> {
    val result = linkedMapOf<String, Any?>()
    apiKey.trim().takeIf { it.isNotBlank() }?.let { result["api_key"] = it }
    accessToken.trim().takeIf { it.isNotBlank() }?.let { result["access_token"] = it }
    refreshToken.trim().takeIf { it.isNotBlank() }?.let { result["refresh_token"] = it }
    baseUrl.trim().takeIf { it.isNotBlank() }?.let { result["base_url"] = it }
    if (extraJson.isNotBlank()) {
        val json = JsonParser.parseString(extraJson.trim())
        require(json.isJsonObject) { "其他凭据必须是 JSON 对象" }
        json.asJsonObject.entrySet().forEach { (key, value) ->
            result[key] = Gson().fromJson(value, Any::class.java)
        }
    }
    return result
}

private fun credentialBadges(account: AiAccount): List<String> = buildList {
    if (account.hasCredential("cookie") || account.hasCredential("session_key")) add("Cookie")
    if (account.hasCredential("api_key")) add("API Key")
    if (account.hasCredential("access_token")) add("Access Token")
    if (account.hasCredential("refresh_token")) add("Refresh Token")
    if (account.hasCredential("service_account_json") || account.hasCredential("service_account")) add("Service Account")
}

@Composable
private fun quotaColor(remainingPercent: Double): Color = when {
    remainingPercent <= 20.0 -> MaterialTheme.colorScheme.error
    remainingPercent <= 50.0 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

private fun formatPercent(value: Double): String = "${DecimalFormat("0.#").format(value.coerceIn(0.0, 100.0))}%"

private fun formatTotalPercent(value: Double): String = "${DecimalFormat("0.#").format(value.coerceAtLeast(0.0))}%"

private fun formatMetricValue(metric: AiQuotaMetric): String {
    val amount = metric.remainingAmount
    return when {
        amount != null && metric.unit == "USD" -> "$${DecimalFormat("#,##0.00").format(amount)}"
        amount != null && metric.unit == "credits" -> "${formatQuotaNumber(amount)} Credits"
        amount != null && !metric.unit.isNullOrBlank() -> "${formatQuotaNumber(amount)} ${metric.unit}"
        metric.remainingPercent != null -> formatPercent(metric.remainingPercent)
        else -> "--"
    }
}

private fun formatQuotaNumber(value: Double): String = when {
    value >= 1_000_000_000 -> "${DecimalFormat("0.#").format(value / 1_000_000_000)}B"
    value >= 1_000_000 -> "${DecimalFormat("0.#").format(value / 1_000_000)}M"
    value >= 1_000 -> "${DecimalFormat("0.#").format(value / 1_000)}K"
    else -> DecimalFormat("#,##0.##").format(value)
}

private fun shortQuotaLabel(label: String): String = if (label.length <= 20) label else "${label.take(17)}…"

private fun platformLabel(platform: String): String = when (platform.lowercase()) {
    "anthropic" -> "Claude"
    "openai" -> "OpenAI"
    "gemini" -> "Gemini"
    "antigravity" -> "Antigravity"
    "grok" -> "Grok"
    else -> platform.ifBlank { "未知" }
}

private fun typeLabel(type: String): String = accountTypeChoices.firstOrNull { it.value == type }?.label ?: type

private fun platformColor(platform: String): Color = when (platform.lowercase()) {
    "anthropic" -> Color(0xFF9A4E2E)
    "openai" -> Color(0xFF176448)
    "gemini" -> Color(0xFF3568B8)
    "antigravity" -> Color(0xFF7252A3)
    "grok" -> Color(0xFF2E3431)
    else -> Color(0xFF5B655F)
}
