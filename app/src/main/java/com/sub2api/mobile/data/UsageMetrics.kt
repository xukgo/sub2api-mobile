package com.sub2api.mobile.data

import java.time.Instant
import java.time.ZoneId

data class UsageAccountSummary(val id: Long = 0, val name: String = "")
data class UsageFilters(val userId: Long? = null, val accountId: Long? = null, val model: String? = null)
data class UsageDateRange(val start: String, val end: String)
data class UsagePage(val stats: UsageStats, val records: Paginated<UsageLog>)

fun usageDateRange(period: String, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): UsageDateRange {
    val today = now.atZone(zone).toLocalDate()
    val start = when (period) { "all" -> java.time.LocalDate.of(1970, 1, 1); "week" -> today.minusDays(6); "month" -> today.minusDays(29); else -> today }
    return UsageDateRange(start.toString(), today.toString())
}

// v0.2.19's web UI uses output tokens / full request duration, including TTFT.
val UsageLog.outputRate: Double?
    get() {
        val elapsed = durationMs ?: return null
        if (elapsed <= 0 || outputTokens <= 0 || imageCount > 0 || imageOutputTokens > 0 || billingMode == "image" ||
            (requestType != null && requestType !in setOf("sync", "stream", "ws_v2", "cyber"))) return null
        return (outputTokens.toDouble() * 1000 / elapsed).takeIf { it.isFinite() }
    }

val UsageLog.accountLabel: String
    get() = account?.name?.takeIf { it.isNotBlank() } ?: (account?.id ?: accountId)?.takeIf { it > 0 }?.let { "账号 #$it" } ?: "后端未提供"
val UsageLog.channelLabel: String
    get() = channelId?.takeIf { it > 0 }?.let { "渠道 #$it" } ?: "后端未提供"

data class ChannelPerformance(
    val account: String,
    val channel: String,
    val requests: Int,
    val firstTokenMs: Double?,
    val durationMs: Double?,
    val outputRate: Double?,
)

fun channelPerformance(logs: List<UsageLog>): List<ChannelPerformance> = logs
    .groupBy { (it.account?.id ?: it.accountId) to it.channelId }
    .values.map { rows ->
        val firstTokens = rows.mapNotNull { it.firstTokenMs?.takeIf { v -> v >= 0 }?.toDouble() }
        val durations = rows.mapNotNull { it.durationMs?.takeIf { v -> v >= 0 }?.toDouble() }
        val textRows = rows.filter { it.outputRate != null }
        val totalDuration = textRows.sumOf { it.durationMs!!.toDouble() }
        ChannelPerformance(
            rows.first().accountLabel, rows.first().channelLabel, rows.size,
            firstTokens.takeIf { it.isNotEmpty() }?.average(), durations.takeIf { it.isNotEmpty() }?.average(),
            if (totalDuration > 0) textRows.sumOf { it.outputTokens.toDouble() } * 1000 / totalDuration else null,
        )
    }.sortedByDescending { it.requests }

data class UserUsageRank(
    val userId: Long = 0,
    val email: String = "",
    val username: String = "",
    val requests: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheTokens: Long = 0,
    val totalTokens: Long = 0,
    val actualCost: Double = 0.0,
)
data class UserUsageRankingResponse(
    val users: List<UserUsageRank> = emptyList(),
    val startDate: String? = null,
    val endDate: String? = null,
)
data class UsageRanking(val rows: List<UserUsageRank>, val dates: UsageDateRange, val timezone: String)

fun sortedUserRanking(rows: List<UserUsageRank>, metric: String): List<UserUsageRank> = when (metric) {
    "requests" -> rows.sortedWith(compareByDescending<UserUsageRank> { it.requests }.thenBy { it.userId })
    "actual_cost" -> rows.sortedWith(compareByDescending<UserUsageRank> { it.actualCost }.thenBy { it.userId })
    else -> rows.sortedWith(compareByDescending<UserUsageRank> { it.totalTokens }.thenBy { it.userId })
}
