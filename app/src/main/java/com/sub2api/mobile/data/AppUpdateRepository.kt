package com.sub2api.mobile.data

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AppRelease(
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String?,
    val apkSha256: String? = null,
    val apkSizeBytes: Long? = null,
)

class AppUpdateRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .followRedirects(true)
        .build()
    private val releaseClient = client.newBuilder()
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val service: GitHubReleaseService = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(releaseClient)
        .addConverterFactory(
            GsonConverterFactory.create(
                GsonBuilder()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .create(),
            ),
        )
        .build()
        .create(GitHubReleaseService::class.java)

    suspend fun latestRelease(): AppRelease {
        val response = service.latestRelease()
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw IOException(
                when (response.code()) {
                    403 -> "GitHub 请求次数受限，请稍后重试"
                    404 -> "暂未找到正式版本"
                    else -> "GitHub 返回错误 (${response.code()})"
                },
            )
        }
        val release = response.body() ?: throw IOException("GitHub 返回了空响应")
        val version = release.tagName.trim().removePrefix("v").removePrefix("V")
        if (version.isBlank()) throw IOException("正式版本号无效")
        val apk = release.assets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) ||
                asset.contentType == "application/vnd.android.package-archive"
        }
        return AppRelease(
            versionName = version,
            title = release.name.ifBlank { "Sub2API Mobile v$version" },
            releaseNotes = releaseNotesForDisplay(release.body),
            releasePageUrl = release.htmlUrl.ifBlank {
                "https://github.com/startuip/sub2api-mobile/releases/tag/${release.tagName}"
            },
            apkDownloadUrl = apk?.browserDownloadUrl?.takeIf { it.isNotBlank() },
            apkSha256 = normalizeSha256Digest(apk?.digest),
            apkSizeBytes = apk?.size?.takeIf { it > 0L },
        )
    }

    suspend fun downloadApk(
        release: AppRelease,
        directory: File,
        onProgress: (Int?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val url = release.apkDownloadUrl ?: throw IOException("正式版本未提供 APK 下载文件")
        if (!directory.exists() && !directory.mkdirs()) throw IOException("无法创建更新下载目录")

        val safeVersion = release.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = File(directory, "sub2api-mobile-v$safeVersion.apk")
        val partial = File(directory, "${destination.name}.part")
        val expectedDigest = release.apkSha256

        if (destination.isFile && expectedDigest != null && sha256Hex(destination) == expectedDigest) {
            onProgress(100)
            return@withContext destination
        }
        destination.delete()
        partial.delete()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.android.package-archive")
            .header("User-Agent", "Sub2API-Mobile/${release.versionName}")
            .build()
        val response = client.newCall(request).execute()
        try {
            response.use {
                if (!it.isSuccessful) throw IOException("下载更新失败 (HTTP ${it.code})")
                val body = it.body ?: throw IOException("下载更新失败：服务器未返回文件")
                val totalBytes = body.contentLength().takeIf { length -> length > 0L } ?: release.apkSizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                var lastProgress: Int? = null
                onProgress(if (totalBytes != null) 0 else null)

                body.byteStream().use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            copied += count
                            val progress = totalBytes?.let { length ->
                                ((copied * 100L) / length).toInt().coerceIn(0, 99)
                            }
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }

                if (copied <= 0L) throw IOException("下载更新失败：APK 文件为空")
                release.apkSizeBytes?.let { expectedSize ->
                    if (copied != expectedSize) throw IOException("下载更新失败：APK 文件不完整")
                }
                val actualDigest = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                if (expectedDigest != null && actualDigest != expectedDigest) {
                    throw IOException("下载更新失败：APK 校验不通过")
                }
            }
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }

        if (destination.exists() && !destination.delete()) throw IOException("无法替换已下载的更新包")
        if (!partial.renameTo(destination)) {
            partial.copyTo(destination, overwrite = true)
            partial.delete()
        }
        directory.listFiles()
            ?.filter { it != destination && (it.extension == "apk" || it.extension == "part") }
            ?.forEach(File::delete)
        onProgress(100)
        destination
    }
}

internal fun normalizeSha256Digest(value: String?): String? {
    val digest = value?.trim()?.removePrefix("sha256:")?.lowercase() ?: return null
    return digest.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
}

internal fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

internal fun isVersionNewer(latest: String, current: String): Boolean {
    val latestVersion = SemanticVersion.parse(latest) ?: return false
    val currentVersion = SemanticVersion.parse(current) ?: return false
    return latestVersion > currentVersion
}

internal fun releaseNotesForDisplay(markdown: String): String {
    val notes = markdown.lineSequence()
        .map { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#") -> trimmed.trimStart('#').trim()
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> "• ${trimmed.drop(2).trim()}"
                else -> trimmed
            }.replace("`", "")
        }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
    return notes.ifBlank { "本次版本未提供更新说明。" }.take(4_000)
}

private data class SemanticVersion(
    val numbers: List<Int>,
    val preRelease: List<String>?,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        repeat(maxOf(numbers.size, other.numbers.size)) { index ->
            val comparison = (numbers.getOrNull(index) ?: 0).compareTo(other.numbers.getOrNull(index) ?: 0)
            if (comparison != 0) return comparison
        }
        if (preRelease == null && other.preRelease != null) return 1
        if (preRelease != null && other.preRelease == null) return -1
        if (preRelease == null || other.preRelease == null) return 0
        repeat(maxOf(preRelease.size, other.preRelease.size)) { index ->
            val left = preRelease.getOrNull(index) ?: return -1
            val right = other.preRelease.getOrNull(index) ?: return 1
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right, ignoreCase = true)
            }
            if (comparison != 0) return comparison
        }
        return 0
    }

    companion object {
        private val pattern = Regex(
            "^[vV]?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$",
        )

        fun parse(raw: String): SemanticVersion? {
            val match = pattern.matchEntire(raw.trim()) ?: return null
            val numbers = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
            val preRelease = match.groupValues[2].takeIf { it.isNotBlank() }?.split('.')
            return SemanticVersion(numbers, preRelease)
        }
    }
}

private interface GitHubReleaseService {
    @Headers(
        "Accept: application/vnd.github+json",
        "X-GitHub-Api-Version: 2022-11-28",
    )
    @GET("repos/startuip/sub2api-mobile/releases/latest")
    suspend fun latestRelease(): Response<GitHubReleaseResponse>
}

private data class GitHubReleaseResponse(
    val tagName: String = "",
    val name: String = "",
    val body: String = "",
    val htmlUrl: String = "",
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

private data class GitHubReleaseAsset(
    val name: String = "",
    val contentType: String = "",
    val browserDownloadUrl: String = "",
    val digest: String = "",
    val size: Long = 0L,
)
