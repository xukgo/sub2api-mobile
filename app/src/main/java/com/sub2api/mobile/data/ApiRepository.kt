package com.sub2api.mobile.data

import com.google.gson.Gson
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.util.TimeZone

class ApiException(message: String, val status: Int = 0, val code: Int? = null) : Exception(message)

class ApiRepository(
    private val sessionStore: SessionStore,
    factory: ApiClientFactory,
    val session: SessionState = sessionStore.state.value,
) {
    private val service by lazy { factory.service(session) }
    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    suspend fun testServer(): PublicSettings = unwrap(service.publicSettings())

    suspend fun login(email: String, password: String): LoginResult {
        val result = unwrap(service.login(LoginRequest(email.trim(), password)))
        persistLogin(result)
        return result
    }

    suspend fun login2Fa(tempToken: String, code: String): LoginResult {
        val result = unwrap(service.login2Fa(Login2FaRequest(tempToken, code.trim())))
        persistLogin(result)
        return result
    }

    suspend fun logout() {
        val refresh = sessionStore.sessionFor(session)?.refreshToken.orEmpty()
        try {
            if (refresh.isNotBlank()) service.logout(RefreshRequest(refresh))
        } finally {
            clearSession()
        }
    }

    fun clearSession() = sessionStore.clearTokens(session)

    suspend fun currentUser(): User = unwrap(service.currentUser()).also {
        sessionStore.setAccount(session, it.email)
    }
    suspend fun dashboardStats(): DashboardStats = unwrap(service.dashboardStats())
    suspend fun subscriptionSummary(): SubscriptionSummary = unwrap(service.subscriptionSummary())
    suspend fun keys(): List<ApiKey> = unwrap(service.keys()).items
    suspend fun groups(): List<Group> = unwrap(service.availableGroups())

    suspend fun createKey(name: String, groupId: Long?, quota: Double?, expiresInDays: Int?): ApiKey =
        unwrap(service.createKey(CreateApiKeyRequest(name.trim(), groupId, quota, expiresInDays)))

    suspend fun setKeyStatus(key: ApiKey, active: Boolean): ApiKey =
        unwrap(service.updateKey(key.id, UpdateApiKeyRequest(status = if (active) "active" else "inactive")))

    suspend fun deleteKey(id: Long) {
        unwrapNullable(service.deleteKey(id))
    }

    suspend fun usage(period: String, page: Int = 1, admin: Boolean = false, filters: UsageFilters = UsageFilters()): UsagePage {
        val timezone = TimeZone.getDefault().id
        val dates = usageDateRange(period)
        val stats = if (admin) unwrap(service.adminUsageStats(timezone, dates.start, dates.end, filters.userId, filters.accountId, filters.model))
            else unwrap(service.usageStats(period, timezone, dates.start, dates.end))
        val logs = if (admin) unwrap(service.adminUsageLogs(page, pageSize = 50, timezone = timezone, startDate = dates.start, endDate = dates.end,
            userId = filters.userId, accountId = filters.accountId, model = filters.model))
            else unwrap(service.usageLogs(page = page, pageSize = 50, timezone = timezone, startDate = dates.start, endDate = dates.end))
        return UsagePage(stats, logs)
    }

    suspend fun userRanking(period: String, metric: String, onProgress: (Int) -> Unit = {}): UsageRanking {
        val window = RankingWindow.create(period)
        val dates = window.dates
        if (period == "today") {
            val result = unwrap(service.userUsageRanking(dates.start, dates.end, window.zone.id, metric))
            return UsageRanking(result.users, window.start, window.end)
        }
        // v0.2.19 accepts calendar dates only. Filter all pages against a fixed, exact 24-hour window.
        val accumulator = UsageRankingAccumulator(window)
        var page = 1
        var scanned = 0
        do {
            currentCoroutineContext().ensureActive()
            val result = unwrap(service.adminUsageLogs(page, pageSize = 1000, sortOrder = "asc",
                timezone = window.zone.id, startDate = dates.start, endDate = dates.end))
            if (result.items.isEmpty() && page < result.pages) throw ApiException("分页数据不完整，请重新刷新排行")
            withContext(Dispatchers.Default) { result.items.forEach(accumulator::add) }
            scanned += result.items.size
            currentCoroutineContext().ensureActive()
            onProgress(scanned)
            if (page >= result.pages) break
            page++
        } while (true)
        return accumulator.result(scanned)
    }

    suspend fun subscriptions(): Pair<List<UserSubscription>, List<SubscriptionProgress>> =
        unwrap(service.subscriptions()) to
            SubscriptionProgressParser.parse(unwrap(service.subscriptionProgress()), gson)

    suspend fun channelMonitors(): List<ChannelMonitorView> =
        unwrap(service.channelMonitors()).items.distinctBy { it.id }

    suspend fun channelMonitorStatus(id: Long): ChannelMonitorDetail =
        unwrap(service.channelMonitorStatus(id))

    suspend fun redeem(code: String): RedeemResult = unwrap(service.redeem(RedeemRequest(code.trim())))
    suspend fun redeemHistory(): List<RedeemHistory> = unwrap(service.redeemHistory())
    suspend fun updateUsername(username: String): User =
        unwrap(service.updateProfile(UpdateProfileRequest(username = username.trim())))

    suspend fun aiAccounts(
        platform: String? = null,
        status: String? = null,
        search: String? = null,
    ): List<AiAccount> {
        val accounts = mutableListOf<AiAccount>()
        var page = 1
        var totalPages: Int
        do {
            val result = unwrap(
                service.aiAccounts(
                    page = page,
                    platform = platform?.takeIf { it.isNotBlank() },
                    status = status?.takeIf { it.isNotBlank() },
                    search = search?.trim()?.takeIf { it.isNotBlank() },
                ),
            )
            accounts += result.items
            totalPages = result.pages
            page++
        } while (page <= totalPages)
        return accounts.distinctBy { it.id }
    }

    suspend fun adminGroups(): List<Group> = unwrap(service.adminGroups())
    suspend fun createAiAccount(request: CreateAiAccountRequest): AiAccount =
        unwrap(service.createAiAccount(request))

    suspend fun updateAiAccount(id: Long, request: UpdateAiAccountRequest): AiAccount =
        unwrap(service.updateAiAccount(id, request))

    suspend fun deleteAiAccount(id: Long) {
        unwrapNullable(service.deleteAiAccount(id))
    }

    suspend fun accountModels(id: Long): List<AccountModel> = unwrap(service.accountModels(id)).filter { it.modelId.isNotBlank() }.distinctBy { it.modelId }

    suspend fun testAiAccount(id: Long, modelId: String = ""): AiAccountTestResult {
        val started = System.nanoTime()
        val response = service.testAiAccount(id, TestAiAccountRequest(modelId.trim()))
        if (!response.isSuccessful) throw parseHttpError(response)
        val body = response.body() ?: throw ApiException("服务器返回了空响应", response.code())
        val raw = withContext(Dispatchers.IO) { body.use { it.string() } }
        return AccountTestSseParser.parse(raw).copy(latencyMs = (System.nanoTime() - started) / 1_000_000)
    }

    suspend fun setAiAccountSchedulable(id: Long, schedulable: Boolean): AiAccount =
        unwrap(service.setAiAccountSchedulable(id, SetAiAccountSchedulableRequest(schedulable)))

    suspend fun clearAiAccountError(id: Long): AiAccount = unwrap(service.clearAiAccountError(id))

    suspend fun aiAccountQuota(account: AiAccount, force: Boolean = false): AiAccountQuota {
        if (!AiQuotaCalculator.requiresUsage(account)) {
            return AiQuotaCalculator.calculate(account)
        }
        return try {
            val source = "passive".takeIf {
                !force && account.platform == "anthropic" && (account.type == "oauth" || account.type == "setup-token")
            }
            val usage = unwrap(
                service.aiAccountUsage(
                    id = account.id,
                    source = source,
                    force = true.takeIf { force },
                ),
            )
            AiQuotaCalculator.calculate(account, usage)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AiQuotaCalculator.calculate(account, fetchError = error.message ?: "额度读取失败")
        }
    }

    suspend fun exchangeAnthropicCookie(sessionKey: String, setupToken: Boolean): Map<String, Any?> {
        val request = CookieAuthRequest(sessionKey.trim())
        return if (setupToken) {
            unwrap(service.exchangeSetupTokenCookie(request))
        } else {
            unwrap(service.exchangeCookie(request))
        }
    }

    private fun persistLogin(result: LoginResult) {
        val token = result.accessToken
        if (!result.requires2fa && !token.isNullOrBlank()) {
            if (!sessionStore.setTokens(session, token, result.refreshToken, result.expiresIn, email = result.user?.email)) {
                throw CancellationException("服务会话已变更")
            }
        }
    }

    private fun <T> unwrap(response: Response<ApiEnvelope<T>>): T {
        if (!response.isSuccessful) throw parseHttpError(response)
        val body = response.body() ?: throw ApiException("服务器返回了空响应", response.code())
        if (body.code != 0) throw ApiException(body.message.ifBlank { "请求失败" }, response.code(), body.code)
        return body.data ?: throw ApiException("服务器响应缺少数据", response.code(), body.code)
    }

    private fun <T> unwrapNullable(response: Response<ApiEnvelope<T>>): T? {
        if (!response.isSuccessful) throw parseHttpError(response)
        val body = response.body() ?: return null
        if (body.code != 0) throw ApiException(body.message.ifBlank { "请求失败" }, response.code(), body.code)
        return body.data
    }

    private fun parseHttpError(response: Response<*>): ApiException {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        val json = runCatching { Gson().fromJson(raw, JsonObject::class.java) }.getOrNull()
        val message = json?.get("message")?.asString
            ?: json?.get("detail")?.asString
            ?: when (response.code()) {
                301, 302, 303, 307, 308 -> "服务返回重定向，请使用最终的服务器地址"
                401 -> "登录已过期，请重新登录"
                403 -> "没有权限执行此操作"
                404 -> "服务器不支持此功能"
                429 -> "请求过于频繁，请稍后重试"
                else -> "请求失败 (${response.code()})"
            }
        return ApiException(message, response.code(), json?.get("code")?.asInt)
    }
}

