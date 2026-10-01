package io.github.khaledbahaaeldin.emberbyte.data.util

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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
}
