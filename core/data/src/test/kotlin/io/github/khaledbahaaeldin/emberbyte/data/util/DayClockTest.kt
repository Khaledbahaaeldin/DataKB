package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DayClockTest {
    @Test fun emits_today_then_the_next_day_after_midnight() = runTest {
        val clock = SchedulerClock(testScheduler, Instant.parse("2026-10-07T23:59:00Z"))
        val dates = DayClock(clock).dates().take(3).toList()
        assertEquals(listOf(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 9)), dates)
    }

    @Test fun the_first_emission_is_immediate() = runTest {
        val clock = SchedulerClock(testScheduler, Instant.parse("2026-10-07T12:00:00Z"))
        val first = DayClock(clock).dates().take(1).toList().single()
        assertEquals(LocalDate.of(2026, 10, 7), first)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun a_forward_clock_jump_is_noticed_within_one_poll() = runTest {
        var offsetSeconds = 0L
        val base = SchedulerClock(testScheduler, Instant.parse("2026-10-07T12:00:00Z"))
        val jumping = object : java.time.Clock() {
            override fun getZone() = base.zone
            override fun withZone(zone: java.time.ZoneId) = base.withZone(zone)
            override fun instant(): Instant = base.instant().plusSeconds(offsetSeconds)
        }
        val seen = mutableListOf<LocalDate>()
        val job = launch { DayClock(jumping, pollMillis = 30_000).dates().toList(seen) }
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 7)), seen)
        offsetSeconds = 86_400L                      // somebody set the clock one day ahead (or the phone slept through midnight)
        advanceTimeBy(31_000); runCurrent()
        assertEquals(LocalDate.of(2026, 10, 8), seen.last())
        job.cancel()
    }

    @Test fun the_same_date_is_emitted_again_after_a_time_zone_change() = runTest {
        val clock = MutableZoneClock(testScheduler, Instant.parse("2026-10-02T00:30:00Z"), java.time.ZoneId.of("Africa/Cairo")) // Oct 2, 03:30
        val seen = mutableListOf<LocalDate>()
        val job = launch { DayClock(clock, pollMillis = 30_000).dates().toList(seen) }
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 2)), seen)
        clock.zoneId = java.time.ZoneId.of("Europe/London")                 // Oct 2, 01:30: the SAME date in a new zone
        advanceTimeBy(31_000); runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2)), seen)
        job.cancel()
    }

    @Test fun nothing_is_emitted_again_while_neither_date_nor_zone_changes() = runTest {
        val clock = MutableZoneClock(testScheduler, Instant.parse("2026-10-02T12:00:00Z"), java.time.ZoneOffset.UTC)
        val seen = mutableListOf<LocalDate>()
        val job = launch { DayClock(clock, pollMillis = 30_000).dates().toList(seen) }
        runCurrent(); advanceTimeBy(5 * 60_000); runCurrent()
        assertEquals(1, seen.size)
        job.cancel()
    }
}

