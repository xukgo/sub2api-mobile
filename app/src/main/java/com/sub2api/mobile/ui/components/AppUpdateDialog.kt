package com.sub2api.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sub2api.mobile.BuildConfig
import com.sub2api.mobile.UpdateDownloadState
import com.sub2api.mobile.UpdateDownloadStatus
import com.sub2api.mobile.data.AppRelease

@Composable
fun AppUpdateDialog(
    release: AppRelease,
    download: UpdateDownloadState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (String) -> Unit,
    onOpenReleasePage: (String) -> Unit,
) {
    val hasApk = release.apkDownloadUrl != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("发现新版本 v${release.versionName}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "当前版本 ${BuildConfig.VERSION_NAME}  ·  最新版本 ${release.versionName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (download.status) {
                    UpdateDownloadStatus.IDLE -> Unit
                    UpdateDownloadStatus.DOWNLOADING -> {
                        Text(
                            download.progressPercent?.let { "正在应用内下载 $it%" } ?: "正在连接下载服务",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ContinuousLinearProgressIndicator(
                            progress = { (download.progressPercent ?: 0) / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                    UpdateDownloadStatus.READY -> Text(
                        "更新包已下载并通过校验，可以开始安装。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    UpdateDownloadStatus.FAILED -> Text(
                        download.error ?: "下载失败，请重试",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text("更新日志", fontWeight = FontWeight.SemiBold)
                Text(release.releaseNotes, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        !hasApk -> onOpenReleasePage(release.releasePageUrl)
                        download.status == UpdateDownloadStatus.READY -> download.apkPath?.let(onInstall)
                        download.status != UpdateDownloadStatus.DOWNLOADING -> onDownload()
                    }
                },
                enabled = download.status != UpdateDownloadStatus.DOWNLOADING &&
                    (download.status != UpdateDownloadStatus.READY || download.apkPath != null),
            ) {
                Text(
                    when {
                        !hasApk -> "查看发布页"
                        download.status == UpdateDownloadStatus.DOWNLOADING -> download.progressPercent?.let { "下载中 $it%" } ?: "连接中"
                        download.status == UpdateDownloadStatus.READY -> "安装更新"
                        download.status == UpdateDownloadStatus.FAILED -> "重试下载"
                        else -> "应用内下载"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (download.status == UpdateDownloadStatus.DOWNLOADING) "后台下载" else "稍后")
            }
        },
    )
}
