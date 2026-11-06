package com.sub2api.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiQuotaCalculatorTest {
    @Test
    fun anthropicUsesMostConstrainedWindowAsAccountRemaining() {
        val account = AiAccount(id = 1, platform = "anthropic", type = "oauth")
        val usage = AiAccountUsage(
            fiveHour = AiUsageProgress(utilization = 25.0),
            sevenDay = AiUsageProgress(utilization = 70.0),
            sevenDaySonnet = AiUsageProgress(utilization = 40.0),
        )

        val quota = AiQuotaCalculator.calculate(account, usage)

        assertEquals(30.0, quota.remainingPercent!!, 0.0001)
        assertEquals(listOf("5h", "7d", "7d Sonnet"), quota.metrics.map { it.label })
    }

    @Test
    fun apiKeyQuotaKeepsDollarAmountsAndUsesTightestLimit() {
        val account = AiAccount(
            id = 2,
            platform = "openai",
            type = "apikey",
            quotaLimit = 100.0,
            quotaUsed = 20.0,
            quotaDailyLimit = 10.0,
            quotaDailyUsed = 8.0,
        )

        val quota = AiQuotaCalculator.calculate(account)

        assertEquals(20.0, quota.remainingPercent!!, 0.0001)
        assertEquals(80.0, quota.metrics.first { it.label == "总额" }.remainingAmount!!, 0.0001)
        assertEquals(2.0, quota.metrics.first { it.label == "1d" }.remainingAmount!!, 0.0001)
        assertEquals("USD", quota.metrics.first().unit)
    }

    @Test
    fun grokRemainingWindowsAreNormalizedWithoutLosingAmounts() {
        val account = AiAccount(id = 3, platform = "grok", type = "oauth")
        val usage = AiAccountUsage(
            grokRequestQuota = GrokQuotaWindow(limit = 100.0, remaining = 55.0),
            grokTokenQuota = GrokQuotaWindow(limit = 1_000_000.0, remaining = 250_000.0),
        )

        val quota = AiQuotaCalculator.calculate(account, usage)

        assertEquals(25.0, quota.remainingPercent!!, 0.0001)
        assertEquals(55.0, quota.metrics.first { it.label == "请求" }.remainingAmount!!, 0.0001)
        assertEquals(250_000.0, quota.metrics.first { it.label == "Token" }.remainingAmount!!, 0.0001)
    }

    @Test
    fun antigravityShowsModelCapacityAndCredits() {
        val account = AiAccount(id = 4, platform = "antigravity", type = "oauth")
        val usage = AiAccountUsage(
            antigravityQuota = mapOf(
                "gemini-pro" to AntigravityModelQuota(utilization = 90.0),
                "claude-sonnet" to AntigravityModelQuota(utilization = 35.0),
            ),
            aiCredits = listOf(AiCredit(amount = 12.5), AiCredit(amount = 7.5)),
        )

        val quota = AiQuotaCalculator.calculate(account, usage)

        assertEquals(10.0, quota.remainingPercent!!, 0.0001)
        assertEquals(20.0, quota.metrics.first { it.unit == "credits" }.remainingAmount!!, 0.0001)
    }

    @Test
    fun summarySumsEveryMeasuredAccountIncludingInactiveAccounts() {
        val accounts = listOf(
            AiAccount(id = 10, status = "active"),
            AiAccount(id = 11, status = "active"),
            AiAccount(id = 12, status = "inactive"),
            AiAccount(id = 13, status = "active"),
        )
        val quotas = mapOf(
            10L to AiAccountQuota(10, listOf(AiQuotaMetric("5h", 25.0))),
            11L to AiAccountQuota(11, listOf(AiQuotaMetric("7d", 75.0))),
            12L to AiAccountQuota(12, listOf(AiQuotaMetric("5h", 100.0))),
            13L to AiAccountQuota(13, listOf(AiQuotaMetric("Credits", remainingAmount = 9.0, unit = "credits"))),
        )

        val summary = AiQuotaCalculator.summarize(accounts, quotas)

        assertEquals(200.0, summary.totalRemainingPercent, 0.0001)
        assertEquals(300.0, summary.totalCapacityPercent, 0.0001)
        assertEquals(200.0 / 3.0, summary.capacityRemainingPercent, 0.0001)
        assertEquals(3, summary.measuredAccounts)
        assertEquals(4, summary.totalAccounts)
        assertNull(quotas.getValue(13).remainingPercent)
    }

    @Test
    fun totalChangesWhenAccountsAreAddedOrRemoved() {
        val first = AiAccount(id = 20)
        val second = AiAccount(id = 21)
        val added = AiAccount(id = 22)
        val quotas = mapOf(
            20L to AiAccountQuota(20, listOf(AiQuotaMetric("7d", 1.0))),
            21L to AiAccountQuota(21, listOf(AiQuotaMetric("7d", 1.0))),
            22L to AiAccountQuota(22, listOf(AiQuotaMetric("7d", 50.0))),
        )

        val initial = AiQuotaCalculator.summarize(listOf(first, second), quotas)
        val afterAdd = AiQuotaCalculator.summarize(listOf(first, second, added), quotas)
        val afterDelete = AiQuotaCalculator.summarize(listOf(second, added), quotas)

        assertEquals(2.0, initial.totalRemainingPercent, 0.0001)
        assertEquals(200.0, initial.totalCapacityPercent, 0.0001)
        assertEquals(1.0, initial.capacityRemainingPercent, 0.0001)
        assertEquals(52.0, afterAdd.totalRemainingPercent, 0.0001)
        assertEquals(300.0, afterAdd.totalCapacityPercent, 0.0001)
        assertEquals(52.0 / 3.0, afterAdd.capacityRemainingPercent, 0.0001)
        assertEquals(51.0, afterDelete.totalRemainingPercent, 0.0001)
        assertEquals(200.0, afterDelete.totalCapacityPercent, 0.0001)
        assertEquals(25.5, afterDelete.capacityRemainingPercent, 0.0001)
    }

    @Test
    fun duplicateAccountRowsAreOnlyCountedOnce() {
        val account = AiAccount(id = 30)
        val quotas = mapOf(
            30L to AiAccountQuota(30, listOf(AiQuotaMetric("7d", 80.0))),
        )

        val summary = AiQuotaCalculator.summarize(listOf(account, account), quotas)

        assertEquals(80.0, summary.totalRemainingPercent, 0.0001)
        assertEquals(100.0, summary.totalCapacityPercent, 0.0001)
        assertEquals(1, summary.measuredAccounts)
        assertEquals(1, summary.totalAccounts)
    }
}