fun editedAccountCredentials(account: AiAccount, baseUrl: String, apiKey: String): Map<String, Any?>? {
    val base = baseUrl.trim()
    if (base.isNotBlank()) {
        val parsed = runCatching { java.net.URI(base) }.getOrNull()
        require(parsed?.host?.isNotBlank() == true && parsed.scheme.lowercase() in setOf("https", "http")) { "Base URL 需要是完整的 HTTP 或 HTTPS 地址" }
        require(parsed.rawUserInfo == null) { "Base URL 不能包含用户名或密码" }
    }
    val changes = mutableMapOf<String, Any?>()
    if (base != (account.credentials["base_url"] as? String).orEmpty().trim()) changes["base_url"] = base
    // Secret values are redacted by the server. Blank means keep the existing secret.
    apiKey.trim().takeIf { it.isNotEmpty() }?.let { changes["api_key"] = it }
    return changes.takeIf { it.isNotEmpty() }?.let { account.credentials + it }
}

internal object AccountTestSseParser {
    private val gson = Gson()

    fun parse(raw: String): AiAccountTestResult {
        var completed = false
        var succeeded = false
        var error: String? = null
        var model: String? = null
        raw.lineSequence().forEach { line ->
            val payload = line.trimStart().takeIf { it.startsWith("data:") }
                ?.removePrefix("data:")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@forEach
            val event = runCatching { gson.fromJson(payload, JsonObject::class.java) }.getOrNull() ?: return@forEach
            when (event["type"]?.asString) {
                "test_start" -> model = event["model"]?.takeUnless { it.isJsonNull }?.asString
                "error" -> error = event["error"]?.asString ?: "连接测试失败"
                "test_complete" -> { completed = true; succeeded = event["success"]?.asBoolean == true }
            }
        }
        return when {
            error != null -> AiAccountTestResult(success = false, message = error.orEmpty(), model = model)
            completed && succeeded -> AiAccountTestResult(success = true, message = "连接测试通过", model = model)
            completed -> AiAccountTestResult(success = false, message = "连接测试失败", model = model)
            else -> AiAccountTestResult(success = false, message = "连接测试未返回完成状态")
        }
    }

}

internal object SubscriptionProgressParser {
    fun parse(items: List<JsonElement>, gson: Gson): List<SubscriptionProgress> = items.mapNotNull { element ->
        runCatching {
            val json = element.asJsonObject
            if (json.has("progress")) {
                val progressElement = json.get("progress")?.takeUnless { it.isJsonNull } ?: return@runCatching null
                val progress = gson.fromJson(progressElement, SubscriptionProgress::class.java)
                val wrapperSubscriptionId = json.getAsJsonObject("subscription")
                    ?.get("id")
                    ?.takeUnless { it.isJsonNull }
                    ?.asLong
                progress.copy(
                    subscriptionId = progress.subscriptionId.takeIf { it != 0L } ?: wrapperSubscriptionId ?: 0L,
                )
            } else {
                gson.fromJson(element, SubscriptionProgress::class.java)
            }
        }.getOrNull()
    }
}
