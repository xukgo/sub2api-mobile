package com.sub2api.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumericSafetyTest {
    @Test
    fun progressFractionRejectsNonFiniteValues() {
        assertEquals(0f, safeProgressFraction(Double.NaN))
        assertEquals(0f, safeProgressFraction(Double.POSITIVE_INFINITY))
        assertEquals(0f, safeProgressFraction(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun progressFractionClampsServerValues() {
        assertEquals(0f, safeProgressFraction(-50.0))
        assertEquals(0.375f, safeProgressFraction(37.5))
        assertEquals(1f, safeProgressFraction(180.0))
    }

    @Test
    fun summaryCalculatesProgressFromCurrentServerFields() {
        val summary = SubscriptionSummaryItem(
            dailyUsedUsd = 2.5,
            dailyLimitUsd = 10.0,
        )

        assertEquals(25.0, summary.resolvedDailyProgress!!, 0.0001)
    }

    @Test
    fun summaryRejectsInvalidLimit() {
        assertNull(resolvePercentage(null, 2.5, 0.0))
        assertNull(resolvePercentage(null, 2.5, Double.NaN))
    }
}
