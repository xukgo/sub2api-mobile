package com.sub2api.mobile.data

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class ApiEnvelope<T>(
    val code: Int = -1,
    val message: String = "",
    val data: T? = null,
)

data class PublicSettings(
    val siteName: String = "Sub2API",
    val siteSubtitle: String = "",
    val siteLogo: String = "",
    val apiBaseUrl: String = "",
    val docUrl: String = "",
    val contactInfo: String = "",
    val registrationEnabled: Boolean = false,
    val emailVerifyEnabled: Boolean = false,
    val passwordResetEnabled: Boolean = false,
    val paymentEnabled: Boolean = false,
    val version: String = "",
)

data class User(
    val id: Long = 0,
    val username: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val role: String = "user",
    val balance: Double = 0.0,
    val frozenBalance: Double = 0.0,
    val concurrency: Int = 0,
    val rpmLimit: Int = 0,
    val status: String = "active",
    val lastActiveAt: String? = null,
    val createdAt: String = "",
)

data class LoginRequest(val email: String, val password: String)
data class Login2FaRequest(val tempToken: String, val totpCode: String)

data class LoginResult(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Long? = null,
    val tokenType: String? = null,
    val user: User? = null,
    @SerializedName("requires_2fa") val requires2fa: Boolean = false,
    val tempToken: String? = null,
    val userEmailMasked: String? = null,
)

data class RefreshRequest(val refreshToken: String)
data class RefreshResult(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresIn: Long = 0,
    val tokenType: String = "Bearer",
)

data class Group(
    val id: Long = 0,
    val name: String = "",
    val description: String? = null,
    val platform: String = "",
    val rateMultiplier: Double = 1.0,
    val status: String = "active",
    val subscriptionType: String = "standard",
    val dailyLimitUsd: Double? = null,
    val weeklyLimitUsd: Double? = null,
    val monthlyLimitUsd: Double? = null,
)

data class ApiKey(
    val id: Long = 0,
    val userId: Long = 0,
    val key: String = "",
    val name: String = "",
    val groupId: Long? = null,
    val status: String = "active",
    val lastUsedAt: String? = null,
    val lastUsedIp: String? = null,
    val quota: Double = 0.0,
    val quotaUsed: Double = 0.0,
    val expiresAt: String? = null,
    val createdAt: String = "",
    val currentConcurrency: Int = 0,
    val group: Group? = null,
    @SerializedName("rate_limit_5h") val rateLimit5h: Double = 0.0,
    val rateLimit1d: Double = 0.0,
    val rateLimit7d: Double = 0.0,
    @SerializedName("usage_5h") val usage5h: Double = 0.0,
    val usage1d: Double = 0.0,
    val usage7d: Double = 0.0,
)

data class CreateApiKeyRequest(
    val name: String,
    val groupId: Long? = null,
    val quota: Double? = null,
    val expiresInDays: Int? = null,
)

data class UpdateApiKeyRequest(
    val name: String? = null,
    val status: String? = null,
)

data class AiAccount(
    val id: Long = 0,
    val name: String = "",
    val notes: String? = null,
    val platform: String = "",
    val type: String = "",
    val credentials: Map<String, Any?> = emptyMap(),
    val credentialsStatus: Map<String, Boolean> = emptyMap(),
    val proxyId: Long? = null,
    val concurrency: Int = 0,
    val currentConcurrency: Int = 0,
    val priority: Int = 0,
    val rateMultiplier: Double = 1.0,
    val status: String = "active",
    val schedulable: Boolean = true,
    val errorMessage: String? = null,
    val lastUsedAt: String? = null,
    val expiresAt: Long? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val groupIds: List<Long> = emptyList(),
    val groups: List<Group> = emptyList(),
    val quotaLimit: Double? = null,
    val quotaUsed: Double? = null,
    val quotaDailyLimit: Double? = null,
    val quotaDailyUsed: Double? = null,
    val quotaWeeklyLimit: Double? = null,
    val quotaWeeklyUsed: Double? = null,
) {
    fun hasCredential(name: String): Boolean =
        credentialsStatus["has_$name"] == true || credentials[name] != null
}

data class CreateAiAccountRequest(
    val name: String,
    val notes: String? = null,
    val platform: String,
    val type: String,
    val credentials: Map<String, Any?>,
    val concurrency: Int = 1,
    val priority: Int = 0,
    val rateMultiplier: Double = 1.0,
    val groupIds: List<Long> = emptyList(),
)

data class UpdateAiAccountRequest(
    val name: String? = null,
    val notes: String? = null,
    val type: String? = null,
    val credentials: Map<String, Any?>? = null,
    val concurrency: Int? = null,
    val priority: Int? = null,
    val rateMultiplier: Double? = null,
    val status: String? = null,
    val groupIds: List<Long>? = null,
)

data class SetAiAccountSchedulableRequest(val schedulable: Boolean)

