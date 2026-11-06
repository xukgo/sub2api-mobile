package com.sub2api.mobile.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.sub2api.mobile.ui.components.ContinuousLinearProgressIndicator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProgressIndicatorUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun continuousProgressHasNoColoredStopIndicatorAtTrackEnd() {
        compose.setContent {
            ContinuousLinearProgressIndicator(
                progress = { 0.6f },
                modifier = Modifier.width(200.dp).height(8.dp).testTag("progress"),
                color = Color.Red,
                trackColor = Color.Blue,
            )
        }

        val pixels = compose.onNodeWithTag("progress").captureToImage().toPixelMap()
        var activePixelsAtTrackEnd = 0
        for (x in (pixels.width * 9 / 10) until pixels.width) {
            for (y in 0 until pixels.height) {
                val pixel = pixels[x, y]
                if (pixel.red > 0.8f && pixel.green < 0.2f && pixel.blue < 0.2f) {
                    activePixelsAtTrackEnd++
                }
            }
        }

        assertEquals("轨道尾部不应绘制主进度颜色的终点标记", 0, activePixelsAtTrackEnd)
    }
}
