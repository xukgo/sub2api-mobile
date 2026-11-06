package com.sub2api.mobile.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

internal fun canInstallApks(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

internal fun unknownSourcesSettingsIntent(context: Context): Intent = Intent(
    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
    Uri.parse("package:${context.packageName}"),
)

internal fun apkInstallIntent(context: Context, apkPath: String): Intent {
    val apk = File(apkPath)
    require(apk.isFile && apk.length() > 0L) { "更新包不存在，请重新下载" }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apk,
    )
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

internal fun launchApkInstaller(context: Context, apkPath: String): Result<Unit> = runCatching {
    context.startActivity(apkInstallIntent(context, apkPath))
}
