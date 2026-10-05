package io.github.khaledbahaaeldin.emberbyte.data.spike

import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemorySpikeStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.SeriesBuilder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpikeEvaluatorTest {
    private val windowDuration = Duration.ofSeconds(SeriesBuilder.WINDOW_SECONDS)
    private val now = Instant.parse("2026-10-15T12:30:00Z")
    private val currentWindow = SeriesBuilder.windowStart(now)
    private val clock = MutableClock(now)
    private val usage = InMemoryUsageStore()
    private val spikeStore = InMemorySpikeStore()
    private val evaluator = SpikeEvaluator(usage, spikeStore, clock)

    private class MutableClock(var instant: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
        override fun instant(): Instant = instant
        fun advance(duration: Duration) {
            instant = instant.plus(duration)
        }
    }

    private suspend fun registerApp(uid: Int = 10001, pkg: String = "com.video") {
        usage.upsertAppMeta(listOf(AppMeta(uid, pkg, "Video")))
    }

    @Test
    fun a_window_far_above_the_apps_usual_is_a_spike() = runBlocking {
        val uid = 10001
        val pkg = "com.video"
        registerApp(uid, pkg)

        // 3 windows a day with 20 MB on 10 of last 14 days
        val history = mutableListOf<HourlyUsage>()
        for (day in 4..13) {
            val dayStart = currentWindow.minus(Duration.ofDays(day.toLong()))
            for (w in 0..2) {
                val winStart = dayStart.plus(windowDuration.multipliedBy(w.toLong()))
                history += HourlyUsage(winStart, uid, NetworkKind.MOBILE, 1, 20_000_000L, 0L)
            }
        }
        // Current window has 300 MB
        history += HourlyUsage(currentWindow, uid, NetworkKind.MOBILE, 1, 300_000_000L, 0L)
        usage.upsertHourly(history)

        val spikes = evaluator.evaluate()
        assertEquals(1, spikes.size)
        val event = spikes.single()
        assertEquals(pkg, event.packageName)
        assertEquals(currentWindow, event.windowStart)
        assertEquals(0L, event.baselineBytes)
        assertEquals(300_000_000L, event.bytes)
        assertTrue(event.multiple >= 5f)
    }

    @Test
    fun a_busy_app_has_a_higher_baseline_and_needs_more() = runBlocking {
        val uid = 10001
        val pkg = "com.video"
        registerApp(uid, pkg)

        // Every window in 14 days has 60 MB (168 windows)
        val history = mutableListOf<HourlyUsage>()
        for (i in 1..168) {
            val winStart = currentWindow.minus(windowDuration.multipliedBy(i.toLong()))
            history += HourlyUsage(winStart, uid, NetworkKind.MOBILE, 1, 60_000_000L, 0L)
        }
        // 200 MB in current window -> 0 spikes
        history += HourlyUsage(currentWindow, uid, NetworkKind.MOBILE, 1, 200_000_000L, 0L)
        usage.upsertHourly(history)

        val spikes200 = evaluator.evaluate()
        assertTrue(spikes200.isEmpty())

        // 400 MB in current window -> 1 spike
        usage.replaceHourly(currentWindow, listOf(HourlyUsage(currentWindow, uid, NetworkKind.MOBILE, 1, 400_000_000L, 0L)))
        val spikes400 = evaluator.evaluate()
        assertEquals(1, spikes400.size)
        val event = spikes400.single()
        assertEquals(pkg, event.packageName)
        assertEquals(60_000_000L, event.baselineBytes)
        assertEquals(400_000_000L, event.bytes)
        assertTrue(event.multiple >= 5f)
    }

    @Test
    fun an_event_is_stored_once_per_window() = runBlocking {
        val uid = 10001
        val pkg = "com.video"
        registerApp(uid, pkg)

        val history = listOf(
            HourlyUsage(currentWindow.minus(Duration.ofDays(10)), uid, NetworkKind.MOBILE, 1, 10_000_000L, 0L),
            HourlyUsage(currentWindow, uid, NetworkKind.MOBILE, 1, 300_000_000L, 0L),
        )
        usage.upsertHourly(history)

        val first = evaluator.evaluate()
        assertEquals(1, first.size)

        val second = evaluator.evaluate()
        assertTrue(second.isEmpty())

        val stored = spikeStore.observeSpikes().first()
        assertEquals(1, stored.size)
        assertEquals(first.single().windowStart, stored.single().windowStart)
    }

    @Test
    fun the_previous_two_windows_are_checked_too() = runBlocking {
        val uid = 10001
        val pkg = "com.video"
        registerApp(uid, pkg)

        val prev1 = currentWindow.minus(windowDuration)
        val prev2 = currentWindow.minus(windowDuration.multipliedBy(2))

        val history = listOf(
            HourlyUsage(currentWindow.minus(Duration.ofDays(10)), uid, NetworkKind.MOBILE, 1, 10_000_000L, 0L),
            HourlyUsage(prev1, uid, NetworkKind.MOBILE, 1, 300_000_000L, 0L),
            HourlyUsage(prev2, uid, NetworkKind.MOBILE, 1, 400_000_000L, 0L),
        )
        usage.upsertHourly(history)

        val spikes = evaluator.evaluate()
        assertEquals(2, spikes.size)
        val windowStarts = spikes.map { it.windowStart }.toSet()
        assertEquals(setOf(prev1, prev2), windowStarts)
    }

    @Test
    fun an_app_with_less_than_three_days_of_history_is_skipped() = runBlocking {
        val uid = 10001
        val pkg = "com.video"
        registerApp(uid, pkg)

        // First seen only 2 days ago (< 3 days required)
        val history = listOf(
            HourlyUsage(currentWindow.minus(Duration.ofDays(2)), uid, NetworkKind.MOBILE, 1, 10_000_000L, 0L),
            HourlyUsage(currentWindow, uid, NetworkKind.MOBILE, 1, 300_000_000L, 0L),
        )
        usage.upsertHourly(history)

        val spikes = evaluator.evaluate()
        assertTrue(spikes.isEmpty())
    }

    @Test
    fun wifi_and_below_the_minimum_size_never_spike() = runBlocking {
        val uid = 10001
        val pkg = "com.video"
        registerApp(uid, pkg)

        val history = listOf(
            HourlyUsage(currentWindow.minus(Duration.ofDays(10)), uid, NetworkKind.MOBILE, 1, 10_000_000L, 0L),
            // Wi-Fi 500 MB
            HourlyUsage(currentWindow, uid, NetworkKind.WIFI, 1, 500_000_000L, 0L),
            // Mobile below 100 MB (99 MB)
            HourlyUsage(currentWindow, uid, NetworkKind.MOBILE, 1, 99_000_000L, 0L),
        )
        usage.upsertHourly(history)

        val spikes = evaluator.evaluate()
        assertTrue(spikes.isEmpty())
    }

    @Test
    fun rows_of_unknown_uids_are_ignored() = runBlocking {
        val unknownUid = 99999
        val history = listOf(
            HourlyUsage(currentWindow.minus(Duration.ofDays(10)), unknownUid, NetworkKind.MOBILE, 1, 10_000_000L, 0L),
            HourlyUsage(currentWindow, unknownUid, NetworkKind.MOBILE, 1, 500_000_000L, 0L),
        )
        usage.upsertHourly(history)

        val spikes = evaluator.evaluate()
        assertTrue(spikes.isEmpty())
    }

    @Test
    fun notifications_are_rate_limited_to_one_per_app_per_hour() = runBlocking {
        val event1 = SpikeEvent(
            id = 1L,
            packageName = "com.video",
            windowStart = currentWindow,
            detectedAt = now,
            windowMinutes = 120,
            bytes = 300_000_000L,
            baselineBytes = 0L,
            multiple = 300f,
            dismissed = false,
        )
        val event2 = event1.copy(id = 2L, windowStart = currentWindow.minus(windowDuration))

        val notified = evaluator.selectForNotification(listOf(event1, event2))
        assertEquals(1, notified.size)
        assertEquals(event1, notified.single())

        clock.advance(Duration.ofMinutes(30))
        val event3 = event1.copy(id = 3L, windowStart = currentWindow.plus(windowDuration))
        val nextNotified = evaluator.selectForNotification(listOf(event3))
        assertTrue(nextNotified.isEmpty())
    }

    @Test
    fun after_an_hour_the_app_may_be_notified_again() = runBlocking {
        val event1 = SpikeEvent(
            id = 1L,
            packageName = "com.video",
            windowStart = currentWindow,
            detectedAt = now,
            windowMinutes = 120,
            bytes = 300_000_000L,
            baselineBytes = 0L,
            multiple = 300f,
            dismissed = false,
        )
        val firstNotified = evaluator.selectForNotification(listOf(event1))
        assertEquals(1, firstNotified.size)

        clock.advance(Duration.ofMinutes(61))
        val event2 = event1.copy(id = 2L, windowStart = currentWindow.minus(windowDuration))
        val secondNotified = evaluator.selectForNotification(listOf(event2))
        assertEquals(1, secondNotified.size)
        assertEquals(event2, secondNotified.single())
    }
}
