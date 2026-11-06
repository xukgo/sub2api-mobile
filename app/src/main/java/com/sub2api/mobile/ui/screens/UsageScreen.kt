package com.sub2api.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.UsageViewState
import com.sub2api.mobile.data.UsageFilters
import com.sub2api.mobile.data.UsageLog
import com.sub2api.mobile.data.RankingPeriod
import com.sub2api.mobile.data.accountLabel
import com.sub2api.mobile.data.channelLabel
import com.sub2api.mobile.data.channelPerformance
import com.sub2api.mobile.data.outputRate
import com.sub2api.mobile.data.sortedUserRanking
import com.sub2api.mobile.data.usageDateRange
import com.sub2api.mobile.ui.components.AppCardShape
import com.sub2api.mobile.ui.components.EmptyState
import com.sub2api.mobile.ui.components.FullScreenLoading
import com.sub2api.mobile.ui.components.MetricCard
import com.sub2api.mobile.ui.components.SectionHeader
import com.sub2api.mobile.ui.components.appCardBorder
import com.sub2api.mobile.ui.components.formatCount
import com.sub2api.mobile.ui.components.formatDate
import com.sub2api.mobile.ui.components.formatMoney
import com.sub2api.mobile.ui.icons.Icon
import com.sub2api.mobile.ui.icons.Icons
import java.time.ZoneId
import java.util.Locale

@Composable
fun UsageScreen(
    state: AppUiState,
    onPeriodChange: (String) -> Unit,
    onRefresh: () -> Unit = {},
    onPageChange: (Int) -> Unit = {},
) {
    LaunchedEffect(state.user?.id, state.user?.role) { onRefresh() }
    UsageRecords(state.personalUsage, false, onPeriodChange, onRefresh, onPageChange)
}

@Composable
fun AdminUsageScreen(
    state: AppUiState,
    onLoad: (String, Int, UsageFilters) -> Unit,
    onRanking: (String, String) -> Unit,
    onCancelRanking: () -> Unit,
) {
    if (state.user?.role != "admin") { EmptyState("仅管理员可查看所有用量"); return }
    var mode by rememberSaveable { mutableStateOf("records") }
    DisposableEffect(Unit) { onDispose { onCancelRanking() } }
    LaunchedEffect(mode) {
        if (mode == "records") { onCancelRanking(); onLoad(state.adminUsage.period, 1, state.adminUsage.filters) }
        else onRanking(state.rankingPeriod, state.rankingMetric)
    }
    Column {
        UsageChoices(listOf("records" to "所有记录", "ranking" to "用户排行"), mode, { mode = it })
        if (mode == "ranking") UserRanking(state, onRanking)
        else UsageRecords(state.adminUsage, true,
            { onLoad(it, 1, state.adminUsage.filters) },
            { onLoad(state.adminUsage.period, state.adminUsage.page, state.adminUsage.filters) },
            { onLoad(state.adminUsage.period, it, state.adminUsage.filters) },
            { onLoad(state.adminUsage.period, 1, it) })
    }
}

