package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.AppGraph
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.SeriesBuilder
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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

    @Test
    fun late_settings_flow_reads_real_value_and_false_suppresses_posting_when_settings_state_is_null() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph(app)
        val now = graph.clock.instant()
        val winStart = SeriesBuilder.windowStart(now)
        val store = io.github.khaledbahaaeldin.emberbyte.data.store.UsageStores.create(app)
        store.upsertAppMeta(listOf(AppMeta(10001, "com.video", "Video")))
        store.upsertHourly(listOf(
            HourlyUsage(winStart.minusSeconds(4 * 86400), 10001, NetworkKind.MOBILE, 1, 10_000_000L, 0L),
            HourlyUsage(winStart, 10001, NetworkKind.MOBILE, 1, 300_000_000L, 0L),
        ))

        val runner = SpikeRunner.create(app, graph, spikeAlertsEnabled = { false })
        runner.run()

        val manager = app.getSystemService(NotificationManager::class.java)
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }
}
