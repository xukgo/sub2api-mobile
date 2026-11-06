package com.sub2api.mobile.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import java.util.UUID

internal fun createSessionPersistence(context: Context): SessionPersistence = try {
    EncryptedSessionPersistence(context.applicationContext)
} catch (_: Exception) {
    // Never fall back to writing credentials in plaintext. Keep existing files for recovery.
    object : SessionPersistence {
        override val error = "无法打开安全存储，请重启设备后重试。为保护账号，暂时无法保存服务或登录。"
        override fun read() = StoredSessions()
        override fun write(sessions: StoredSessions): Unit = throw IllegalStateException(error)
    }
}

internal class EncryptedSessionPersistence(private val context: Context) : SessionPersistence {
    private val gson = Gson()
    private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private fun encrypted(name: String) = EncryptedSharedPreferences.create(
        context, name, masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val prefs = encrypted("sub2api_profiles")
    private val initial: StoredSessions

    init {
        val saved = prefs.getString("sessions", null)
        if (saved != null) {
            initial = gson.fromJson(saved, StoredSessions::class.java)
        } else {
            val plaintext = context.getSharedPreferences("sub2api_session", Context.MODE_PRIVATE)
            val secure = try {
                encrypted("sub2api_secure").also { it.getString("server", null) }
            } catch (error: Exception) {
                if (plaintext.getString("server", null).isNullOrBlank()) throw error
                null
            }
            val legacy = if (!secure?.getString("server", null).isNullOrBlank()) secure!! else plaintext
            initial = migrateLegacySession(
                legacy.getString("server", "").orEmpty(),
                legacy.getString("access", "").orEmpty(),
                legacy.getString("refresh", "").orEmpty(),
                legacy.getLong("expires", 0),
            )
            write(initial)
        }
        // Migration is committed before removing old credentials. Retry cleanup on every start.
        listOf("sub2api_secure", "sub2api_session").forEach { name ->
            check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()) {
                "无法清理旧版登录凭据"
            }
        }
    }

    override fun read() = initial

    override fun write(sessions: StoredSessions) {
        check(prefs.edit().putString("sessions", gson.toJson(sessions)).commit()) { "无法安全保存服务配置" }
    }
}

internal fun migrateLegacySession(serverUrl: String, access: String, refresh: String, expiresAt: Long): StoredSessions {
    if (serverUrl.isBlank()) return StoredSessions()
    val url = ServerUrl.normalize(serverUrl)
    val profile = ServerProfile(
        id = UUID.randomUUID().toString(), name = java.net.URI(url).host, serverUrl = url,
        accessToken = access, refreshToken = refresh, expiresAt = expiresAt,
    )
    return StoredSessions(listOf(profile), profile.id)
}
