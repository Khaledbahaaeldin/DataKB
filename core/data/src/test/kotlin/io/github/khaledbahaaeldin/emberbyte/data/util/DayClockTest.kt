package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
