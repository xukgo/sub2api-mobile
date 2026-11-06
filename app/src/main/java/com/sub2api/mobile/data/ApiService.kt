package com.sub2api.mobile.data

import com.google.gson.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Streaming
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("settings/public")
    suspend fun publicSettings(): Response<ApiEnvelope<PublicSettings>>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiEnvelope<LoginResult>>

    @POST("auth/login/2fa")
    suspend fun login2Fa(@Body request: Login2FaRequest): Response<ApiEnvelope<LoginResult>>

    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshRequest): Response<ApiEnvelope<JsonElement>>

    @GET("auth/me")
    suspend fun currentUser(): Response<ApiEnvelope<User>>

    @GET("usage/dashboard/stats")
    suspend fun dashboardStats(): Response<ApiEnvelope<DashboardStats>>

    @GET("subscriptions/summary")
    suspend fun subscriptionSummary(): Response<ApiEnvelope<SubscriptionSummary>>

    @GET("keys")
    suspend fun keys(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
    ): Response<ApiEnvelope<Paginated<ApiKey>>>

    @GET("groups/available")
    suspend fun availableGroups(): Response<ApiEnvelope<List<Group>>>

    @GET("channel-monitors")
    suspend fun channelMonitors(): Response<ApiEnvelope<ChannelMonitorListResponse>>

    @GET("channel-monitors/{id}/status")
    suspend fun channelMonitorStatus(@Path("id") id: Long): Response<ApiEnvelope<ChannelMonitorDetail>>

    @POST("keys")
    suspend fun createKey(@Body request: CreateApiKeyRequest): Response<ApiEnvelope<ApiKey>>

    @PUT("keys/{id}")
    suspend fun updateKey(
        @Path("id") id: Long,
        @Body request: UpdateApiKeyRequest,
    ): Response<ApiEnvelope<ApiKey>>

    @DELETE("keys/{id}")
    suspend fun deleteKey(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @GET("usage/stats")
    suspend fun usageStats(
        @Query("period") period: String,
        @Query("timezone") timezone: String,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
    ): Response<ApiEnvelope<UsageStats>>

    @GET("usage")
    suspend fun usageLogs(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 50,
        @Query("sort_by") sortBy: String = "created_at",
        @Query("sort_order") sortOrder: String = "desc",
        @Query("timezone") timezone: String,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
    ): Response<ApiEnvelope<Paginated<UsageLog>>>

    @GET("admin/usage")
    suspend fun adminUsageLogs(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int = 50,
        @Query("sort_by") sortBy: String = "created_at",
        @Query("sort_order") sortOrder: String = "desc",
        @Query("timezone") timezone: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("user_id") userId: Long? = null,
        @Query("account_id") accountId: Long? = null,
        @Query("model") model: String? = null,
        @Query("exact_total") exactTotal: Boolean = true,
    ): Response<ApiEnvelope<Paginated<UsageLog>>>

    @GET("admin/usage/stats")
    suspend fun adminUsageStats(
        @Query("timezone") timezone: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("user_id") userId: Long? = null,
        @Query("account_id") accountId: Long? = null,
        @Query("model") model: String? = null,
        @Query("nocache") noCache: Int = 1,
    ): Response<ApiEnvelope<UsageStats>>

    @GET("admin/dashboard/user-breakdown")
    suspend fun userUsageRanking(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("timezone") timezone: String,
        @Query("sort_by") sortBy: String,
        @Query("limit") limit: Int = 50,
    ): Response<ApiEnvelope<UserUsageRankingResponse>>

    @GET("subscriptions")
    suspend fun subscriptions(): Response<ApiEnvelope<List<UserSubscription>>>

    @GET("subscriptions/progress")
    suspend fun subscriptionProgress(): Response<ApiEnvelope<List<JsonElement>>>

    @POST("redeem")
    suspend fun redeem(@Body request: RedeemRequest): Response<ApiEnvelope<RedeemResult>>

    @GET("redeem/history")
    suspend fun redeemHistory(): Response<ApiEnvelope<List<RedeemHistory>>>

    @PUT("user")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<ApiEnvelope<User>>

    @GET("admin/accounts")
    suspend fun aiAccounts(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
        @Query("platform") platform: String? = null,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
        @Query("lite") lite: String = "false",
    ): Response<ApiEnvelope<Paginated<AiAccount>>>

    @GET("admin/groups/all")
    suspend fun adminGroups(): Response<ApiEnvelope<List<Group>>>

    @GET("admin/accounts/{id}")
    suspend fun aiAccount(@Path("id") id: Long): Response<ApiEnvelope<AiAccount>>

    @POST("admin/accounts")
    suspend fun createAiAccount(@Body request: CreateAiAccountRequest): Response<ApiEnvelope<AiAccount>>

    @PUT("admin/accounts/{id}")
    suspend fun updateAiAccount(
        @Path("id") id: Long,
        @Body request: UpdateAiAccountRequest,
    ): Response<ApiEnvelope<AiAccount>>

    @DELETE("admin/accounts/{id}")
    suspend fun deleteAiAccount(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @Streaming
    @POST("admin/accounts/{id}/test")
    suspend fun testAiAccount(@Path("id") id: Long, @Body request: TestAiAccountRequest): Response<ResponseBody>

    @GET("admin/accounts/{id}/models")
    suspend fun accountModels(@Path("id") id: Long): Response<ApiEnvelope<List<AccountModel>>>

    @POST("admin/accounts/{id}/refresh")
    suspend fun refreshAiAccount(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @GET("admin/accounts/{id}/usage")
    suspend fun aiAccountUsage(
        @Path("id") id: Long,
        @Query("source") source: String? = null,
        @Query("force") force: Boolean? = null,
    ): Response<ApiEnvelope<AiAccountUsage>>

    @POST("admin/accounts/{id}/clear-error")
    suspend fun clearAiAccountError(@Path("id") id: Long): Response<ApiEnvelope<AiAccount>>

    @POST("admin/accounts/cookie-auth")
    suspend fun exchangeCookie(@Body request: CookieAuthRequest): Response<ApiEnvelope<Map<String, Any?>>>

    @POST("admin/accounts/setup-token-cookie-auth")
    suspend fun exchangeSetupTokenCookie(@Body request: CookieAuthRequest): Response<ApiEnvelope<Map<String, Any?>>>

    @POST("admin/accounts/{id}/apply-oauth-credentials")
    suspend fun applyOAuthCredentials(
        @Path("id") id: Long,
        @Body request: ApplyOAuthCredentialsRequest,
    ): Response<ApiEnvelope<AiAccount>>
}
