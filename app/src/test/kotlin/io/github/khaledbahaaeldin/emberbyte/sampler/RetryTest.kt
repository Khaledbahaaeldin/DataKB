package io.github.khaledbahaaeldin.emberbyte.sampler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryTest {
    @Test fun a_failing_block_is_retried_with_doubling_backoff_until_it_succeeds() = runTest {
        var calls = 0
        val errors = mutableListOf<Throwable>()
        val job = launch {
            retryWithBackoff(initialMillis = 1_000, maxMillis = 60_000, onError = { errors += it }) {
                calls++
                if (calls < 3) error("boom $calls")
            }
        }
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(2, calls)
        advanceTimeBy(1_999); runCurrent()
        assertEquals(2, calls)               // the second wait is 2 s, not 1 s
        advanceTimeBy(1); runCurrent()
        assertEquals(3, calls)
        assertTrue(job.isCompleted)
        assertEquals(2, errors.size)
    }

    @Test fun the_backoff_is_capped() = runTest {
        var calls = 0
        val job = launch { retryWithBackoff(initialMillis = 1_000, maxMillis = 4_000, onError = {}) { calls++; error("always") } }
        runCurrent()
        advanceTimeBy(1_000 + 2_000 + 4_000 + 4_000); runCurrent()
        assertEquals(5, calls)               // waits: 1 s, 2 s, 4 s, 4 s (capped)
        job.cancel()
    }

    @Test fun cancellation_is_never_swallowed() = runTest {
        var calls = 0
        val job = launch { retryWithBackoff(1_000, 4_000, onError = {}) { calls++; throw CancellationException("stop") } }
        runCurrent()
        assertEquals(1, calls)
        assertTrue(job.isCancelled)
        assertFalse(job.isActive)
    }
}
