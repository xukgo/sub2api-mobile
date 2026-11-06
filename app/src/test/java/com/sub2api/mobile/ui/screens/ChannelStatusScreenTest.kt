package com.sub2api.mobile.ui.screens

import com.sub2api.mobile.data.ChannelMonitorView
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelStatusScreenTest {
    @Test
    fun summarizesEveryChannelStatus() {
        val summary = summarizeChannelHealth(
            listOf(
                monitor(1, "operational"),
                monitor(2, "degraded"),
                monitor(3, "failed"),
                monitor(4, "error"),
                monitor(5, ""),
            ),
        )

        assertEquals(5, summary.total)
        assertEquals(1, summary.operational)
        assertEquals(1, summary.degraded)
        assertEquals(2, summary.unavailable)
        assertEquals(1, summary.unknown)
    }

    @Test
    fun duplicateRowsDoNotInflateOverallCounts() {
        val channel = monitor(9, "operational")

        val summary = summarizeChannelHealth(listOf(channel, channel))

        assertEquals(1, summary.total)
        assertEquals(1, summary.operational)
    }

    private fun monitor(id: Long, status: String) = ChannelMonitorView(id = id, primaryStatus = status)
}
