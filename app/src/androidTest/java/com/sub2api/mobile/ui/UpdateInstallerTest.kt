package com.sub2api.mobile.ui

import android.content.Intent
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateInstallerTest {
    @Test
    fun createsPrivateFileProviderInstallIntent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "updates").apply { mkdirs() }
        val apk = File(directory, "test-update.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            val intent = apkInstallIntent(context, apk.absolutePath)

            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertEquals("content", intent.data?.scheme)
            assertEquals("${context.packageName}.fileprovider", intent.data?.authority)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        } finally {
            apk.delete()
        }
    }

    @Test
    fun opensPerAppUnknownSourcesSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = unknownSourcesSettingsIntent(context)

        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
    }
}
