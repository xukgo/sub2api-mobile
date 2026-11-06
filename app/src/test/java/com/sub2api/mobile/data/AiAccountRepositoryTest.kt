package com.sub2api.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAccountRepositoryTest {
    @Test
    fun cookieUpdateKeepsVisibleConfigurationForServerSideSecretMerge() {
        val current = mapOf<String, Any?>(
            "base_url" to "https://api.example.com",
            "organization_uuid" to "org-123",
            "model_mapping" to mapOf("claude" to "custom-claude"),
        )

        val updated = credentialsWithReplacement(current, "cookie", "  session-cookie  ")

        assertEquals("session-cookie", updated["cookie"])
        assertEquals(current["base_url"], updated["base_url"])
        assertEquals(current["organization_uuid"], updated["organization_uuid"])
        assertEquals(current["model_mapping"], updated["model_mapping"])
        assertFalse(current.containsKey("cookie"))
    }

    @Test
    fun parsesSuccessfulAccountTestSse() {
        val result = AccountTestSseParser.parse(
            """
            data: {"type":"test_start","model":"claude-sonnet"}

            data:{"type":"content","text":"Hi"}

            data: {"type":"test_complete","success":true}
            """.trimIndent(),
        )

        assertTrue(result.success)
        assertEquals("连接测试通过", result.message)
    }

    @Test
    fun parsesAccountTestSseError() {
        val result = AccountTestSseParser.parse("data: {\"type\":\"error\",\"error\":\"invalid token\"}\n\n")

        assertFalse(result.success)
        assertEquals("invalid token", result.message)
    }

    @Test
    fun completedButFailedTestIsReportedAsFailure() {
        val result = AccountTestSseParser.parse("data: {\"type\":\"test_start\",\"model\":\"custom\"}\n\ndata: {\"type\":\"test_complete\",\"success\":false}\n")
        assertFalse(result.success)
        assertEquals("连接测试失败", result.message)
        assertEquals("custom", result.model)
    }
    @Test
    fun completionWithoutExplicitSuccessDoesNotReportFalsePositive() {
        // The server omits success=false because TestEvent uses json:"success,omitempty".
        val result = AccountTestSseParser.parse("data: {\"type\":\"test_complete\"}\n")
        assertFalse(result.success)
    }

}
