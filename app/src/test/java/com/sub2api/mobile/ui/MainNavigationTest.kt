package com.sub2api.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationTest {
    @Test
    fun adminGetsAiAccountsInsteadOfBottomSubscriptionTab() {
        val tabs = mainTabsForRole("admin")

        assertEquals(
            listOf(MainTab.DASHBOARD, MainTab.ACCOUNTS, MainTab.USAGE, MainTab.ALL_USAGE, MainTab.PROFILE),
            tabs,
        )
        assertTrue(MainTab.ACCOUNTS in tabs)
        assertTrue(MainTab.ALL_USAGE in tabs)
        assertFalse(MainTab.SUBSCRIPTIONS in tabs)
        assertFalse(MainTab.CHANNEL_STATUS in tabs)
    }

    @Test
    fun regularUserNeverGetsAiAccountsTab() {
        val tabs = mainTabsForRole("user")

        assertEquals(
            listOf(MainTab.DASHBOARD, MainTab.KEYS, MainTab.USAGE, MainTab.SUBSCRIPTIONS, MainTab.PROFILE),
            tabs,
        )
        assertFalse(MainTab.ACCOUNTS in tabs)
        assertFalse(MainTab.ALL_USAGE in tabs)
        assertFalse(MainTab.CHANNEL_STATUS in tabs)
    }
}
