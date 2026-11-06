package com.sub2api.mobile.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.UpdateDownloadState
import com.sub2api.mobile.UpdateDownloadStatus
import com.sub2api.mobile.data.AppRelease
import com.sub2api.mobile.data.User
import com.sub2api.mobile.ui.components.AppUpdateDialog
import com.sub2api.mobile.ui.screens.ProfileScreen
import com.sub2api.mobile.ui.theme.Sub2ApiTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppUpdateUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun profileOffersManualUpdateCheck() {
        var checked = false
        compose.setContent {
            MaterialTheme {
                ProfileScreen(
                    state = AppUiState(
                        user = User(username = "测试用户", email = "test@example.com"),
                        serverInput = "https://api.example.com",
                    ),
                    onUpdateUsername = { _, _ -> },
                    onLogout = {},
                    onChangeServer = {},
                    onOpenUrl = {},
                    onOpenChannelStatus = {},
                    onCheckForUpdate = { checked = true },
                )
            }
        }

        compose.onNodeWithText("检查更新").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(checked) }
    }

    @Test
    fun updateDialogShowsReleaseNotesAndDownloadAction() {
        var downloadRequested = false
        val release = AppRelease(
            versionName = "1.3.0",
            title = "Sub2API Mobile v1.3.0",
            releaseNotes = "• 启动时自动检查更新\n• 支持手动检查更新",
            releasePageUrl = "https://github.com/startuip/sub2api-mobile/releases/tag/v1.3.0",
            apkDownloadUrl = "https://example.com/sub2api-mobile-v1.3.0.apk",
        )
        compose.setContent {
            Sub2ApiTheme {
                AppUpdateDialog(
                    release = release,
                    download = UpdateDownloadState(),
                    onDismiss = {},
                    onDownload = { downloadRequested = true },
                    onInstall = {},
                    onOpenReleasePage = {},
                )
            }
        }

        compose.onNodeWithText("发现新版本 v1.3.0").assertIsDisplayed()
        compose.onNodeWithText("更新日志").assertIsDisplayed()
        compose.onNodeWithText("• 启动时自动检查更新\n• 支持手动检查更新").assertIsDisplayed()
        compose.onNodeWithText("应用内下载").performClick()
        compose.runOnIdle { assertTrue(downloadRequested) }
    }

    @Test
    fun downloadedUpdateOffersInstallAction() {
        var installedPath: String? = null
        val release = AppRelease(
            versionName = "1.5.0",
            title = "Sub2API Mobile v1.5.0",
            releaseNotes = "内置更新下载器",
            releasePageUrl = "https://github.com/startuip/sub2api-mobile/releases/tag/v1.5.0",
            apkDownloadUrl = "https://example.com/sub2api-mobile-v1.5.0.apk",
        )
        compose.setContent {
            Sub2ApiTheme {
                AppUpdateDialog(
                    release = release,
                    download = UpdateDownloadState(
                        status = UpdateDownloadStatus.READY,
                        progressPercent = 100,
                        apkPath = "/data/user/0/com.sub2api.mobile/files/updates/update.apk",
                    ),
                    onDismiss = {},
                    onDownload = {},
                    onInstall = { installedPath = it },
                    onOpenReleasePage = {},
                )
            }
        }

        compose.onNodeWithText("更新包已下载并通过校验，可以开始安装。").assertIsDisplayed()
        compose.onNodeWithText("安装更新").performClick()
        compose.runOnIdle { assertTrue(installedPath?.endsWith("update.apk") == true) }
    }

    @Test
    fun updateDialogShowsInternalDownloadProgress() {
        val release = AppRelease(
            versionName = "1.5.0",
            title = "Sub2API Mobile v1.5.0",
            releaseNotes = "• 内置下载器\n• 下载完成后直接安装",
            releasePageUrl = "https://github.com/startuip/sub2api-mobile/releases/tag/v1.5.0",
            apkDownloadUrl = "https://example.com/sub2api-mobile-v1.5.0.apk",
        )
        compose.setContent {
            Sub2ApiTheme {
                AppUpdateDialog(
                    release = release,
                    download = UpdateDownloadState(
                        status = UpdateDownloadStatus.DOWNLOADING,
                        progressPercent = 42,
                    ),
                    onDismiss = {},
                    onDownload = {},
                    onInstall = {},
                    onOpenReleasePage = {},
                )
            }
        }

        compose.onNodeWithText("正在应用内下载 42%").assertIsDisplayed()
        compose.onNodeWithText("下载中 42%").assertIsDisplayed()
        saveScreenshot("ui-update-download.png")
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir(null)
        val file = File(requireNotNull(directory), name)
        file.outputStream().use { stream ->
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        assertTrue("视觉回归截图应成功写入", file.length() > 1_000)
    }
}
