package com.sub2api.mobile.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

// A revision invalidates work started before logout or an address change.
data class SessionState(
    val profileId: String = "",
    val revision: Long = 0,
    val selectionVersion: Long = 0,
    val serverName: String = "",
    val serverUrl: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0,
) {
    val isConfigured: Boolean get() = profileId.isNotBlank() && serverUrl.isNotBlank()
    val isAuthenticated: Boolean get() = accessToken.isNotBlank()
}

data class ServerSummary(
    val id: String,
    val name: String,
    val serverUrl: String,
    val email: String,
    val isAuthenticated: Boolean,
)

internal data class ServerProfile(
    val id: String,
    val name: String,
    val serverUrl: String,
    val email: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0,
    val revision: Long = 0,
) {
    fun session(selectionVersion: Long) = SessionState(
        id, revision, selectionVersion, name, serverUrl, accessToken, refreshToken, expiresAt,
    )
    fun summary() = ServerSummary(id, name, serverUrl, email, accessToken.isNotBlank())
}

internal data class StoredSessions(
    val profiles: List<ServerProfile> = emptyList(),
    val activeProfileId: String? = null,
)

internal interface SessionPersistence {
    val error: String? get() = null
    fun read(): StoredSessions
    fun write(sessions: StoredSessions)
}

class SessionStore internal constructor(private val persistence: SessionPersistence) {
    constructor(context: Context) : this(createSessionPersistence(context))

    private var sessions = persistence.read()
    private var selectionVersion = 0L
    private val _state = MutableStateFlow(activeSession())
    val state: StateFlow<SessionState> = _state.asStateFlow()
    private val _profiles = MutableStateFlow(sessions.profiles.map { it.summary() })
    val profiles: StateFlow<List<ServerSummary>> = _profiles.asStateFlow()
    val storageError: String? get() = persistence.error

    @Synchronized
    fun saveServer(id: String?, name: String, serverUrl: String): String {
        val normalized = ServerUrl.normalize(serverUrl)
        require(name.isNotBlank()) { "请输入服务名称" }
        require(sessions.profiles.none { it.id != id && it.serverUrl == normalized }) { "该服务地址已存在" }
        val old = id?.let { key -> sessions.profiles.firstOrNull { it.id == key } }
        require(id == null || old != null) { "服务已被删除" }
        val profile = when {
            old == null -> ServerProfile(UUID.randomUUID().toString(), name.trim(), normalized)
            old.serverUrl != normalized -> ServerProfile(old.id, name.trim(), normalized, revision = old.revision + 1)
            else -> old.copy(name = name.trim())
        }
        val profiles = if (old == null) sessions.profiles + profile else sessions.profiles.map { if (it.id == id) profile else it }
        publish(sessions.copy(profiles = profiles, activeProfileId = sessions.activeProfileId ?: profile.id))
        return profile.id
    }

    @Synchronized
    fun selectServer(id: String) {
        require(sessions.profiles.any { it.id == id }) { "服务已被删除" }
        if (sessions.activeProfileId != id) publish(sessions.copy(activeProfileId = id))
    }

    @Synchronized
    fun removeServer(id: String) {
        val remaining = sessions.profiles.filterNot { it.id == id }
        publish(StoredSessions(remaining, sessions.activeProfileId.takeIf { it != id } ?: remaining.firstOrNull()?.id))
    }

    @Synchronized
    fun sessionFor(expected: SessionState): SessionState? = sessions.profiles
        .firstOrNull { it.id == expected.profileId && it.serverUrl == expected.serverUrl && it.revision == expected.revision }
        ?.session(expected.selectionVersion)

    @Synchronized
    fun isActive(expected: SessionState): Boolean = state.value.let {
        it.profileId == expected.profileId && it.revision == expected.revision &&
            it.serverUrl == expected.serverUrl && it.selectionVersion == expected.selectionVersion
    }

