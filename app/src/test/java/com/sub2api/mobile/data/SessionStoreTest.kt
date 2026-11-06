package com.sub2api.mobile.data

import org.junit.Assert.*
import org.junit.Test

internal class MemorySessionPersistence(var saved: StoredSessions = StoredSessions()) : SessionPersistence {
    override fun read() = saved
    override fun write(sessions: StoredSessions) { saved = sessions }
}

class SessionStoreTest {
    @Test
    fun retainsIndependentLoginsAndRestoresLastSelectedServer() {
        val disk = MemorySessionPersistence()
        val store = SessionStore(disk)
        val a = store.saveServer(null, "家庭", "home.example.com")
        store.setTokens(store.state.value, "access-a", "refresh-a", 3600, email = "a@example.com")
        val b = store.saveServer(null, "云端", "cloud.example.com")
        store.selectServer(b)
        store.setTokens(store.state.value, "access-b", "refresh-b", 3600, email = "b@example.com")
        store.selectServer(a)
        assertEquals("access-a", store.state.value.accessToken)
        store.selectServer(b)
        val restored = SessionStore(disk)
        assertEquals(b, restored.state.value.profileId)
        assertEquals("access-b", restored.state.value.accessToken)
        assertTrue(restored.profiles.value.all { it.isAuthenticated })
        assertEquals(listOf("a@example.com", "b@example.com"), restored.profiles.value.map { it.email })
    }

    @Test
    fun logoutAndDeletionOnlyAffectTheirOwnServerAndRejectLateRefresh() {
        val store = SessionStore(MemorySessionPersistence())
        val a = store.saveServer(null, "A", "a.example.com")
        store.setTokens(store.state.value, "a", "ra", 60)
        val oldA = store.state.value
        val b = store.saveServer(null, "B", "b.example.com")
        store.selectServer(b)
        store.setTokens(store.state.value, "b", "rb", 60)
        store.clearTokens(oldA)
        assertEquals("b", store.state.value.accessToken)
        assertFalse(store.setTokens(oldA, "late", "late-refresh", 60))
        store.selectServer(a)
        assertFalse(store.state.value.isAuthenticated)
        store.removeServer(a)
        assertEquals(b, store.state.value.profileId)
        assertEquals("b", store.state.value.accessToken)
        assertFalse(store.setTokens(oldA, "late", "late-refresh", 60))
        store.removeServer(b)
        assertFalse(store.state.value.isConfigured)
    }

    @Test
    fun renamingPreservesLoginButChangingAddressInvalidatesIt() {
        val store = SessionStore(MemorySessionPersistence())
        val a = store.saveServer(null, "A", "a.example.com")
        store.setTokens(store.state.value, "a", "ra", 60)
        val original = store.state.value
        store.saveServer(a, "新名称", "https://a.example.com/api/v1/")
        assertEquals("a", store.state.value.accessToken)
        assertEquals("新名称", store.state.value.serverName)
        store.saveServer(a, "新名称", "new.example.com")
        assertFalse(store.state.value.isAuthenticated)
        assertNull(store.sessionFor(original))
        assertFalse(store.setTokens(original, "late", "late", 60))
    }

    @Test
    fun switchingAwayAndBackRejectsOldUiAndWidgetResults() {
        val store = SessionStore(MemorySessionPersistence())
        val a = store.saveServer(null, "A", "a.example.com")
        val oldA = store.state.value
        val b = store.saveServer(null, "B", "b.example.com")
        store.selectServer(b)
        store.selectServer(a)
        var published = false
        assertFalse(store.whileActive(oldA) { published = true })
        assertFalse(published)
        assertNotNull(store.sessionFor(oldA)) // Its credentials still belong to A.
    }

    @Test
    fun rejectsDuplicateNormalizedAddresses() {
        val store = SessionStore(MemorySessionPersistence())
        store.saveServer(null, "A", "a.example.com")
        assertThrows(IllegalArgumentException::class.java) {
            store.saveServer(null, "Duplicate", "https://a.example.com/api/v1/")
        }
        assertEquals(1, store.profiles.value.size)
    }

    @Test
    fun refreshCannotReplaceOrClearNewerCredentials() {
        val store = SessionStore(MemorySessionPersistence())
        store.saveServer(null, "A", "a.example.com")
        store.setTokens(store.state.value, "first", "r1", 60)
        val initial = store.state.value
        store.setTokens(initial, "second", "r2", 60)
        assertFalse(store.setTokens(initial, "late", "r3", 60, expectedRefreshToken = "r1"))
        store.clearTokens(initial, expectedRefreshToken = "r1")
        assertEquals("second", store.state.value.accessToken)
    }

    @Test
    fun failedSecureWriteDoesNotChangeLiveSelectionOrTokens() {
        val migrated = migrateLegacySession("old.example.com", "access", "refresh", 1234)
        val store = SessionStore(object : SessionPersistence {
            override fun read() = migrated
            override fun write(sessions: StoredSessions) { error("secure storage unavailable") }
        })
        assertThrows(IllegalStateException::class.java) { store.clearTokens(store.state.value) }
        assertEquals("access", store.state.value.accessToken)
        assertEquals(1, store.profiles.value.size)
    }

    @Test
    fun migratesLegacyServerAndTokensWithoutChangingTheirValues() {
        val migrated = migrateLegacySession("https://old.example.com/api/v1/", "access", "refresh", 1234)
        val store = SessionStore(MemorySessionPersistence(migrated))
        assertEquals("old.example.com", store.state.value.serverName)
        assertEquals("https://old.example.com/api/v1/", store.state.value.serverUrl)
        assertEquals("access", store.state.value.accessToken)
        assertEquals("refresh", store.state.value.refreshToken)
        assertEquals(1234L, store.state.value.expiresAt)
        assertTrue(migrateLegacySession("", "", "", 0).profiles.isEmpty())
    }
}
