package com.sub2api.mobile.data

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

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
    fun rolling24HoursAndTodayRemainDistinctAcrossDaylightSavingTime() {
        val now = Instant.parse("2026-03-08T18:00:00Z")
        val zone = ZoneId.of("America/New_York")
        val rolling = RankingWindow.create("24h", zone, now)
        val today = RankingWindow.create("today", zone, now)
        assertEquals(24L, Duration.between(rolling.start, rolling.end).toHours())
        assertEquals(Instant.parse("2026-03-08T05:00:00Z"), today.start)
        assertEquals("2026-03-07", rolling.dates.start)
        assertEquals("2026-03-08", rolling.dates.end)
        assertEquals(UsageDateRange("2026-03-02", "2026-03-08"), usageDateRange("week", zone, now))
    }

    @Test
    fun rollingRankingFiltersExactBoundariesAndDeduplicatesPages() {
        val window = RankingWindow.create("24h", ZoneId.of("UTC"), Instant.parse("2026-11-06T12:00:00Z"))
        val accumulator = UsageRankingAccumulator(window)
        val first = UsageLog(id = 1, userId = 10, inputTokens = 5, outputTokens = 10, cacheReadTokens = 5, actualCost = 0.1,
            createdAt = window.start.toString(), user = User(email = "first@example.com"))
        accumulator.add(first)
        accumulator.add(first)
        accumulator.add(first.copy(id = 2, createdAt = window.start.minusMillis(1).toString()))
        accumulator.add(first.copy(id = 3, createdAt = window.end.toString()))
        accumulator.add(first.copy(id = 4, userId = 20, createdAt = window.end.minusSeconds(1).toString(), outputTokens = 100))
        val result = accumulator.result(5)
        assertEquals(2, result.rows.size)
        assertEquals(2L, result.rows.sumOf { it.requests })
        assertEquals(20L, result.rows.first { it.userId == 10L }.totalTokens)
        assertEquals(20L, sortedUserRanking(result.rows, "total_tokens").first().userId)
        assertThrows(ApiException::class.java) { accumulator.add(first.copy(id = 9, createdAt = "invalid")) }
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
