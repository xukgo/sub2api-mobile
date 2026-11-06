package com.sub2api.mobile.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsOff
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
    private var toggles = 0

    private fun showAccounts(type: String = "apikey") {
        val account = AiAccount(id = 1, name = "Upstream", platform = "openai", type = type, status = "active", schedulable = false, priority = 8,
            credentials = mapOf("base_url" to "https://old.example.com", "model_mapping" to mapOf("a" to "b")),
            credentialsStatus = mapOf("has_api_key" to true))
        compose.setContent {
            Sub2ApiTheme {
                AccountsScreen(
                    state = AppUiState(user = User(id = 1, role = "admin"), aiAccounts = listOf(account), allAiAccounts = listOf(account)),
                    onFilter = { _, _, _ -> }, onCreate = { _, _, _, _ -> },
                    onUpdate = { _, request, done -> updated = request; done() },
                    onTest = { _, _ -> },
                    onClearError = {}, onToggle = { toggles++ }, onDelete = {}, onRefreshQuotas = { _, _, _ -> },
                )
            }
        }
    }

    private fun openEditor() {
        showAccounts()
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

    @Test
    fun switchShowsSchedulingRatherThanActiveStatusAndCredentialActionsAreRemoved() {
        showAccounts(type = "oauth")
        compose.onNodeWithContentDescription("账号调度").assertIsOff().performClick()
        compose.runOnIdle { assertEquals(1, toggles) }
        compose.onNodeWithText("调度停用").assertExists()
        compose.onNodeWithContentDescription("更新 Cookie 或凭据").assertDoesNotExist()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("刷新凭据").assertDoesNotExist()
    }

    @Test
    fun priorityCanBeEditedDirectlyWithoutChangingOtherSettings() {
        showAccounts()
        compose.onNodeWithText("优先级：8").performClick()
        compose.onNodeWithText("优先级").performTextReplacement("-3")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals(-3, updated!!.priority)
            assertNull(updated!!.credentials)
            assertNull(updated!!.status)
            assertNull(updated!!.name)
        }
    }

    @Test
    fun invalidPriorityDoesNotSilentlySaveAsZero() {
        showAccounts()
        compose.onNodeWithText("优先级：8").performClick()
        compose.onNodeWithText("优先级").performTextReplacement("2147483648")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("请输入有效整数").assertExists()
        compose.runOnIdle { assertNull(updated) }
    }
}
