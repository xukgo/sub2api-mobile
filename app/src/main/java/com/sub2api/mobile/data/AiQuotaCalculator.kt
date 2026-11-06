package com.sub2api.mobile.data

import kotlin.math.max

internal object AiQuotaCalculator {
    fun requiresUsage(account: AiAccount): Boolean = when (account.platform) {
        "anthropic" -> account.type == "oauth" || account.type == "setup-token"
        "openai" -> account.type == "oauth"
        "gemini" -> true
        "antigravity", "grok" -> account.type == "oauth"
        else -> false
    }

    fun calculate(
        account: AiAccount,
        usage: AiAccountUsage? = null,
        fetchError: String? = null,
    ): AiAccountQuota {
        val metrics = mutableListOf<AiQuotaMetric>()
        addConfiguredQuotaMetrics(account, metrics)

        when (account.platform) {
            "anthropic", "openai" -> {
                metrics.addProgress("5h", usage?.fiveHour)
                metrics.addProgress("7d", usage?.sevenDay)
                metrics.addProgress("7d Sonnet", usage?.sevenDaySonnet)
                metrics.addProgress("7d Fable", usage?.sevenDayFable)
            }
            "gemini" -> {
                metrics.addProgress("1d", usage?.geminiSharedDaily)
                metrics.addProgress("Pro", usage?.geminiProDaily)
                metrics.addProgress("Flash", usage?.geminiFlashDaily)
                if (metrics.none { it.label in setOf("1d", "Pro", "Flash") }) {
                    metrics.addProgress("RPM", usage?.geminiSharedMinute)
                    metrics.addProgress("Pro RPM", usage?.geminiProMinute)
                    metrics.addProgress("Flash RPM", usage?.geminiFlashMinute)
                }
            }
            "antigravity" -> {
                usage?.antigravityQuota.orEmpty().forEach { (model, quota) ->
                    metrics += AiQuotaMetric(model, remainingFromUtilization(quota.utilization))
                }
            }
            "grok" -> addGrokMetrics(usage, metrics)
        }

        val credits = usage?.aiCredits.orEmpty().sumOf { max(0.0, it.amount) }
        if (credits > 0.0) {
            metrics += AiQuotaMetric("Credits", remainingAmount = credits, unit = "credits")
        }

        return AiAccountQuota(
            accountId = account.id,
            metrics = metrics,
            error = usage?.error?.takeIf { it.isNotBlank() } ?: fetchError,
        )
    }

    fun summarize(accounts: List<AiAccount>, quotas: Map<Long, AiAccountQuota>): AiQuotaSummary {
        val uniqueAccounts = accounts.distinctBy { it.id }
        val percentages = uniqueAccounts.mapNotNull { quotas[it.id]?.remainingPercent }
        return AiQuotaSummary(
            totalRemainingPercent = percentages.sum(),
            totalCapacityPercent = percentages.size * 100.0,
            measuredAccounts = percentages.size,
            totalAccounts = uniqueAccounts.size,
        )
    }

    private fun addConfiguredQuotaMetrics(account: AiAccount, metrics: MutableList<AiQuotaMetric>) {
        metrics.addLimit("总额", account.quotaUsed, account.quotaLimit)
        metrics.addLimit("1d", account.quotaDailyUsed, account.quotaDailyLimit)
        metrics.addLimit("7d", account.quotaWeeklyUsed, account.quotaWeeklyLimit)
    }

    private fun addGrokMetrics(usage: AiAccountUsage?, metrics: MutableList<AiQuotaMetric>) {
        val billing = usage?.grokBilling
        val billingUsed = billing?.usagePercent
            ?: billing?.usedPercent
            ?: billing?.monthlyLimitCents?.takeIf { it > 0.0 }?.let { limit ->
                (billing.usedCents ?: 0.0) / limit * 100.0
            }
        if (billingUsed != null) {
            val label = if (billing?.periodType.equals("weekly", ignoreCase = true)) "7d" else "账期"
            metrics += AiQuotaMetric(label, remainingFromUtilization(billingUsed))
            return
        }
        metrics.addGrokWindow("请求", usage?.grokRequestQuota, "次")
        metrics.addGrokWindow("Token", usage?.grokTokenQuota, "tokens")
    }

    private fun MutableList<AiQuotaMetric>.addProgress(label: String, progress: AiUsageProgress?) {
        if (progress != null) add(AiQuotaMetric(label, remainingFromUtilization(progress.utilization)))
    }

    private fun MutableList<AiQuotaMetric>.addLimit(label: String, used: Double?, limit: Double?) {
        if (limit == null || !limit.isFinite() || limit <= 0.0) return
        val safeUsed = used?.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val amount = (limit - safeUsed).coerceIn(0.0, limit)
        add(AiQuotaMetric(label, amount / limit * 100.0, amount, "USD"))
    }

    private fun MutableList<AiQuotaMetric>.addGrokWindow(label: String, quota: GrokQuotaWindow?, unit: String) {
        val limit = quota?.limit ?: return
        val remaining = quota.remaining ?: return
        if (!limit.isFinite() || !remaining.isFinite() || limit <= 0.0) return
        val safeRemaining = remaining.coerceIn(0.0, limit)
        add(AiQuotaMetric(label, safeRemaining / limit * 100.0, safeRemaining, unit))
    }

    private fun remainingFromUtilization(utilization: Double): Double =
        if (utilization.isFinite()) (100.0 - utilization).coerceIn(0.0, 100.0) else 0.0
}

internal fun summarizeAiQuotas(
    accounts: List<AiAccount>,
    quotas: Map<Long, AiAccountQuota>,
): AiQuotaSummary = AiQuotaCalculator.summarize(accounts, quotas)
