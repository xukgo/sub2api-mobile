package com.sub2api.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.data.ServerSummary
import com.sub2api.mobile.ui.components.ServerManagerDialog
import com.sub2api.mobile.ui.theme.Sub2ApiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ServerManagerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun switchesBetweenSavedServices() {
        var selected = ""
        compose.setContent {
            Sub2ApiTheme {
                ServerManagerDialog(
                    state = AppUiState(servers = listOf(
                        ServerSummary("a", "家庭服务", "https://home.example.com/api/v1/", "a@example.com", true),
                        ServerSummary("b", "云服务", "https://cloud.example.com/api/v1/", "", false),
                    ), activeServerId = "a"),
                    onSelect = { selected = it }, onSave = { _, _, _ -> true }, onRemove = {}, onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("家庭服务 · 当前").assertIsDisplayed()
        compose.onNodeWithText("切换").performClick()
        compose.runOnIdle { assertEquals("b", selected) }
    }

    @Test
    fun failedSaveRetainsEnteredServiceAndShowsError() {
        compose.setContent {
            var state by remember { mutableStateOf(AppUiState()) }
            Sub2ApiTheme {
                ServerManagerDialog(state, onSelect = {}, onSave = { _, _, _ ->
                    state = state.copy(serverManagerError = "该服务地址已存在")
                    false
                }, onRemove = {}, onDismiss = {})
            }
        }
        compose.onNodeWithText("添加服务").performClick()
        compose.onNodeWithText("服务名称").performTextInput("家庭服务")
        compose.onNodeWithText("服务器地址").performTextInput("https://home.example.com")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("该服务地址已存在").assertIsDisplayed()
        compose.onNodeWithText("家庭服务").assertIsDisplayed()
    }
}
