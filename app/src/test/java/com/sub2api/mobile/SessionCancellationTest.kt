package com.sub2api.mobile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionCancellationTest {
    @Test
    fun cancelledOperationNeverRunsCallbacksForTheNextService() = runTest {
        var success = false
        var failure = false
        val request = launch {
            runSessionCatching {
                try {
                    kotlinx.coroutines.awaitCancellation()
                } catch (_: CancellationException) {
                    // A dependency may swallow cancellation and still return stale data.
                    "old server data"
                }
            }.onSuccess { success = true }.onFailure { failure = true }
        }
        yield()
        request.cancelAndJoin()
        assertFalse(success)
        assertFalse(failure)
    }
}
