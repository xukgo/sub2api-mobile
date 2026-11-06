package com.sub2api.mobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.data.RankingPeriod
import com.sub2api.mobile.data.UsageDateRange
import com.sub2api.mobile.data.UsageRanking
import com.sub2api.mobile.data.User
import com.sub2api.mobile.ui.screens.AdminUsageScreen
import com.sub2api.mobile.ui.theme.Sub2ApiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UserRankingUiTest {
    @get:Rule val compose = createComposeRule()
    private val requests = mutableListOf<Pair<String, String>>()

    private fun showRanking(state: AppUiState = AppUiState()) {
        compose.setContent {
            Sub2ApiTheme {
                AdminUsageScreen(
                    state = state.copy(user = User(id = 1, role = "admin")),
                    onLoad = { _, _, _ -> }, onCancelRanking = {},
                    onRanking = { period, metric -> requests += period to metric },
                )
            }
        }
        compose.onNodeWithText("用户排行").performClick()
    }

    @Test
    fun defaultsToTodayAndOffersAllEightCalendarPresetsInADropdown() {
        showRanking()
        compose.onNodeWithText("统计范围：今天").assertIsDisplayed().performClick()
        RankingPeriod.entries.forEach { compose.onNodeWithText(it.label).assertExists() }
        compose.onNodeWithText("上月").performClick()
        compose.runOnIdle {
            assertEquals("today" to "total_tokens", requests.first())
            assertEquals("last_month" to "total_tokens", requests.last())
        }
    }

    @Test
    fun web24HourPresetExplainsDateAggregationAndShowsTheActualRange() {
        showRanking(AppUiState(rankingPeriod = "24h", ranking = UsageRanking(
            emptyList(), UsageDateRange("2026-11-05", "2026-11-06"), "Asia/Shanghai",
        )))
        compose.onNodeWithText("统计范围：近24小时").assertIsDisplayed()
        compose.onNodeWithText("这不是精确滚动的 24 小时", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2026-11-05 — 2026-11-06", substring = true).assertIsDisplayed()
    }
}
