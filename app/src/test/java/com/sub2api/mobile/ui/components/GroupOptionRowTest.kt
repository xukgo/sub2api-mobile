package com.sub2api.mobile.ui.components

import com.sub2api.mobile.data.Group
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupOptionRowTest {
    @Test
    fun explicitProviderIsUsedEvenWhenGroupNameMentionsAnotherProvider() {
        assertEquals(GroupBrand.CLAUDE, groupBrand(Group(name = "GPT 对比 Claude", platform = "anthropic")))
        assertEquals(GroupBrand.DEEPSEEK, groupBrand(Group(name = "主线路", platform = "deepseek")))
        assertEquals(GroupBrand.GROK, groupBrand(Group(name = "专属线路", platform = "grok")))
        assertEquals(GroupBrand.GEMINI, groupBrand(Group(name = "主线路", platform = "gemini")))
    }

    @Test
    fun openAiCompatibleGroupsCanShowTheirOwnProviderBrand() {
        assertEquals(GroupBrand.OPENAI, groupBrand(Group(name = "GPT - Cat Wu", platform = "openai")))
        assertEquals(GroupBrand.DEEPSEEK, groupBrand(Group(name = "DeepSeek V3", platform = "openai")))
        assertEquals(GroupBrand.GROK, groupBrand(Group(name = "Grok", platform = "openai")))
        assertEquals(GroupBrand.CLAUDE, groupBrand(Group(name = "Cursor Claude+")))
        assertEquals(GroupBrand.OTHER, groupBrand(Group(name = "共享分组", platform = "custom")))
    }

    @Test
    fun ratesKeepServerPrecisionWithoutTrailingZerosOrLocaleChanges() {
        assertEquals("0.17x 倍率", groupRateLabel(0.17))
        assertEquals("0.12345x 倍率", groupRateLabel(0.12345))
        assertEquals("1x 倍率", groupRateLabel(1.0))
        assertEquals("0x 倍率", groupRateLabel(0.0))
        assertEquals("倍率未知", groupRateLabel(Double.NaN))
        assertEquals("倍率未知", groupRateLabel(-1.0))
    }
}