data class AiAccountTestResult(
    val success: Boolean = false,
    val message: String = "",
    val latencyMs: Long? = null,
    val model: String? = null,
)

data class AccountModel(val id: String = "", val name: String = "", val displayName: String = "") {
    val modelId: String get() = id.ifBlank { name.removePrefix("models/") }
}

data class TestAiAccountRequest(val modelId: String = "", val prompt: String = "Hi")

data class AiUsageProgress(
    val utilization: Double = 0.0,
    val resetsAt: String? = null,
    val remainingSeconds: Long = 0,
)

data class AntigravityModelQuota(
    val utilization: Double = 0.0,
    val resetTime: String? = null,
)

data class GrokQuotaWindow(
    val limit: Double? = null,
    val remaining: Double? = null,
    val resetAt: String? = null,
)

data class GrokBillingSummary(
    val periodType: String? = null,
    val usagePercent: Double? = null,
    val usedPercent: Double? = null,
    val monthlyLimitCents: Double? = null,
    val usedCents: Double? = null,
    val periodEnd: String? = null,
    val billingPeriodEnd: String? = null,
)

data class AiCredit(
    val creditType: String? = null,
    val amount: Double = 0.0,
    val minimumBalance: Double = 0.0,
)

data class AiAccountUsage(
    val source: String? = null,
    val updatedAt: String? = null,
    val fiveHour: AiUsageProgress? = null,
    val sevenDay: AiUsageProgress? = null,
    val sevenDaySonnet: AiUsageProgress? = null,
    val sevenDayFable: AiUsageProgress? = null,
    val geminiSharedDaily: AiUsageProgress? = null,
    val geminiProDaily: AiUsageProgress? = null,
    val geminiFlashDaily: AiUsageProgress? = null,
    val geminiSharedMinute: AiUsageProgress? = null,
    val geminiProMinute: AiUsageProgress? = null,
    val geminiFlashMinute: AiUsageProgress? = null,
    val antigravityQuota: Map<String, AntigravityModelQuota> = emptyMap(),
    val grokRequestQuota: GrokQuotaWindow? = null,
    val grokTokenQuota: GrokQuotaWindow? = null,
    val grokBilling: GrokBillingSummary? = null,
    val aiCredits: List<AiCredit> = emptyList(),
    val error: String? = null,
)

data class AiQuotaMetric(
    val label: String,
    val remainingPercent: Double? = null,
    val remainingAmount: Double? = null,
    val unit: String? = null,
)

data class AiAccountQuota(
    val accountId: Long,
    val metrics: List<AiQuotaMetric> = emptyList(),
    val error: String? = null,
) {
    val remainingPercent: Double?
        get() = metrics.mapNotNull { it.remainingPercent }.minOrNull()
}

data class AiQuotaSummary(
    val totalRemainingPercent: Double = 0.0,
    val totalCapacityPercent: Double = 0.0,
    val measuredAccounts: Int = 0,
    val totalAccounts: Int = 0,
) {
    val capacityRemainingPercent: Double
        get() = if (totalCapacityPercent > 0.0) {
            (totalRemainingPercent / totalCapacityPercent * 100.0).coerceIn(0.0, 100.0)
        } else {
            0.0
        }
}

data class CookieAuthRequest(
    val code: String,
    val proxyId: Long? = null,
)

data class Paginated<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
    val pages: Int = 1,
)

data class ChannelMonitorListResponse(
    val items: List<ChannelMonitorView> = emptyList(),
)

data class ChannelMonitorView(
    val id: Long = 0,
    val name: String = "",
    val provider: String = "",
    val groupName: String = "",
    val primaryModel: String = "",
    val primaryStatus: String = "",
    val primaryLatencyMs: Int? = null,
    val primaryPingLatencyMs: Int? = null,
    @SerializedName("availability_7d") val availability7d: Double = 0.0,
    val extraModels: List<ChannelMonitorExtraModel> = emptyList(),
    val timeline: List<ChannelMonitorTimelinePoint> = emptyList(),
)

data class ChannelMonitorExtraModel(
    val model: String = "",
    val status: String = "",
    val latencyMs: Int? = null,
)

data class ChannelMonitorTimelinePoint(
    val status: String = "",
    val latencyMs: Int? = null,
    val pingLatencyMs: Int? = null,
    val checkedAt: String = "",
)

data class ChannelMonitorDetail(
    val id: Long = 0,
    val name: String = "",
    val provider: String = "",
    val groupName: String = "",
    val models: List<ChannelMonitorModelDetail> = emptyList(),
)

data class ChannelMonitorModelDetail(
    val model: String = "",
    val latestStatus: String = "",
    val latestLatencyMs: Int? = null,
    @SerializedName("availability_7d") val availability7d: Double = 0.0,
    @SerializedName("availability_15d") val availability15d: Double = 0.0,
    @SerializedName("availability_30d") val availability30d: Double = 0.0,
    @SerializedName("avg_latency_7d_ms") val avgLatency7dMs: Int? = null,
)

