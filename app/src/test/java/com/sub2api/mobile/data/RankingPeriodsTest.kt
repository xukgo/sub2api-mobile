package com.sub2api.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RankingPeriodsTest {
    @Test
    fun allEightPresetsMatchTheOfficialWebCalendarRanges() {
        val now = Instant.parse("2026-11-06T15:00:00Z")
        val zone = ZoneId.of("Asia/Shanghai")
        val expected = mapOf(
            "today" to UsageDateRange("2026-11-06", "2026-11-06"),
            "yesterday" to UsageDateRange("2026-11-05", "2026-11-05"),
            "24h" to UsageDateRange("2026-11-05", "2026-11-06"),
            "7d" to UsageDateRange("2026-10-31", "2026-11-06"),
            "14d" to UsageDateRange("2026-10-24", "2026-11-06"),
            "30d" to UsageDateRange("2026-10-08", "2026-11-06"),
            "this_month" to UsageDateRange("2026-11-01", "2026-11-06"),
            "last_month" to UsageDateRange("2026-10-01", "2026-10-31"),
        )
        assertEquals(RankingPeriod.entries.map { it.key }.toSet(), expected.keys)
        expected.forEach { (period, range) -> assertEquals(period, range, rankingDateRange(period, zone, now)) }
        assertThrows(IllegalArgumentException::class.java) { rankingDateRange("unknown", zone, now) }
    }

    @Test
    fun previousMonthHandlesLeapYearsAndYearBoundaries() {
        val zone = ZoneId.of("UTC")
        assertEquals(UsageDateRange("2024-02-01", "2024-02-29"), rankingDateRange("last_month", zone, Instant.parse("2024-03-01T12:00:00Z")))
        assertEquals(UsageDateRange("2025-12-01", "2025-12-31"), rankingDateRange("last_month", zone, Instant.parse("2026-01-01T12:00:00Z")))
    }

    @Test
    fun web24HourPresetRetainsTheOfficialDateTruncationAcrossDaylightSavingTime() {
        val zone = ZoneId.of("America/New_York")
        val now = Instant.parse("2026-03-09T04:30:00Z")
        assertEquals(UsageDateRange("2026-03-07", "2026-03-09"), rankingDateRange("24h", zone, now))
        assertEquals(UsageDateRange("2026-03-09", "2026-03-09"), rankingDateRange("today", zone, now))
    }
}
