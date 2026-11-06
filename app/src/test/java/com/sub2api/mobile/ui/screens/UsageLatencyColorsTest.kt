package com.sub2api.mobile.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class UsageLatencyColorsTest {
    @Test
    fun missingAndInvalidTimingKeepNeutralBackgroundAndSlowRequestsStayRed() {
        val surface = Color.White
        assertEquals(surface, firstTokenCardColor(null, surface))
        assertEquals(surface, firstTokenCardColor(-1, surface))
        assertEquals(firstTokenCardColor(40_000, surface), firstTokenCardColor(40_001, surface))
        assertEquals(firstTokenCardColor(40_000, surface), firstTokenCardColor(Long.MAX_VALUE, surface))
        val fast = firstTokenCardColor(0, surface)
        assertTrue(fast.green > fast.red)
        val slow = firstTokenCardColor(40_000, surface)
        assertTrue(slow.red > slow.green && slow.red > slow.blue)
    }

    @Test
    fun everyIntervalChangesGraduallyWithoutJumpsAtThresholds() {
        val surface = Color.White
        for ((from, to) in listOf(0L to 5_000L, 5_000L to 10_000L, 10_000L to 20_000L, 20_000L to 40_000L)) {
            val middle = firstTokenCardColor((from + to) / 2, surface)
            assertNotEquals(firstTokenCardColor(from, surface), middle)
            assertNotEquals(firstTokenCardColor(to, surface), middle)
            val left = firstTokenCardColor(to - 1, surface)
            val right = firstTokenCardColor(to + 1, surface)
            assertTrue(abs(left.red - right.red) < 0.001f)
            assertTrue(abs(left.green - right.green) < 0.001f)
            assertTrue(abs(left.blue - right.blue) < 0.001f)
        }
    }

    @Test
    fun bodyAndSecondaryTextRemainReadableInBothThemesAcrossTheWholeRange() {
        val themes = listOf(
            Triple(Color.White, Color(0xFF17201C), Color(0xFF4E5953)),
            Triple(Color(0xFF151C18), Color(0xFFE2EAE4), Color(0xFFBEC9C2)),
        )
        for ((surface, body, secondary) in themes) {
            for (ms in 0L..45_000L step 250L) {
                val background = firstTokenCardColor(ms, surface)
                for (text in listOf(body, secondary)) {
                    val a = background.luminance()
                    val b = text.luminance()
                    val contrast = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
                    assertTrue("Text contrast at $ms ms: $contrast", contrast >= 4.5f)
                }
            }
        }
    }
}
