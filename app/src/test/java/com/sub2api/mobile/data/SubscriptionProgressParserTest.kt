package com.sub2api.mobile.data

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionProgressParserTest {
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @Test
    fun parsesCurrentWrappedResponse() {
        val json = """
            [{
              "subscription": {"id": 7},
              "progress": {
                "id": 7,
                "group_name": "Pro",
                "expires_in_days": 9,
                "daily": {
                  "used_usd": 3.0,
                  "limit_usd": 10.0,
                  "percentage": 30.0,
                  "resets_in_seconds": 120
                }
              }
            }]
        """.trimIndent()

        val result = SubscriptionProgressParser.parse(JsonParser.parseString(json).asJsonArray.toList(), gson)

        assertEquals(1, result.size)
        assertEquals(7, result.single().subscriptionId)
        assertEquals(9, result.single().daysRemaining)
        assertEquals(3.0, result.single().daily!!.used, 0.0001)
        assertEquals(10.0, result.single().daily!!.limit!!, 0.0001)
        assertEquals(120L, result.single().daily!!.resetInSeconds)
    }

    @Test
    fun parsesLegacyFlatResponse() {
        val json = """
            [{
              "subscription_id": 11,
              "days_remaining": 30,
              "daily": {"used": 1.0, "limit": 8.0, "percentage": 12.5}
            }]
        """.trimIndent()

        val result = SubscriptionProgressParser.parse(JsonParser.parseString(json).asJsonArray.toList(), gson)

        assertEquals(11, result.single().subscriptionId)
        assertEquals(12.5, result.single().daily!!.percentage, 0.0001)
    }

    @Test
    fun skipsMalformedEntriesWithoutCrashingTheScreen() {
        val json = """[{"progress":null}, "not-an-object", null]"""

        val result = SubscriptionProgressParser.parse(JsonParser.parseString(json).asJsonArray.toList(), gson)

        assertEquals(emptyList<SubscriptionProgress>(), result)
    }
}
