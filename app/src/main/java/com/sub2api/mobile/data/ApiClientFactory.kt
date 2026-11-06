package com.sub2api.mobile.data

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response as OkHttpResponse
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class ApiClientFactory(private val sessionStore: SessionStore) {
    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    private val services = mutableMapOf<Pair<String, Long>, ApiService>()

    @Synchronized
    fun service(session: SessionState): ApiService {
        require(session.isConfigured) { "请先配置服务器" }
        services.keys.removeAll { (id, revision) ->
            sessionStore.profiles.value.none { it.id == id } ||
                (id == session.profileId && revision != session.revision)
        }
        return services.getOrPut(session.profileId to session.revision) { build(session) }
    }

    private fun build(expected: SessionState): ApiService {
        val baseUrl = expected.serverUrl
        val refreshLock = Any()
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val session = sessionStore.sessionFor(expected) ?: throw IOException("服务会话已失效")
                val request = chain.request().newBuilder()
                    .header("Accept-Language", Locale.getDefault().toLanguageTag())
                    .header("X-User-UI-Request", "1")
                    .apply {
                        if (chain.request().url.encodedPath.contains("/api/v1/admin/")) {
                            header("X-Admin-UI-Request", "1")
                        }
                        if (session.accessToken.isNotBlank()) {
                            header("Authorization", "Bearer ${session.accessToken}")
                        }
                    }
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .authenticator { _, response -> synchronized(refreshLock) { refreshAccessToken(expected, response) } }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }

    private fun refreshAccessToken(expected: SessionState, failedResponse: OkHttpResponse): Request? {
        val baseUrl = expected.serverUrl
        if (responseCount(failedResponse) > 1) return null
        val failedRequest = failedResponse.request
        val failedToken = failedRequest.header("Authorization")?.removePrefix("Bearer ").orEmpty()
        val current = sessionStore.sessionFor(expected) ?: return null
        if (current.accessToken.isNotBlank() && current.accessToken != failedToken) {
            return failedRequest.newBuilder()
                .header("Authorization", "Bearer ${current.accessToken}")
                .build()
        }
        if (current.refreshToken.isBlank()) return null

        val body = gson.toJson(RefreshRequest(current.refreshToken))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${baseUrl}auth/refresh")
            .post(body)
            .build()
        val response = runCatching {
            OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(20, TimeUnit.SECONDS).build().newCall(request).execute()
        }.getOrNull() ?: return null

        return try {
            response.use {
                if (!it.isSuccessful) {
                    if (it.code in setOf(400, 401, 403)) sessionStore.clearTokens(expected, expectedRefreshToken = current.refreshToken)
                    return null
                }
                val parsed = runCatching { gson.fromJson(it.body?.charStream(), RefreshEnvelope::class.java) }.getOrNull()
                val refreshed = parsed?.data
                if (parsed?.code != 0 || refreshed == null || refreshed.accessToken.isBlank()) {
                    sessionStore.clearTokens(expected, expectedRefreshToken = current.refreshToken)
                    return null
                }
                if (!sessionStore.setTokens(
                        expected, refreshed.accessToken, refreshed.refreshToken, refreshed.expiresIn,
                        expectedRefreshToken = current.refreshToken,
                    )) return null
                failedRequest.newBuilder()
                    .header("Authorization", "Bearer ${refreshed.accessToken}")
                    .build()
            }
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("无法安全更新服务登录信息", error)
        }
    }

    private fun responseCount(response: OkHttpResponse): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private data class RefreshEnvelope(
        val code: Int = -1,
        val message: String = "",
        val data: RefreshResult? = null,
    )
}
