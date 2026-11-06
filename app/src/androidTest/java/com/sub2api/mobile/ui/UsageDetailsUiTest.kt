package com.sub2api.mobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sub2api.mobile.data.UsageAccountSummary
import com.sub2api.mobile.data.UsageLog
import com.sub2api.mobile.data.User
import com.sub2api.mobile.ui.screens.UsageLogCard
import com.sub2api.mobile.ui.theme.Sub2ApiTheme
import org.junit.Rule
import org.junit.Test

class UsageDetailsUiTest {
    @get:Rule val compose = createComposeRule()
    private val log = UsageLog(id = 1, userId = 2, user = User(email = "requester@example.com"),
        account = UsageAccountSummary(3, "Cloud channel"), channelId = 4, model = "custom-model",
        firstTokenMs = 500, durationMs = 2000, outputTokens = 100, createdAt = "2026-11-06T12:00:00Z")

    @Test
    fun adminRecordDisplaysIdentityFirstTokenDurationAndThroughput() {
        compose.setContent { Sub2ApiTheme { UsageLogCard(log, admin = true) } }
        compose.onNodeWithText("用户：requester@example.com").assertIsDisplayed()
        compose.onNodeWithText("账号：Cloud channel").assertIsDisplayed()
        compose.onNodeWithText("custom-model").assertIsDisplayed()
        compose.onNodeWithText("0.50 s").assertIsDisplayed()
        compose.onNodeWithText("2.00 s").assertIsDisplayed()
        compose.onNodeWithText("50.0 tok/s").assertIsDisplayed()
    }

    @Test
    fun personalRecordDoesNotRenderAdminUserIdentity() {
        compose.setContent { Sub2ApiTheme { UsageLogCard(log, admin = false) } }
        compose.onNodeWithText("用户：requester@example.com").assertDoesNotExist()
        compose.onNodeWithText("TPS").assertIsDisplayed()
    }
}