@Composable
private fun UsageRecords(
    state: UsageViewState,
    admin: Boolean,
    onPeriod: (String) -> Unit,
    onRefresh: () -> Unit,
    onPage: (Int) -> Unit,
    onFilter: (UsageFilters) -> Unit = {},
) {
    var showPerformance by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.page, state.period, state.filters) { listState.scrollToItem(0) }
    val dates = usageDateRange(state.period)
    LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { UsageChoices(listOf("today" to "今日", "week" to "近 7 天", "month" to "近 30 天") +
            (if (admin) listOf("all" to "全部") else emptyList()), state.period, onPeriod) }
        item { Text("${dates.start} — ${dates.end} · ${ZoneId.systemDefault().id}", style = MaterialTheme.typography.labelSmall) }
        if (admin) item { UsageFilterForm(state.filters, onFilter) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("请求", formatCount(state.stats?.totalRequests ?: 0), Modifier.weight(1f))
                MetricCard("Tokens", formatCount(state.stats?.totalTokens ?: 0), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("实际消费", formatMoney(state.stats?.totalActualCost ?: 0.0), Modifier.weight(1f))
                MetricCard("平均总耗时", elapsedLabel(state.stats?.averageDurationMs), Modifier.weight(1f))
            }
        }
        item { TextButton(onClick = onRefresh, enabled = !state.loading) { Text("刷新记录") } }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if (state.loading) item { FullScreenLoading() }
        if (admin && state.logs.isNotEmpty()) {
            item { TextButton(onClick = { showPerformance = !showPerformance }) { Text(if (showPerformance) "收起渠道性能样本" else "查看当前页渠道性能样本") } }
            if (showPerformance) {
                item { Text("仅统计当前页 ${state.logs.size} 条记录；样本 TPS = 输出 Token 合计 ÷ 总耗时合计，不代表渠道实时总吞吐。", style = MaterialTheme.typography.bodySmall) }
                items(channelPerformance(state.logs)) { sample ->
                    Card(shape = AppCardShape, border = appCardBorder()) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${sample.account} · ${sample.channel}", fontWeight = FontWeight.SemiBold)
                            Text("${sample.requests} 条 · 首字 ${elapsedLabel(sample.firstTokenMs)} · 总耗时 ${elapsedLabel(sample.durationMs)}", style = MaterialTheme.typography.bodySmall)
                            Text("样本 TPS ${rateLabel(sample.outputRate)}")
                        }
                    }
                }
            }
        }
        item { SectionHeader(if (admin) "全站请求记录" else "我的请求记录", "共 ${state.total} 条 · 每页 50 条") }
        if (state.logs.isEmpty() && !state.loading && state.error == null) item { EmptyState("当前范围没有使用记录") }
        items(state.logs, key = { it.id }) { log -> UsageLogCard(log, admin) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onPage(state.page - 1) }, enabled = !state.loading && state.page > 1) { Text("上一页") }
                Text("${state.page} / ${state.pages}", modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = { onPage(state.page + 1) }, enabled = !state.loading && state.page < state.pages) { Text("下一页") }
            }
        }
        item { Text("TPS 与网页版一致：输出 Token ÷ 完整请求耗时。图片、无有效耗时或未提供的数据显示 —。账号名称及渠道详情受后端权限控制。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun UsageFilterForm(filters: UsageFilters, onApply: (UsageFilters) -> Unit) {
    var user by rememberSaveable { mutableStateOf(filters.userId?.toString().orEmpty()) }
    var account by rememberSaveable { mutableStateOf(filters.accountId?.toString().orEmpty()) }
    var model by rememberSaveable { mutableStateOf(filters.model.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(user, { user = it }, Modifier.weight(1f), label = { Text("用户 ID（可选）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(account, { account = it }, Modifier.weight(1f), label = { Text("账号 ID（可选）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        }
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("模型名（可选）") }, singleLine = true)
        Row {
            Button(onClick = {
                if (listOf(user, account).any { it.isNotBlank() && (it.toLongOrNull()?.let { id -> id > 0 } != true) }) {
                    error = "用户 ID 和账号 ID 需要是正整数"
                } else {
                    error = null
                    onApply(UsageFilters(user.toLongOrNull(), account.toLongOrNull(), model.trim().takeIf { it.isNotEmpty() }))
                }
            }) { Text("应用筛选") }
            TextButton(onClick = { user = ""; account = ""; model = ""; error = null; onApply(UsageFilters()) }) { Text("清除筛选") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun UsageLogCard(log: UsageLog, admin: Boolean = false) {
    Card(shape = AppCardShape, border = appCardBorder(), colors = CardDefaults.cardColors(
        containerColor = firstTokenCardColor(log.firstTokenMs, MaterialTheme.colorScheme.surface),
        contentColor = MaterialTheme.colorScheme.onSurface,
    )) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(log.model.ifBlank { "未知模型" }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDate(log.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            (log.upstreamResponseModel ?: log.upstreamModel)?.takeIf { it != log.model }?.let { Text("实际模型：$it", style = MaterialTheme.typography.bodySmall) }
            if (admin) Text("用户：${log.user?.email?.takeIf { it.isNotBlank() } ?: log.user?.username?.takeIf { it.isNotBlank() } ?: "#${log.userId}"}")
            Text("账号：${log.accountLabel}", style = MaterialTheme.typography.bodySmall)
            if ((log.channelId ?: 0) > 0) Text(log.channelLabel, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TimingMetric("首字", elapsedLabel(log.firstTokenMs?.toDouble()))
                TimingMetric("总耗时", elapsedLabel(log.durationMs?.toDouble()))
                TimingMetric("TPS", rateLabel(log.outputRate))
            }
            Text("${formatCount(log.totalTokens)} Tokens · ${formatMoney(log.actualCost)} · ${if (log.stream) "Stream" else "Sync"}", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TimingMetric(label: String, value: String) {
    Column { Text(label, style = MaterialTheme.typography.labelSmall); Text(value, style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun UserRanking(state: AppUiState, onLoad: (String, String) -> Unit) {
    val ranking = state.ranking
    val rows = remember(ranking, state.rankingMetric) { sortedUserRanking(ranking?.rows.orEmpty(), state.rankingMetric).take(50) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { RankingPeriodPicker(state.rankingPeriod) { onLoad(it, state.rankingMetric) } }
        if (state.rankingPeriod == "24h") item {
            Text("与网页版一致：按日期范围聚合，实际日期见下方；这不是精确滚动的 24 小时。", style = MaterialTheme.typography.bodySmall)
        }
        item { UsageChoices(listOf("total_tokens" to "Tokens", "requests" to "请求数", "actual_cost" to "消费"), state.rankingMetric, { onLoad(state.rankingPeriod, it) }) }
        item { TextButton(onClick = { onLoad(state.rankingPeriod, state.rankingMetric) }) { Text("刷新排行") } }
        if (state.rankingLoading) item { FullScreenLoading("正在加载用户排行") }
        state.rankingError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        ranking?.let {
            item { Text("${it.dates.start} — ${it.dates.end}（含结束日期）· ${it.timezone}", style = MaterialTheme.typography.bodySmall) }
            item { SectionHeader("用户用量排行", "Top 50") }
        }
        items(rows, key = { it.userId }) { row ->
            Card(shape = AppCardShape, border = appCardBorder()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${rows.indexOf(row) + 1}. ${row.email.ifBlank { row.username.ifBlank { "用户 #${row.userId}" } }}", fontWeight = FontWeight.SemiBold)
                    Text("${formatCount(row.totalTokens)} Tokens · ${row.requests} 次请求 · ${formatMoney(row.actualCost)}")
                    Text("输入 ${formatCount(row.inputTokens)} · 输出 ${formatCount(row.outputTokens)} · 缓存 ${formatCount(row.cacheTokens)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (ranking != null && rows.isEmpty() && !state.rankingLoading) item { EmptyState("这个时间范围暂无用户用量") }
        item { Text("默认显示今天，所有范围均由服务端汇总，并按所选指标显示前 50 名。", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun RankingPeriodPicker(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = RankingPeriod.entries.firstOrNull { it.key == selected } ?: RankingPeriod.TODAY
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("统计范围：${current.label}", modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RankingPeriod.entries.forEach { period ->
                DropdownMenuItem(text = { Text(period.label) }, onClick = {
                    expanded = false
                    onSelect(period.key)
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsageChoices(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (key, label) ->
            SegmentedButton(selected = selected == key, onClick = { onSelect(key) }, shape = SegmentedButtonDefaults.itemShape(index, options.size)) { Text(label) }
        }
    }
}

internal fun elapsedLabel(value: Double?): String = if (value == null || !value.isFinite() || value < 0) "—" else String.format(Locale.ROOT, "%.2f s", value / 1000)
internal fun rateLabel(value: Double?): String = if (value == null || !value.isFinite()) "—" else String.format(Locale.ROOT, "%.1f tok/s", value)
