package com.sub2api.mobile.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsScreenTest {
    @Test
    fun buildsArbitraryCredentialJsonAlongsideKnownFields() {
        val credentials = buildCredentials(
            apiKey = " sk-example ",
            accessToken = "",
            refreshToken = "refresh-example",
            baseUrl = " https://gateway.example.com ",
            extraJson = """{
              "organization_id":"org-42",
              "nested":{"enabled":true},
              "retry_count":2
            }""".trimIndent(),
        )

        assertEquals("sk-example", credentials["api_key"])
        assertEquals("refresh-example", credentials["refresh_token"])
        assertEquals("https://gateway.example.com", credentials["base_url"])
        assertEquals("org-42", credentials["organization_id"])
        assertEquals(2.0, credentials["retry_count"])
        assertTrue(credentials["nested"] is Map<*, *>)
    }
}
