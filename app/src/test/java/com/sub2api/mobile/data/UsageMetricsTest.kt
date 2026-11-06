package com.sub2api.mobile.data

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.*
import org.junit.Test

class UsageMetricsTest {
    @Test
    fun decodesAdminUsageIdentityAndTimingFieldsFromV0219() {
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
        val log = gson.fromJson("""{"id":9,"user_id":8,"account_id":7,"channel_id":6,"model":"requested","upstream_model":"sent","first_token_ms":500,"duration_ms":2000,"output_tokens":100,"user":{"id":8,"email":"owner@example.com"},"account":{"id":7,"name":"Cloud API"},"group":{"name":"Primary"}}""", UsageLog::class.java)
        assertEquals("owner@example.com", log.user!!.email)
        assertEquals("Cloud API", log.accountLabel)
        assertEquals("渠道 #6", log.channelLabel)
        assertEquals("sent", log.upstreamModel)
        assertEquals(500L, log.firstTokenMs)
        assertEquals(50.0, log.outputRate!!, 0.001)
    }

    @Test
    fun tpsMatchesWebFullDurationRatherThanSubtractingFirstTokenTime() {
        val log = UsageLog(outputTokens = 100, durationMs = 2000, firstTokenMs = 1000, requestType = "stream")
        assertEquals(50.0, log.outputRate!!, 0.001)
        assertNull(log.copy(durationMs = null).outputRate)
        assertNull(log.copy(durationMs = 0).outputRate)
        assertNull(log.copy(outputTokens = 0).outputRate)
        assertNull(log.copy(imageCount = 1).outputRate)
        assertNull(log.copy(imageOutputTokens = 10).outputRate)
        assertNull(log.copy(billingMode = "image").outputRate)
        assertNull(log.copy(requestType = "video").outputRate)
    }

    @Test
    fun channelSamplesUseWeightedTotalsAndExcludeInvalidThroughput() {
        val rows = listOf(
            UsageLog(accountId = 1, channelId = 2, outputTokens = 100, durationMs = 1000, firstTokenMs = 100),
            UsageLog(accountId = 1, channelId = 2, outputTokens = 100, durationMs = 3000, firstTokenMs = 300),
            UsageLog(accountId = 1, channelId = 2, outputTokens = 100, durationMs = 0),
            UsageLog(accountId = 4, channelId = 5, outputTokens = 200, durationMs = 1000),
        )
        val first = channelPerformance(rows).first()
        assertEquals(3, first.requests)
        assertEquals(50.0, first.outputRate!!, 0.001)
        assertEquals(200.0, first.firstTokenMs!!, 0.001)
        assertEquals(2, channelPerformance(rows).size)
    }

    @Test
    fun rankingRowsSortByTheSelectedServerMetric() {
        val rows = listOf(
            UserUsageRank(userId = 1, totalTokens = 10, requests = 20, actualCost = 0.1),
            UserUsageRank(userId = 2, totalTokens = 100, requests = 1, actualCost = 2.0),
        )
        assertEquals(2L, sortedUserRanking(rows, "total_tokens").first().userId)
        assertEquals(1L, sortedUserRanking(rows, "requests").first().userId)
        assertEquals(2L, sortedUserRanking(rows, "actual_cost").first().userId)
    }

    @Test
    fun blankApiKeyPreservesRedactedSecretAndUnrelatedConfiguration() {
        val account = AiAccount(credentials = mapOf("base_url" to "https://old.example.com", "model_mapping" to mapOf("x" to "y")),
            credentialsStatus = mapOf("has_api_key" to true))
        assertNull(editedAccountCredentials(account, "https://old.example.com", ""))
        val changed = editedAccountCredentials(account, "https://new.example.com", " ")!!
        assertEquals("https://new.example.com", changed["base_url"])
        assertFalse(changed.containsKey("api_key"))
        assertEquals(account.credentials["model_mapping"], changed["model_mapping"])
        assertEquals("new-secret", editedAccountCredentials(account, "https://old.example.com", " new-secret ")!!["api_key"])
        assertThrows(IllegalArgumentException::class.java) { editedAccountCredentials(account, "ftp://host", "") }
    }
}
