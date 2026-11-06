package com.sub2api.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaWidgetFormatterTest {
    @Test
    fun readyStateDisplaysTheSumInsteadOfAnAverage() {
        val state = QuotaWidgetState(
            status = QuotaWidgetStatus.READY,
            totalRemainingPercent = 147.0,
            totalCapacityPercent = 200.0,
            measuredAccounts = 2,
            totalAccounts = 2,
            updatedAtMillis = 1L,
        )

        val presentation = QuotaWidgetFormatter.present(state, timeLabel = "15:42")

        assertEquals("147%", presentation.value)
        assertEquals("满额 200% · 2/2 · 15:42", presentation.detail)
        assertEquals(735, presentation.progress)
    }

    @Test
    fun partialResultStatesHowManyAccountsWereIncluded() {
        val state = QuotaWidgetState(
            status = QuotaWidgetStatus.READY,
            totalRemainingPercent = 71.5,
            totalCapacityPercent = 100.0,
            measuredAccounts = 1,
            totalAccounts = 3,
            updatedAtMillis = 1L,
        )

        val presentation = QuotaWidgetFormatter.present(state, timeLabel = "15:42")

        assertEquals("71.5%", presentation.value)
        assertTrue(presentation.detail.contains("已合计 1/3"))
        assertTrue(presentation.detail.contains("满额 100%"))
    }

    @Test
    fun refreshFailureKeepsTheLastSuccessfulTotal() {
        val state = QuotaWidgetState(
            status = QuotaWidgetStatus.ERROR,
            totalRemainingPercent = 147.0,
            totalCapacityPercent = 200.0,
            measuredAccounts = 2,
            totalAccounts = 2,
            updatedAtMillis = 1L,
        )

        val presentation = QuotaWidgetFormatter.present(state, timeLabel = "15:42")

        assertEquals("147%", presentation.value)
        assertEquals("刷新失败 · 上次 15:42 · 2/2", presentation.detail)
    }
}