data class DashboardStats(
    val totalApiKeys: Int = 0,
    val activeApiKeys: Int = 0,
    val totalRequests: Long = 0,
    val totalTokens: Long = 0,
    val totalActualCost: Double = 0.0,
    val todayRequests: Long = 0,
    val todayTokens: Long = 0,
    val todayActualCost: Double = 0.0,
    val averageDurationMs: Double = 0.0,
    val rpm: Double = 0.0,
    val tpm: Double = 0.0,
)

data class UsageStats(
    val period: String? = null,
    val totalRequests: Long = 0,
    val totalInputTokens: Long = 0,
    val totalOutputTokens: Long = 0,
    val totalCacheTokens: Long = 0,
    val totalTokens: Long = 0,
    val totalCost: Double = 0.0,
    val totalActualCost: Double = 0.0,
    val averageDurationMs: Double = 0.0,
)

data class UsageLog(
    val id: Long = 0,
    val userId: Long = 0,
    val accountId: Long? = null,
    val channelId: Long? = null,
    val user: User? = null,
    val account: UsageAccountSummary? = null,
    val upstreamModel: String? = null,
    val upstreamResponseModel: String? = null,
    val imageCount: Int = 0,
    val imageOutputTokens: Long = 0,
    val apiKeyId: Long = 0,
    val requestId: String = "",
    val model: String = "",
    val inboundEndpoint: String? = null,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheCreationTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val totalCost: Double = 0.0,
    val actualCost: Double = 0.0,
    val requestType: String? = null,
    val stream: Boolean = false,
    val durationMs: Long? = null,
    val firstTokenMs: Long? = null,
    val billingMode: String? = null,
    val createdAt: String = "",
    val apiKey: ApiKey? = null,
    val group: Group? = null,
) {
    val totalTokens: Long get() = inputTokens + outputTokens + cacheCreationTokens + cacheReadTokens
}

data class UserSubscription(
    val id: Long = 0,
    val groupId: Long = 0,
    val status: String = "",
    val startsAt: String = "",
    val dailyUsageUsd: Double = 0.0,
    val weeklyUsageUsd: Double = 0.0,
    val monthlyUsageUsd: Double = 0.0,
    val expiresAt: String? = null,
    val group: Group? = null,
)

data class ProgressWindow(
    @SerializedName(value = "used", alternate = ["used_usd"])
    val used: Double = 0.0,
    @SerializedName(value = "limit", alternate = ["limit_usd"])
    val limit: Double? = null,
    val percentage: Double = 0.0,
    @SerializedName(value = "reset_in_seconds", alternate = ["resets_in_seconds"])
    val resetInSeconds: Long? = null,
)

data class SubscriptionProgress(
    @SerializedName(value = "subscription_id", alternate = ["id"])
    val subscriptionId: Long = 0,
    val groupName: String = "",
    val daily: ProgressWindow? = null,
    val weekly: ProgressWindow? = null,
    val monthly: ProgressWindow? = null,
    val expiresAt: String? = null,
    @SerializedName(value = "days_remaining", alternate = ["expires_in_days"])
    val daysRemaining: Int? = null,
)

data class SubscriptionSummary(
    val activeCount: Int = 0,
    val subscriptions: List<SubscriptionSummaryItem> = emptyList(),
)

data class SubscriptionSummaryItem(
    val id: Long = 0,
    val groupName: String = "",
    val status: String = "",
    val dailyProgress: Double? = null,
    val weeklyProgress: Double? = null,
    val monthlyProgress: Double? = null,
    val dailyUsedUsd: Double? = null,
    val dailyLimitUsd: Double? = null,
    val weeklyUsedUsd: Double? = null,
    val weeklyLimitUsd: Double? = null,
    val monthlyUsedUsd: Double? = null,
    val monthlyLimitUsd: Double? = null,
    val expiresAt: String? = null,
    val daysRemaining: Int? = null,
) {
    val resolvedDailyProgress: Double?
        get() = resolvePercentage(dailyProgress, dailyUsedUsd, dailyLimitUsd)
}

data class RedeemRequest(val code: String)
data class RedeemResult(
    val message: String = "",
    val type: String = "",
    val value: Double = 0.0,
    val newBalance: Double? = null,
    val newConcurrency: Int? = null,
)

data class RedeemHistory(
    val id: Long = 0,
    val code: String = "",
    val type: String = "",
    val value: Double = 0.0,
    val status: String = "",
    val usedAt: String = "",
    val notes: String? = null,
    val group: Group? = null,
    val validityDays: Int? = null,
)

data class UpdateProfileRequest(
    val username: String? = null,
    val avatarUrl: String? = null,
)

typealias EmptyResult = JsonElement
