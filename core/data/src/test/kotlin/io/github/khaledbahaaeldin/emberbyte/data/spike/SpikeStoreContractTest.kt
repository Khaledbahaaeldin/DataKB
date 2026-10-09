package io.github.khaledbahaaeldin.emberbyte.data.spike

import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

abstract class SpikeStoreContractTest {
    abstract fun createStore(): SpikeStore
    private val w1 = Instant.parse("2026-10-05T10:00:00Z")
    private fun event(pkg: String = "com.video", window: Instant = w1, detectedAt: Instant = w1.plusSeconds(3600)) =
        SpikeEvent(0, pkg, window, detectedAt, 120, 600_000_000L, 40_000_000L, 15f, false)

    @Test fun an_event_is_stored_once_per_package_and_window() = runBlocking {
        val s = createStore()
        assertTrue(s.insertIfAbsent(event()))
        assertFalse(s.insertIfAbsent(event(detectedAt = w1.plusSeconds(7000))))
        assertTrue(s.insertIfAbsent(event(window = w1.plusSeconds(7200))))
        assertTrue(s.insertIfAbsent(event(pkg = "other")))
        assertEquals(3, s.observeSpikes(true).first().size)
    }
    @Test fun events_come_newest_first_and_dismissed_ones_are_hidden_by_default() = runBlocking {
        val s = createStore()
        s.insertIfAbsent(event(window = w1, detectedAt = w1.plusSeconds(100)))
        s.insertIfAbsent(event(window = w1.plusSeconds(7200), detectedAt = w1.plusSeconds(8000)))
        val all = s.observeSpikes(true).first()
        assertEquals(listOf(w1.plusSeconds(7200), w1), all.map { it.windowStart })
        assertTrue(s.dismiss(all.first().id))
        assertEquals(listOf(w1), s.observeSpikes(false).first().map { it.windowStart })
        assertTrue(s.observeSpikes(true).first().first().dismissed)
        assertFalse(s.dismiss(999L))
    }
    @Test fun the_floating_multiple_survives_storage() = runBlocking {
        val s = createStore()
        s.insertIfAbsent(event())
        assertEquals(15f, s.observeSpikes(true).first().single().multiple, 0.001f)
    }
    @Test fun notification_times_are_remembered_per_package() = runBlocking {
        val s = createStore()
        val t = Instant.parse("2026-10-05T12:00:00Z")
        assertNull(s.lastNoticeAt("a"))
        s.markNotified("a", t)
        s.markNotified("a", t.plusSeconds(60))
        assertEquals(t.plusSeconds(60), s.lastNoticeAt("a"))
        assertNull(s.lastNoticeAt("b"))
    }
}
