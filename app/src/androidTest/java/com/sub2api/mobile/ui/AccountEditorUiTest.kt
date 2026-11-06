package com.sub2api.mobile.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.data.AiAccount
import com.sub2api.mobile.data.UpdateAiAccountRequest
import com.sub2api.mobile.data.User
import com.sub2api.mobile.ui.screens.AccountsScreen
import com.sub2api.mobile.ui.theme.Sub2ApiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccountEditorUiTest {
    @get:Rule val compose = createComposeRule()
    private var updated: UpdateAiAccountRequest? = null

    private fun openEditor() {
        val account = AiAccount(id = 1, name = "Upstream", platform = "openai", type = "apikey", status = "active",
            credentials = mapOf("base_url" to "https://old.example.com", "model_mapping" to mapOf("a" to "b")),
            credentialsStatus = mapOf("has_api_key" to true))
        compose.setContent {
            Sub2ApiTheme {
                AccountsScreen(
                    state = AppUiState(user = User(id = 1, role = "admin"), aiAccounts = listOf(account), allAiAccounts = listOf(account)),
                    onFilter = { _, _, _ -> }, onCreate = { _, _, _, _ -> },
                    onUpdate = { _, request, done -> updated = request; done() },
                    onCredential = { _, _, _, _, _ -> }, onTest = { _, _ -> },
                    onRefreshCredential = {}, onClearError = {}, onToggle = {}, onDelete = {}, onRefreshQuotas = { _, _, _ -> },
                )
            }
        }
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("编辑设置").performClick()
    }

    @Test
    fun changingBaseUrlDoesNotReplaceRedactedApiKey() {
        openEditor()
        compose.onNodeWithTag("account-editor-fields").performScrollToNode(hasText("Base URL"))
        compose.onNodeWithText("Base URL").performTextReplacement("https://new.example.com")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals("https://new.example.com", updated!!.credentials!!["base_url"])
            assertFalse(updated!!.credentials!!.containsKey("api_key"))
            assertEquals(mapOf("a" to "b"), updated!!.credentials!!["model_mapping"])
        }
    }

    @Test
    fun apiKeyCanBeReplacedFromExistingAccountEditor() {
        openEditor()
        compose.onNodeWithTag("account-editor-fields").performScrollToNode(hasText("新 API Key（留空保留）"))
        compose.onNodeWithText("新 API Key（留空保留）").performTextReplacement("replacement-test-key")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals("replacement-test-key", updated!!.credentials!!["api_key"]) }
    }
}
