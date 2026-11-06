package com.sub2api.mobile.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.data.AiAccount
import com.sub2api.mobile.data.AiAccountQuota
import com.sub2api.mobile.data.AiQuotaMetric
import com.sub2api.mobile.data.ApiKey
import com.sub2api.mobile.data.DashboardStats
import com.sub2api.mobile.data.Group
import com.sub2api.mobile.data.PublicSettings
import com.sub2api.mobile.data.SubscriptionSummary
import com.sub2api.mobile.data.SubscriptionSummaryItem
import com.sub2api.mobile.data.User
import com.sub2api.mobile.ui.screens.AccountsScreen
import com.sub2api.mobile.ui.screens.DashboardScreen
import com.sub2api.mobile.ui.screens.ProfileScreen
import com.sub2api.mobile.ui.theme.Sub2ApiTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class UiVisualRegressionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dashboardRendersDenseSampleData() {
        val state = AppUiState(
            user = sampleUser(),
            dashboard = DashboardStats(
                todayRequests = 1_284,
                todayTokens = 4_820_000,
                todayActualCost = 12.4812,
                averageDurationMs = 846.0,
                rpm = 42.0,
            ),
            subscriptionSummary = SubscriptionSummary(
                activeCount = 1,
                subscriptions = listOf(
                    SubscriptionSummaryItem(id = 1, groupName = "生产共享池", status = "active", dailyProgress = 38.0, daysRemaining = 27),
                ),
            ),
            keys = listOf(
                ApiKey(id = 1, name = "生产环境", key = "sk-production-example-123456", status = "active"),
                ApiKey(id = 2, name = "数据分析", key = "sk-analytics-example-654321", status = "inactive"),
            ),
        )
        setScreen { DashboardScreen(state) }

        compose.onNodeWithText("可用余额").assertIsDisplayed()
        compose.onNodeWithText("生产共享池").assertIsDisplayed()
        saveScreenshot("ui-dashboard.png")
    }

    @Test
    fun accountsRendersSummaryAndQuotaCards() {
        val group = Group(id = 1, name = "共享号池2", platform = "openai")
        val accounts = listOf(
            AiAccount(
                id = 1,
                name = "OpenAI 共享账号",
                platform = "openai",
                type = "oauth",
                credentialsStatus = mapOf("has_access_token" to true, "has_refresh_token" to true),
                concurrency = 10,
                status = "active",
                groups = listOf(group),
                lastUsedAt = "2026-07-23T00:53:00Z",
            ),
            AiAccount(
                id = 2,
                name = "Claude 主账号",
                platform = "anthropic",
                type = "setup-token",
                credentialsStatus = mapOf("has_setup_token" to true),
                concurrency = 5,
                status = "active",
                groups = listOf(Group(id = 2, name = "Claude 池", platform = "anthropic")),
            ),
        )
        val quotas = mapOf(
            1L to AiAccountQuota(1, listOf(AiQuotaMetric("7d", 93.0), AiQuotaMetric("5h", 100.0))),
            2L to AiAccountQuota(2, listOf(AiQuotaMetric("7d", 54.0), AiQuotaMetric("5h", 78.0))),
        )
        val state = AppUiState(
            user = sampleUser(role = "admin"),
            aiAccounts = accounts,
            allAiAccounts = accounts,
            aiAccountQuotas = quotas,
            adminGroups = accounts.flatMap { it.groups },
        )
        setScreen {
            AccountsScreen(
                state = state,
                onFilter = { _, _, _ -> },
                onCreate = { _, _, _, _ -> },
                onUpdate = { _, _, _ -> },
                onTest = { _, _ -> },
                onClearError = {},
                onToggle = {},
                onDelete = {},
                onRefreshQuotas = { _, _, _ -> },
            )
        }

        compose.onNodeWithText("全部账号剩余额度").assertIsDisplayed()
        compose.onNodeWithText("OpenAI 共享账号").assertIsDisplayed()
        saveScreenshot("ui-accounts.png")
    }

    @Test
    fun profileRendersGroupedActions() {
        val state = AppUiState(
            user = sampleUser(),
            settings = PublicSettings(version = "0.96.0", paymentEnabled = true, docUrl = "https://docs.example.com"),
            serverInput = "https://api.example.com",
        )
        setScreen {
            ProfileScreen(
                state = state,
                onUpdateUsername = { _, _ -> },
                onLogout = {},
                onChangeServer = {},
                onOpenUrl = {},
                onOpenChannelStatus = {},
                onCheckForUpdate = {},
            )
        }

        compose.onNodeWithText("设计验证用户").assertIsDisplayed()
        compose.onNodeWithText("渠道状态").assertIsDisplayed()
        saveScreenshot("ui-profile.png")
    }

    private fun setScreen(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            Sub2ApiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    content = content,
                )
            }
        }
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val file = File(requireNotNull(directory), name)
        file.outputStream().use { stream ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        assertTrue("视觉回归截图应成功写入", file.length() > 1_000)
    }

    private fun sampleUser(role: String = "user") = User(
        id = 1,
        username = "设计验证用户",
        email = "designer@example.com",
        role = role,
        balance = 128.4512,
        concurrency = 8,
        rpmLimit = 2_400,
        status = "active",
        createdAt = "2026-06-01T08:00:00Z",
    )
}
