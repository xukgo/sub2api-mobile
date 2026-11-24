package com.sub2api.mobile.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.data.ApiKey
import com.sub2api.mobile.data.Group
import com.sub2api.mobile.ui.screens.KeysScreen
import com.sub2api.mobile.ui.theme.Sub2ApiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class KeyGroupEditorUiTest {
    @get:Rule val compose = createComposeRule()
    private val current = Group(id = 10, name = "GPT 高峰期推荐（神奇的 GPT）", platform = "openai",
        description = "主线路 官方纯血 pro20x 号池 可生图", rateMultiplier = 0.17)
    private val other = Group(id = 20, name = "Grok", platform = "grok",
        description = "低延迟专属线路", rateMultiplier = 0.66)
    private val key = ApiKey(id = 7, name = "codex-2", groupId = current.id, group = current)
    private val state = mutableStateOf(AppUiState(keys = listOf(key), groups = listOf(current, other)))
    private val updates = mutableListOf<Pair<Long, Long>>()
    private var loads = 0
    private var finish: (() -> Unit)? = null

    private fun showKeys() {
        compose.setContent {
            Sub2ApiTheme {
                KeysScreen(
                    state = state.value,
                    onCreate = { _, _, _, _, _ -> }, onToggle = {}, onDelete = {},
                    onLoadGroups = { loads++ },
                    onUpdateGroup = { target, groupId, done ->
                        updates += target.id to groupId
                        finish = done
                        state.value = state.value.copy(keyGroupSaving = true, keyGroupError = null)
                    },
                    onClearGroupError = { state.value = state.value.copy(keyGroupError = null) },
                )
            }
        }
        compose.onNodeWithText("分组：${current.name}").performClick()
    }

    @Test
    fun currentGroupIsSelectedAndSearchChangesSelectionBeforeExplicitSave() {
        showKeys()
        compose.onNodeWithText(current.name).assertIsSelected()
        compose.onNodeWithText("保存修改").assertIsNotEnabled()
        compose.onNodeWithText("搜索分组").performTextReplacement("gRoK")
        compose.onNodeWithText(current.name).assertDoesNotExist()
        compose.onNodeWithText(other.name).performClick()
        compose.runOnIdle { assertTrue(updates.isEmpty()); assertEquals(1, loads) }
        compose.onNodeWithText("保存修改").assertIsEnabled().performClick()
        compose.onNodeWithText("保存中…").assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsNotEnabled()
        compose.onNodeWithText(other.name).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf(7L to 20L), updates) }
    }

    @Test
    fun failedSaveKeepsSelectionForRetryAndSuccessClosesSheet() {
        showKeys()
        compose.onNodeWithText(other.name).performClick()
        compose.onNodeWithText("保存修改").performClick()
        compose.runOnIdle { state.value = state.value.copy(keyGroupSaving = false, keyGroupError = "分组权限已变更") }
        compose.onNodeWithText("分组权限已变更").assertExists()
        compose.onNodeWithText(other.name).assertIsSelected()
        compose.onNodeWithText("保存修改").assertIsEnabled().performClick()
        compose.runOnIdle {
            state.value = state.value.copy(
                keys = listOf(key.copy(groupId = other.id, group = other)),
                keyGroupSaving = false,
                keyGroupError = null,
            )
            finish!!()
            assertEquals(listOf(7L to 20L, 7L to 20L), updates)
        }
        compose.onNodeWithText("修改分组").assertDoesNotExist()
        compose.onNodeWithText("分组：Grok").assertExists()
    }

    @Test
    fun cancellationDiscardsSelectionAndDoesNotSendAnUpdate() {
        showKeys()
        compose.onNodeWithText(other.name).performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("修改分组").assertDoesNotExist()
        compose.onNodeWithText("分组：${current.name}").performClick()
        compose.onNodeWithText(current.name).assertIsSelected()
        compose.onNodeWithText("保存修改").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(updates.isEmpty()) }
    }

    @Test
    fun unavailableGroupsAndUnsupportedDefaultAreNotOfferedAndLoadErrorsAllowRetry() {
        state.value = state.value.copy(groups = listOf(current, other.copy(status = "inactive")))
        showKeys()
        compose.onNodeWithText(other.name).assertDoesNotExist()
        compose.onNodeWithText("默认分组").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(keyGroupsError = "网络连接失败") }
        compose.onNodeWithText("加载分组失败：网络连接失败").assertExists()
        compose.onNodeWithText("保存修改").assertIsNotEnabled()
        compose.onNodeWithText("重新加载").performClick()
        compose.runOnIdle { assertEquals(2, loads) }
    }

    @Test
    fun emptyGroupsAndUnmatchedSearchKeepSaveDisabled() {
        state.value = state.value.copy(groups = emptyList())
        showKeys()
        compose.onNodeWithText("暂无可用分组").assertExists()
        compose.onNodeWithText("保存修改").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(groups = listOf(current, other)) }
        compose.onNodeWithText("搜索分组").performTextReplacement("不存在的分组")
        compose.onNodeWithText("没有匹配的分组").assertExists()
        compose.onNodeWithText("保存修改").assertIsNotEnabled()
    }

    @Test
    fun groupRowsShowProviderDescriptionAndRateAndCanBeFoundByDescription() {
        showKeys()
        compose.onNodeWithContentDescription("OpenAI").assertExists()
        compose.onNodeWithContentDescription("Grok").assertExists()
        compose.onNodeWithText(current.description!!).assertIsDisplayed()
        compose.onNodeWithText("0.17x 倍率").assertIsDisplayed()
        compose.onNodeWithText("0.66x 倍率").assertIsDisplayed()
        compose.onNodeWithText("搜索分组").performTextReplacement("低延迟")
        compose.onNodeWithText(current.name).assertDoesNotExist()
        compose.onNodeWithText(other.description!!).assertIsDisplayed()
        compose.onNodeWithText(other.name).performClick()
        compose.onNodeWithText("保存修改").assertIsEnabled()
    }
}
