package com.sub2api.mobile.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

// These are final card colors: each theme keeps its intended hues and readable text.
private val lightFirstTokenColors = listOf(
    0L to Color(0xFFE3F2E7),
    10_000L to Color(0xFFFFF3C4),
    20_000L to Color(0xFFFFDEB5),
    40_000L to Color(0xFFFAD9DE),
    60_000L to Color(0xFFFFC0C0),
)

private val darkFirstTokenColors = listOf(
    0L to Color(0xFF15442E),
    10_000L to Color(0xFF50460D),
    20_000L to Color(0xFF5A3410),
    40_000L to Color(0xFF542C35),
    60_000L to Color(0xFF651E29),
)

internal fun firstTokenCardColor(firstTokenMs: Long?, surface: Color): Color {
    if (firstTokenMs == null || firstTokenMs < 0) return surface
    val dark = surface.luminance() < 0.5f
    val firstTokenColors = if (dark) darkFirstTokenColors else lightFirstTokenColors
    // Requests below 10 s stay green; yellow starts at the 10 s threshold.
    if (firstTokenMs < 10_000L) {
        val greenEnd = if (dark) Color(0xFF1B5136) else Color(0xFFD5EBDD)
        return lerp(firstTokenColors.first().second, greenEnd, firstTokenMs / 10_000f)
    }
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
