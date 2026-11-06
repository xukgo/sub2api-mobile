package com.sub2api.mobile.widget

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sub2api.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class QuotaWidgetProviderTest {
    @Test
    fun providerIsRegisteredAndRemoteViewsCanBeApplied() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val receiver = context.packageManager.getReceiverInfo(
            ComponentName(context, QuotaWidgetProvider::class.java),
            PackageManager.ComponentInfoFlags.of(0),
        )
        assertNotNull(receiver)

        val state = QuotaWidgetState(
            status = QuotaWidgetStatus.READY,
            totalRemainingPercent = 147.0,
            totalCapacityPercent = 200.0,
            measuredAccounts = 2,
            totalAccounts = 2,
            updatedAtMillis = 1L,
        )
        val applied = QuotaWidgetRenderer.createRemoteViews(context, state)
            .apply(context, FrameLayout(context))

        assertEquals("147%", applied.findViewById<TextView>(R.id.widget_value).text.toString())
        assertTrue(applied.findViewById<android.view.View>(R.id.widget_root).hasOnClickListeners())
        assertTrue(applied.findViewById<android.view.View>(R.id.widget_refresh).hasOnClickListeners())

        saveScreenshot(applied, context, "widget-total-quota-light.png")
        val darkConfiguration = Configuration(context.resources.configuration).apply {
            uiMode = uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or Configuration.UI_MODE_NIGHT_YES
        }
        val darkContext = context.createConfigurationContext(darkConfiguration)
        val darkApplied = QuotaWidgetRenderer.createRemoteViews(darkContext, state)
            .apply(darkContext, FrameLayout(darkContext))
        saveScreenshot(darkApplied, darkContext, "widget-total-quota-dark.png")
    }

    private fun saveScreenshot(view: View, context: Context, name: String) {
        val density = context.resources.displayMetrics.density
        val width = (220 * density).roundToInt()
        val height = (100 * density).roundToInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File(requireNotNull(context.getExternalFilesDir(null)), name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 1_000)
    }
}
