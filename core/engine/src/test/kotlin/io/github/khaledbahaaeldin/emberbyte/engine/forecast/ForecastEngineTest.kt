package io.github.khaledbahaaeldin.emberbyte.engine.forecast

import io.github.khaledbahaaeldin.emberbyte.engine.model.Confidence
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastEngineTest {
    private val utc = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-10T00:00:00Z")
    private val uniform = DoubleArray(24) { 1.0 / 24 }

    private fun stateOf(remaining: Long, used: Long = 3_000_000_000L, windowEnd: String = "2026-11-01T00:00:00Z") = PlanState(
        plan = Plan(7L, "Main", null, used + remaining, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, utc), Rollover.None),
        window = CycleWindow(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse(windowEnd)),
        effectiveCapBytes = used + remaining,
        usedBytes = used,
        remainingBytes = remaining,
        rolledOverBytes = 0, addOnBytes = 0, freeBytes = 0, daysLeft = 22, fractionUsed = 0.3f, isApproximate = false,
    )

    /** [days] consecutive days ending the day before 2026-10-10, each [bytes], or [pattern] repeated. */
    private fun history(days: Int, bytes: Long = 1_000_000_000L, pattern: List<Long>? = null): List<DayUsage> {
        val end = LocalDate.of(2026, 10, 9)
        return (0 until days).map { index ->
            val date = end.minusDays((days - 1 - index).toLong())
            DayUsage(date, pattern?.get(index % pattern.size) ?: bytes, 0L)
        }
    }

    @Test fun a_steady_history_gives_one_precise_run_out_instant() {
        val f = ForecastEngine.forecast(stateOf(5_000_000_000L), history(28), now, utc, hourCurve = uniform)
        val expected = Instant.parse("2026-10-15T00:00:00Z")
        assertEquals(expected, f.runOutExpected)
        assertEquals(expected, f.runOutEarliest)
        assertEquals(expected, f.runOutLatest)
        assertEquals(Confidence.HIGH, f.confidence)
        assertEquals(28, f.historyDays)
        assertEquals(7L, f.planId)
    }

    @Test fun a_plan_that_lasts_gives_three_nulls_and_the_projected_total() {
        val f = ForecastEngine.forecast(stateOf(100_000_000_000L), history(28), now, utc, hourCurve = uniform)
        assertNull(f.runOutEarliest); assertNull(f.runOutExpected); assertNull(f.runOutLatest)
        assertEquals(3_000_000_000L + 22_000_000_000L, f.projectedCycleEndBytes)   // 22 days left from 10 Oct to 1 Nov
    }

    @Test fun the_last_day_is_cut_at_the_end_of_the_window() {
        val f = ForecastEngine.forecast(stateOf(100_000_000_000L, windowEnd = "2026-10-12T12:00:00Z"), history(28), now, utc, hourCurve = uniform)
        assertEquals(3_000_000_000L + 2_500_000_000L, f.projectedCycleEndBytes)
    }

    @Test fun usage_already_exhausted_means_now() {
        val f = ForecastEngine.forecast(stateOf(0L), history(28), now, utc, hourCurve = uniform)
        assertEquals(now, f.runOutEarliest); assertEquals(now, f.runOutExpected); assertEquals(now, f.runOutLatest)
    }

    @Test fun only_the_rest_of_today_counts_according_to_the_hour_curve() {
        val noon = Instant.parse("2026-10-10T12:00:00Z")
        val f = ForecastEngine.forecast(stateOf(5_000_000_000L), history(28), noon, utc, hourCurve = uniform)
        assertEquals(Instant.parse("2026-10-15T12:00:00Z"), f.runOutExpected)   // 0.5 + 4 days + 0.5 day
    }

    @Test fun a_variable_history_widens_the_range() {
        val f = ForecastEngine.forecast(stateOf(15_000_000_000L), history(28, pattern = listOf(0L, 4_000_000_000L)), now, utc, hourCurve = uniform)
        assertNotNull(f.runOutEarliest); assertNotNull(f.runOutExpected)
        assertTrue(f.runOutEarliest!! < f.runOutExpected!!)
        assertTrue(f.runOutLatest == null || f.runOutLatest!! > f.runOutExpected!!)
        assertEquals(Confidence.MEDIUM, f.confidence)      // 28 days but a coefficient of variation of about 1
    }

    @Test fun a_weekday_without_samples_borrows_the_overall_mean() {
        // two days of history on two different weekdays, every other weekday has no sample at all
        val sparse = listOf(DayUsage(LocalDate.of(2026, 10, 8), 1_000_000_000L, 0), DayUsage(LocalDate.of(2026, 10, 9), 1_000_000_000L, 0))
        val f = ForecastEngine.forecast(stateOf(3_000_000_000L), sparse, now, utc, hourCurve = uniform)
        assertEquals(Instant.parse("2026-10-13T00:00:00Z"), f.runOutExpected)
        assertEquals(Confidence.LOW, f.confidence)
    }

    @Test fun no_history_at_all_never_runs_out_and_is_low_confidence() {
        val f = ForecastEngine.forecast(stateOf(5_000_000_000L), emptyList(), now, utc, hourCurve = uniform)
        assertNull(f.runOutExpected)
        assertEquals(Confidence.LOW, f.confidence)
        assertEquals(0, f.historyDays)
        assertEquals(3_000_000_000L, f.projectedCycleEndBytes)
    }

    @Test fun confidence_depends_on_the_number_of_days() {
        fun conf(days: Int) = ForecastEngine.forecast(stateOf(5_000_000_000L), history(days), now, utc).confidence
        assertEquals(Confidence.LOW, conf(6))
        assertEquals(Confidence.MEDIUM, conf(7))
        assertEquals(Confidence.MEDIUM, conf(20))
        assertEquals(Confidence.HIGH, conf(21))
    }

    @Test fun today_and_the_future_in_the_history_are_ignored_and_only_28_days_are_used() {
        val noise = history(28) + DayUsage(LocalDate.of(2026, 10, 10), 99_000_000_000L, 0) + DayUsage(LocalDate.of(2026, 10, 11), 99_000_000_000L, 0)
        val old = (1..5).map { DayUsage(LocalDate.of(2026, 10, 9).minusDays(28L + it), 90_000_000_000L, 0) }
        val f = ForecastEngine.forecast(stateOf(5_000_000_000L), old + noise, now, utc, hourCurve = uniform)
        assertEquals(Instant.parse("2026-10-15T00:00:00Z"), f.runOutExpected)
        assertEquals(28, f.historyDays)
    }

    @Test fun what_if_extra_daily_usage_brings_the_run_out_forward() {
        val f = ForecastEngine.forecast(stateOf(5_000_000_000L), history(28), now, utc, extraBytesPerDay = 1_000_000_000L, extraFrom = now, hourCurve = uniform)
        assertEquals(Instant.parse("2026-10-12T12:00:00Z"), f.runOutExpected)
    }

    @Test fun extra_usage_starts_on_the_chosen_day() {
        val f = ForecastEngine.forecast(
            stateOf(5_000_000_000L), history(28), now, utc,
            extraBytesPerDay = 1_000_000_000L, extraFrom = Instant.parse("2026-10-12T00:00:00Z"), hourCurve = uniform,
        )
        // 10 Oct 1, 11 Oct 1, then 2 per day from 12 Oct: after 12 Oct 4, 13 Oct reaches 5 after half of that day
        assertEquals(Instant.parse("2026-10-13T12:00:00Z"), f.runOutExpected)
    }

    @Test fun the_default_hour_curve_is_a_proper_distribution() {
        assertEquals(24, ForecastEngine.DEFAULT_HOUR_CURVE.size)
        assertEquals(1.0, ForecastEngine.DEFAULT_HOUR_CURVE.sum(), 1e-9)
    }

    @Test fun extra_usage_that_starts_later_today_only_adds_the_rest_of_the_day_after_that_moment() {
        val noon = Instant.parse("2026-10-10T12:00:00Z")
        val f = ForecastEngine.forecast(stateOf(1_250_000_000L), history(28), noon, utc,
            extraBytesPerDay = 1_000_000_000L, extraFrom = Instant.parse("2026-10-10T18:00:00Z"), hourCurve = uniform)
        // today: base 0.5 GB + extra 0.25 GB (18:00 to midnight) = 0.75 GB; tomorrow base 1 + extra 1 = 2 GB; 0.5 GB of those 2 GB are needed
        assertEquals(Instant.parse("2026-10-11T06:00:00Z"), f.runOutExpected)
    }

    @Test fun extra_usage_that_started_earlier_today_adds_what_is_still_ahead() {
        val noon = Instant.parse("2026-10-10T12:00:00Z")
        val f = ForecastEngine.forecast(stateOf(1_000_000_000L), history(28), noon, utc,
            extraBytesPerDay = 1_000_000_000L, extraFrom = Instant.parse("2026-10-10T06:00:00Z"), hourCurve = uniform)
        // today: base 0.5 + extra 0.5 = 1.0 GB, exactly the remaining 1 GB at midnight
        assertEquals(Instant.parse("2026-10-11T00:00:00Z"), f.runOutExpected)
    }

    @Test fun extra_usage_that_starts_before_today_counts_the_whole_rest_of_today() {
        val noon = Instant.parse("2026-10-10T12:00:00Z")
        val a = ForecastEngine.forecast(stateOf(1_000_000_000L), history(28), noon, utc,
            extraBytesPerDay = 1_000_000_000L, extraFrom = Instant.parse("2026-10-08T00:00:00Z"), hourCurve = uniform)
        assertEquals(Instant.parse("2026-10-11T00:00:00Z"), a.runOutExpected)
    }
}
