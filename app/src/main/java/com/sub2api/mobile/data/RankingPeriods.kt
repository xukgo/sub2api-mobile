package com.sub2api.mobile.data

import java.time.Instant
import java.time.ZoneId

enum class RankingPeriod(val key: String, val label: String) {
    TODAY("today", "今天"),
    YESTERDAY("yesterday", "昨天"),
    LAST_24_HOURS("24h", "近24小时"),
    LAST_7_DAYS("7d", "近7天"),
    LAST_14_DAYS("14d", "近14天"),
    LAST_30_DAYS("30d", "近30天"),
    THIS_MONTH("this_month", "本月"),
    LAST_MONTH("last_month", "上月"),
}

// Match the official DateRangePicker's inclusive calendar-date parameters.
fun rankingDateRange(period: String, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): UsageDateRange {
    val selected = RankingPeriod.entries.firstOrNull { it.key == period }
        ?: throw IllegalArgumentException("不支持的排行时间范围")
    val today = now.atZone(zone).toLocalDate()
    val monthStart = today.withDayOfMonth(1)
    val (start, end) = when (selected) {
        RankingPeriod.TODAY -> today to today
        RankingPeriod.YESTERDAY -> today.minusDays(1).let { it to it }
        RankingPeriod.LAST_24_HOURS -> now.minusSeconds(86_400).atZone(zone).toLocalDate() to today
        RankingPeriod.LAST_7_DAYS -> today.minusDays(6) to today
        RankingPeriod.LAST_14_DAYS -> today.minusDays(13) to today
        RankingPeriod.LAST_30_DAYS -> today.minusDays(29) to today
        RankingPeriod.THIS_MONTH -> monthStart to today
        RankingPeriod.LAST_MONTH -> monthStart.minusMonths(1) to monthStart.minusDays(1)
    }
    return UsageDateRange(start.toString(), end.toString())
}
