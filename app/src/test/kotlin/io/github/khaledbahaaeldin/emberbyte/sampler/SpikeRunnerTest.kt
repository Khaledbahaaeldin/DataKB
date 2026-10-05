package io.github.khaledbahaaeldin.emberbyte.sampler

import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpikeRunnerTest {
    private fun spike(pkg: String, id: Long = 1) = SpikeEvent(
        id = id,
        packageName = pkg,
        windowStart = Instant.EPOCH,
        detectedAt = Instant.EPOCH,
        windowMinutes = 120,
        bytes = 200_000_000L,
        baselineBytes = 20_000_000L,
        multiple = 10f,
        dismissed = false,
    )

    @Test
    fun events_are_always_evaluated_even_when_alerts_disabled_but_nothing_posted() = runBlocking {
        var evaluated = false
        var selected = false
        val posted = mutableListOf<SpikeEvent>()

        val runner = SpikeRunner(
            evaluate = { evaluated = true; listOf(spike("com.video")) },
            select = { selected = true; it },
            alertsEnabled = { false },
            post = { posted += it },
        )
        runner.run()

        assertTrue(evaluated)
        assertTrue(!selected)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun when_enabled_only_the_rate_limited_selection_is_posted() = runBlocking {
        val event1 = spike("com.video", 1)
        val event2 = spike("com.video", 2)
        val event3 = spike("com.chat", 3)
        val posted = mutableListOf<SpikeEvent>()

        val runner = SpikeRunner(
            evaluate = { listOf(event1, event2, event3) },
            select = { listOf(event1, event3) },
            alertsEnabled = { true },
            post = { posted += it },
        )
        runner.run()

        assertEquals(listOf(event1, event3), posted)
    }

    @Test
    fun when_no_events_detected_nothing_selected_or_posted() = runBlocking {
        var selected = false
        val posted = mutableListOf<SpikeEvent>()

        val runner = SpikeRunner(
            evaluate = { emptyList() },
            select = { selected = true; it },
            alertsEnabled = { true },
            post = { posted += it },
        )
        runner.run()

        assertTrue(!selected)
        assertTrue(posted.isEmpty())
    }
}
