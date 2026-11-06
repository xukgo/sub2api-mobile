package com.sub2api.mobile.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

// These are final card colors: each theme keeps its intended hues and readable text.
private val lightFirstTokenColors = listOf(
    0L to Color(0xFFE3F2E7),
    5_000L to Color(0xFFFFF3C4),
    10_000L to Color(0xFFFFDEB5),
    20_000L to Color(0xFFFAD9DE),
    40_000L to Color(0xFFFFC0C0),
)

private val darkFirstTokenColors = listOf(
    0L to Color(0xFF15442E),
    5_000L to Color(0xFF50460D),
    10_000L to Color(0xFF5A3410),
    20_000L to Color(0xFF542C35),
    40_000L to Color(0xFF651E29),
)

internal fun firstTokenCardColor(firstTokenMs: Long?, surface: Color): Color {
    if (firstTokenMs == null || firstTokenMs < 0) return surface
    val firstTokenColors = if (surface.luminance() < 0.5f) darkFirstTokenColors else lightFirstTokenColors
    val upper = firstTokenColors.indexOfFirst { firstTokenMs <= it.first }
    return when {
        upper < 0 -> firstTokenColors.last().second
        upper == 0 -> firstTokenColors.first().second
        else -> {
            val (start, from) = firstTokenColors[upper - 1]
            val (end, to) = firstTokenColors[upper]
            lerp(from, to, (firstTokenMs - start).toFloat() / (end - start))
        }
    }
}
