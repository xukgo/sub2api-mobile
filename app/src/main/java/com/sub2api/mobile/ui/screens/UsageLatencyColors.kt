package com.sub2api.mobile.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

// Interpolate by TTFT between the user's thresholds, then blend into the theme surface.
private val firstTokenColors = listOf(
    0L to Color(0xFF81C784),
    5_000L to Color(0xFFFFD54F),
    10_000L to Color(0xFFFF9800),
    20_000L to Color(0xFFEF9A9A),
    40_000L to Color(0xFFF44336),
)

internal fun firstTokenCardColor(firstTokenMs: Long?, surface: Color): Color {
    if (firstTokenMs == null || firstTokenMs < 0) return surface
    val upper = firstTokenColors.indexOfFirst { firstTokenMs <= it.first }
    val tint = when {
        upper < 0 -> firstTokenColors.last().second
        upper == 0 -> firstTokenColors.first().second
        else -> {
            val (start, from) = firstTokenColors[upper - 1]
            val (end, to) = firstTokenColors[upper]
            lerp(from, to, (firstTokenMs - start).toFloat() / (end - start))
        }
    }
    return tint.copy(alpha = 0.20f).compositeOver(surface)
}
