package io.github.khaledbahaaeldin.emberbyte.engine.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class CycleCalculatorTest {
    private fun i(text: String) = Instant.parse(text)
    private fun w(start: String, end: String) = CycleWindow(i(start), i(end))
    private val utc = ZoneOffset.UTC

    @Test fun monthly_window_contains_the_instant() {
        val cycle = Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, utc)
        assertEquals(w("2026-09-12T00:00:00Z", "2026-10-12T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-10-04T10:00:00Z")))
    }

    @Test fun the_renewal_instant_itself_starts_the_new_window() {
        val cycle = Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, utc)
        assertEquals(w("2026-10-12T00:00:00Z", "2026-11-12T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-10-12T00:00:00Z")))
    }

    @Test fun the_time_of_day_of_the_renewal_is_respected() {
        val cycle = Cycle.MonthlyOnDay(12, LocalTime.of(14, 30), utc)
        assertEquals(w("2026-09-12T14:30:00Z", "2026-10-12T14:30:00Z"), CycleCalculator.windowAt(cycle, i("2026-10-12T14:29:00Z")))
        assertEquals(w("2026-10-12T14:30:00Z", "2026-11-12T14:30:00Z"), CycleCalculator.windowAt(cycle, i("2026-10-12T14:30:00Z")))
    }

    @Test fun day_31_clamps_to_the_last_day_of_short_months() {
        val cycle = Cycle.MonthlyOnDay(31, LocalTime.MIDNIGHT, utc)
        assertEquals(w("2026-01-31T00:00:00Z", "2026-02-28T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-02-15T00:00:00Z")))
        assertEquals(w("2026-02-28T00:00:00Z", "2026-03-31T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-03-01T00:00:00Z")))
        assertEquals(w("2026-04-30T00:00:00Z", "2026-05-31T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-05-02T00:00:00Z")))
    }

    @Test fun a_leap_february_has_a_29th() {
        val cycle = Cycle.MonthlyOnDay(31, LocalTime.MIDNIGHT, utc)
        assertEquals(w("2028-02-29T00:00:00Z", "2028-03-31T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2028-03-05T00:00:00Z")))
    }

    @Test fun a_half_hour_zone_shifts_the_window_in_utc() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val cycle = Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, kolkata)
        assertEquals(w("2026-09-30T18:30:00Z", "2026-10-31T18:30:00Z"), CycleCalculator.windowAt(cycle, i("2026-09-30T18:30:00Z")))
        assertEquals(w("2026-08-31T18:30:00Z", "2026-09-30T18:30:00Z"), CycleCalculator.windowAt(cycle, i("2026-09-30T18:29:59Z")))
    }

    @Test fun a_dst_change_inside_the_window_is_followed() {
        val berlin = ZoneId.of("Europe/Berlin")      // +02:00 until 2026-10-25, +01:00 afterwards
        val cycle = Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, berlin)
        assertEquals(w("2026-09-30T22:00:00Z", "2026-10-31T23:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-10-15T00:00:00Z")))
    }

    @Test fun every_n_days_steps_from_the_anchor() {
        val cycle = Cycle.EveryNDays(30, LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT, utc)
        assertEquals(w("2026-01-31T00:00:00Z", "2026-03-02T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-01-31T00:00:00Z")))
        assertEquals(w("2026-01-01T00:00:00Z", "2026-01-31T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-01-30T23:59:00Z")))
    }

    @Test fun every_n_days_works_before_the_anchor() {
        val cycle = Cycle.EveryNDays(7, LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT, utc)
        assertEquals(w("2025-12-25T00:00:00Z", "2026-01-01T00:00:00Z"), CycleCalculator.windowAt(cycle, i("2025-12-30T12:00:00Z")))
    }

    @Test fun every_n_days_respects_a_renewal_time_that_has_not_come_yet() {
        val cycle = Cycle.EveryNDays(7, LocalDate.of(2026, 1, 1), LocalTime.of(6, 0), utc)
        assertEquals(w("2026-01-01T06:00:00Z", "2026-01-08T06:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-01-08T05:00:00Z")))
        assertEquals(w("2026-01-08T06:00:00Z", "2026-01-15T06:00:00Z"), CycleCalculator.windowAt(cycle, i("2026-01-08T06:00:00Z")))
    }

    @Test fun the_previous_window_ends_where_the_window_starts() {
        val cycle = Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, utc)
        val window = CycleCalculator.windowAt(cycle, i("2026-10-04T10:00:00Z"))
        assertEquals(w("2026-08-12T00:00:00Z", "2026-09-12T00:00:00Z"), CycleCalculator.previousWindow(cycle, window))
    }

    @Test fun the_previous_window_of_a_clamped_month_is_correct() {
        val cycle = Cycle.MonthlyOnDay(31, LocalTime.MIDNIGHT, utc)
        val window = w("2026-03-31T00:00:00Z", "2026-04-30T00:00:00Z")
        assertEquals(w("2026-02-28T00:00:00Z", "2026-03-31T00:00:00Z"), CycleCalculator.previousWindow(cycle, window))
    }

    @Test fun windows_tile_the_time_line_without_gaps() {
        val cycle = Cycle.MonthlyOnDay(31, LocalTime.of(3, 15), ZoneId.of("Africa/Cairo"))
        var window = CycleCalculator.windowAt(cycle, i("2026-01-05T00:00:00Z"))
        repeat(24) {
            val next = CycleCalculator.windowAt(cycle, window.end)
            assertEquals(window.end, next.start)
            assertEquals(window, CycleCalculator.previousWindow(cycle, next))
            window = next
        }
    }
}