    @Synchronized
    fun setTokens(
        expected: SessionState,
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long?,
        expectedRefreshToken: String? = null,
        email: String? = null,
    ): Boolean {
        val current = sessionFor(expected) ?: return false
        if (expectedRefreshToken != null && current.refreshToken != expectedRefreshToken) return false
        val expiresAt = expiresInSeconds?.takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 1000 } ?: 0
        publish(sessions.copy(profiles = sessions.profiles.map { profile ->
            if (profile.id != expected.profileId) profile else profile.copy(
                accessToken = accessToken,
                refreshToken = refreshToken?.takeIf { it.isNotBlank() } ?: profile.refreshToken,
                expiresAt = expiresAt,
                email = email ?: profile.email,
            )
        }))
        return true
    }

    @Synchronized
    fun setAccount(expected: SessionState, email: String) {
        if (sessionFor(expected) == null) return
        val profile = sessions.profiles.first { it.id == expected.profileId }
        if (profile.email == email) return
        publish(sessions.copy(profiles = sessions.profiles.map { if (it.id == profile.id) it.copy(email = email) else it }))
    }

    @Synchronized
    fun clearTokens(expected: SessionState, expectedRefreshToken: String? = null) {
        val current = sessionFor(expected) ?: return
        if (expectedRefreshToken != null && current.refreshToken != expectedRefreshToken) return
        publish(sessions.copy(profiles = sessions.profiles.map { profile ->
            if (profile.id != expected.profileId) profile else profile.copy(
                accessToken = "", refreshToken = "", expiresAt = 0, revision = profile.revision + 1,
            )
        }))
    }

    @Synchronized
    internal fun whileActive(expected: SessionState, action: () -> Unit): Boolean {
        if (!isActive(expected)) return false
        action()
        return true
    }

    private fun activeSession(): SessionState = sessions.profiles
        .firstOrNull { it.id == sessions.activeProfileId }?.session(selectionVersion) ?: SessionState(selectionVersion = selectionVersion)

    private fun publish(next: StoredSessions) {
        persistence.write(next)
        val old = activeSession()
        sessions = next
        if (old.profileId != next.activeProfileId || next.profiles.firstOrNull { it.id == next.activeProfileId }?.revision != old.revision) {
            selectionVersion++
        }
        _profiles.value = next.profiles.map { it.summary() }
        _state.value = activeSession()
    }
}

object ServerUrl {
    fun normalize(input: String): String {
        var value = input.trim()
        require(value.isNotEmpty()) { "请输入服务器地址" }
        val explicitScheme = Regex("^([a-z][a-z0-9+.-]*)://", RegexOption.IGNORE_CASE).find(value)?.groupValues?.get(1)
        require(explicitScheme == null || explicitScheme.equals("http", true) || explicitScheme.equals("https", true)) {
            "仅支持 HTTP 或 HTTPS 地址"
        }
        if (explicitScheme == null) {
            value = "https://$value"
        }
        val parsed = runCatching { java.net.URI(value) }.getOrNull()
        require(parsed?.host?.isNotBlank() == true) { "服务器地址格式不正确" }
        require(parsed.scheme.equals("http", true) || parsed.scheme.equals("https", true)) {
            "仅支持 HTTP 或 HTTPS 地址"
        }
        require(parsed.query == null && parsed.fragment == null) { "服务器地址不能包含查询参数或片段" }
        require(parsed.rawUserInfo == null) { "服务器地址不能包含用户名或密码" }
        require(parsed.port in -1..65535) { "服务器端口无效" }
        val scheme = parsed.scheme.lowercase(java.util.Locale.ROOT)
        val host = parsed.host.lowercase(java.util.Locale.ROOT)
        val port = parsed.port.takeUnless { it == -1 || scheme == "https" && it == 443 || scheme == "http" && it == 80 }
        value = "$scheme://$host${port?.let { ":$it" }.orEmpty()}${parsed.rawPath.orEmpty()}".trimEnd('/')
        return if (value.endsWith("/api/v1")) "$value/" else "$value/api/v1/"
    }

    fun webRoot(apiUrl: String): String = apiUrl.trimEnd('/').removeSuffix("/api/v1")
}
