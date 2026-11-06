package com.sub2api.mobile.data

import android.content.Context
import android.content.ContextWrapper
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SessionMigrationTest {
    @Test
    fun migratesEncryptedLegacySessionAndRemovesOldCredentials() = withIsolatedPreferences { context ->
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val legacy = EncryptedSharedPreferences.create(
            context, "sub2api_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        assertTrue(legacy.edit().putString("server", "https://home.example.com/api/v1/")
            .putString("access", "legacy-access").putString("refresh", "legacy-refresh")
            .putLong("expires", 1234).commit())

        val store = SessionStore(context)
        assertNull(store.storageError)
        assertEquals("legacy-access", store.state.value.accessToken)
        assertEquals("legacy-refresh", store.state.value.refreshToken)
        assertEquals(1234L, store.state.value.expiresAt)
        assertTrue(context.getSharedPreferences("sub2api_secure", Context.MODE_PRIVATE).all.isEmpty())
        assertEquals("legacy-access", SessionStore(context).state.value.accessToken)
    }

    @Test
    fun movesPlaintextLegacyCredentialsIntoEncryptedStore() = withIsolatedPreferences { context ->
        val plaintext = context.getSharedPreferences("sub2api_session", Context.MODE_PRIVATE)
        assertTrue(plaintext.edit().putString("server", "https://cloud.example.com/api/v1/")
            .putString("access", "legacy-plain-access").putString("refresh", "legacy-plain-refresh").commit())

        val store = SessionStore(context)
        assertNull(store.storageError)
        assertEquals("legacy-plain-access", store.state.value.accessToken)
        assertTrue(plaintext.all.isEmpty())
        val raw = context.getSharedPreferences("sub2api_profiles", Context.MODE_PRIVATE)
        assertFalse(raw.all.values.any { it.toString().contains("legacy-plain-access") })
        assertEquals("legacy-plain-refresh", SessionStore(context).state.value.refreshToken)
    }

    private fun withIsolatedPreferences(block: (Context) -> Unit) {
        val prefix = "migration-test-${UUID.randomUUID()}-"
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(prefix + name, mode)
            override fun deleteSharedPreferences(name: String) = super.deleteSharedPreferences(prefix + name)
        }
        try {
            block(context)
        } finally {
            listOf("sub2api_secure", "sub2api_session", "sub2api_profiles").forEach { context.deleteSharedPreferences(it) }
        }
    }
}
