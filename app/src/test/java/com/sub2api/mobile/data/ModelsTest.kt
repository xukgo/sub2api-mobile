package com.sub2api.mobile.data

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun usageLogTotalsAllTokenKinds() {
        val log = UsageLog(
            inputTokens = 10,
            outputTokens = 20,
            cacheCreationTokens = 30,
            cacheReadTokens = 40,
        )

        assertEquals(100, log.totalTokens)
    }

    @Test
    fun twoFactorFieldMatchesUpstreamJsonName() {
        val result = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
            .fromJson(
            """{"requires_2fa":true,"temp_token":"temporary"}""",
            LoginResult::class.java,
        )

        assertEquals(true, result.requires2fa)
        assertEquals("temporary", result.tempToken)
    }

    @Test
    fun aiAccountCredentialStatusMapsWithoutExposingSecrets() {
        val account = gson().fromJson(
            """{
              "id":12,
              "name":"Claude Team",
              "platform":"anthropic",
              "type":"oauth",
              "credentials":{"organization_uuid":"org-visible"},
              "credentials_status":{"has_cookie":true,"has_access_token":true,"has_api_key":false}
            }""".trimIndent(),
            AiAccount::class.java,
        )

        assertTrue(account.hasCredential("cookie"))
        assertTrue(account.hasCredential("access_token"))
        assertFalse(account.hasCredential("api_key"))
        assertFalse(account.credentials.containsKey("cookie"))
        assertFalse(account.credentials.containsKey("access_token"))
    }

    @Test
    fun parsesCurrentAdminAccountListEnvelope() {
        val type = object : TypeToken<ApiEnvelope<Paginated<AiAccount>>>() {}.type
        val envelope: ApiEnvelope<Paginated<AiAccount>> = gson().fromJson(
            """{
              "code":0,
              "message":"success",
              "data":{
                "items":[{
                  "id":21,
                  "name":"OpenAI Main",
                  "platform":"openai",
                  "type":"apikey",
                  "credentials":{},
                  "credentials_status":{"has_api_key":true},
                  "current_concurrency":2,
                  "group_ids":[3],
                  "groups":[{"id":3,"name":"Codex","platform":"openai"}]
                }],
                "total":1,
                "page":1,
                "page_size":100,
                "pages":1
              }
            }""".trimIndent(),
            type,
        )

        val account = envelope.data!!.items.single()
        assertEquals(21L, account.id)
        assertEquals(2, account.currentConcurrency)
        assertEquals(listOf(3L), account.groupIds)
        assertEquals("Codex", account.groups.single().name)
        assertTrue(account.hasCredential("api_key"))
    }

    @Test
    fun parsesCrossPlatformAccountUsageResponse() {
        val usage = gson().fromJson(
            """{
              "source":"active",
              "five_hour":{"utilization":42.5,"remaining_seconds":3600},
              "antigravity_quota":{"gemini-pro":{"utilization":63,"reset_time":"2026-07-21T00:00:00Z"}},
              "grok_token_quota":{"limit":1000000,"remaining":250000,"reset_at":"2026-07-21T00:00:00Z"},
              "grok_billing":{"period_type":"weekly","usage_percent":35},
              "ai_credits":[{"credit_type":"standard","amount":18.5}]
            }""".trimIndent(),
            AiAccountUsage::class.java,
        )

        assertEquals(42.5, usage.fiveHour!!.utilization, 0.0001)
        assertEquals(63.0, usage.antigravityQuota.getValue("gemini-pro").utilization, 0.0001)
        assertEquals(250_000.0, usage.grokTokenQuota!!.remaining!!, 0.0001)
        assertEquals(35.0, usage.grokBilling!!.usagePercent!!, 0.0001)
        assertEquals(18.5, usage.aiCredits.single().amount, 0.0001)
    }

    @Test
    fun parsesChannelMonitorListAndTimeline() {
        val type = object : TypeToken<ApiEnvelope<ChannelMonitorListResponse>>() {}.type
        val envelope: ApiEnvelope<ChannelMonitorListResponse> = gson().fromJson(
            """{
              "code":0,
              "message":"success",
              "data":{"items":[{
                "id":8,
                "name":"OpenAI Primary",
                "provider":"openai",
                "group_name":"Pro",
                "primary_model":"gpt-5.2",
                "primary_status":"operational",
                "primary_latency_ms":842,
                "primary_ping_latency_ms":45,
                "availability_7d":99.75,
                "extra_models":[{"model":"gpt-5-mini","status":"degraded","latency_ms":1280}],
                "timeline":[{"status":"operational","latency_ms":842,"ping_latency_ms":45,"checked_at":"2026-07-20T14:30:00Z"}]
              }]}
            }""".trimIndent(),
            type,
        )

        val monitor = envelope.data!!.items.single()
        assertEquals(8L, monitor.id)
        assertEquals("Pro", monitor.groupName)
        assertEquals("gpt-5.2", monitor.primaryModel)
        assertEquals(842, monitor.primaryLatencyMs)
        assertEquals(45, monitor.primaryPingLatencyMs)
        assertEquals(99.75, monitor.availability7d, 0.0001)
        assertEquals("degraded", monitor.extraModels.single().status)
        assertEquals("2026-07-20T14:30:00Z", monitor.timeline.single().checkedAt)
    }

    @Test
    fun parsesChannelMonitorMultiWindowDetail() {
        val type = object : TypeToken<ApiEnvelope<ChannelMonitorDetail>>() {}.type
        val envelope: ApiEnvelope<ChannelMonitorDetail> = gson().fromJson(
            """{
              "code":0,
              "message":"success",
              "data":{
                "id":8,
                "name":"OpenAI Primary",
                "provider":"openai",
                "group_name":"Pro",
                "models":[{
                  "model":"gpt-5.2",
                  "latest_status":"operational",
                  "latest_latency_ms":842,
                  "availability_7d":99.75,
                  "availability_15d":99.5,
                  "availability_30d":98.8,
                  "avg_latency_7d_ms":790
                }]
              }
            }""".trimIndent(),
            type,
        )

        val detail = envelope.data!!
        val model = detail.models.single()
        assertEquals("OpenAI Primary", detail.name)
        assertEquals("operational", model.latestStatus)
        assertEquals(99.5, model.availability15d, 0.0001)
        assertEquals(98.8, model.availability30d, 0.0001)
        assertEquals(790, model.avgLatency7dMs)
    }

    private fun gson() = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
}
