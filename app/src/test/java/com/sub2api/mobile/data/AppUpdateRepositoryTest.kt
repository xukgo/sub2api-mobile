package com.sub2api.mobile.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AppUpdateRepositoryTest {
    @Test
    fun comparesNumericVersionSegments() {
        assertTrue(isVersionNewer("v1.10.0", "1.9.9"))
        assertTrue(isVersionNewer("2.0", "1.99.99"))
        assertFalse(isVersionNewer("1.2.0", "1.2"))
        assertFalse(isVersionNewer("1.1.9", "1.2.0"))
    }

    @Test
    fun stableReleaseIsNewerThanPrerelease() {
        assertTrue(isVersionNewer("1.3.0", "1.3.0-rc.1"))
        assertFalse(isVersionNewer("1.3.0-beta.2", "1.3.0"))
        assertTrue(isVersionNewer("1.3.0-rc.10", "1.3.0-rc.2"))
    }

    @Test
    fun rejectsUnrecognizedTags() {
        assertFalse(isVersionNewer("latest", "1.2.0"))
    }

    @Test
    fun formatsMarkdownReleaseNotesForPlainTextDialog() {
        val formatted = releaseNotesForDisplay(
            """
            ## 更新内容

            - 自动检查更新
            * 手动检查更新
            """.trimIndent(),
        )

        assertEquals("更新内容\n\n• 自动检查更新\n• 手动检查更新", formatted)
    }

    @Test
    fun normalizesAndValidatesReleaseDigest() {
        val file = File.createTempFile("sub2api-update", ".apk")
        try {
            file.writeText("verified update")
            val digest = sha256Hex(file)

            assertEquals(64, digest.length)
            assertEquals(digest, normalizeSha256Digest("sha256:${digest.uppercase()}"))
            assertEquals(null, normalizeSha256Digest("sha256:not-a-digest"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun downloadsApkWithProgressAndDigestVerification() = runBlocking {
        val payload = ByteArray(96 * 1024) { index -> (index % 251).toByte() }
        val source = Files.createTempFile("sub2api-source", ".apk").toFile().apply { writeBytes(payload) }
        val directory = Files.createTempDirectory("sub2api-updates").toFile()
        val server = MockWebServer().apply {
            enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/vnd.android.package-archive")
                    .setBody(Buffer().write(payload)),
            )
            start()
        }
        try {
            val progress = mutableListOf<Int?>()
            val downloaded = AppUpdateRepository().downloadApk(
                release = AppRelease(
                    versionName = "9.9.9",
                    title = "Test release",
                    releaseNotes = "Test",
                    releasePageUrl = server.url("/").toString(),
                    apkDownloadUrl = server.url("/update.apk").toString(),
                    apkSha256 = sha256Hex(source),
                    apkSizeBytes = payload.size.toLong(),
                ),
                directory = directory,
                onProgress = progress::add,
            )

            assertArrayEquals(payload, downloaded.readBytes())
            assertEquals(0, progress.first())
            assertEquals(100, progress.last())
            assertTrue(progress.filterNotNull().zipWithNext().all { (left, right) -> right >= left })
            assertFalse(directory.listFiles().orEmpty().any { it.extension == "part" })
        } finally {
            server.shutdown()
            source.delete()
            directory.deleteRecursively()
        }
    }
}
