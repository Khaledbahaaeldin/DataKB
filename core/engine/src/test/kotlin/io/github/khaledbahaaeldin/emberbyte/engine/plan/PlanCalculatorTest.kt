package io.github.khaledbahaaeldin.emberbyte.engine.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanCalculatorTest {
    private val utc = ZoneOffset.UTC
    private val at = Instant.parse("2026-10-15T12:00:00Z")        // window 2026-10-01 .. 2026-11-01 (UTC)
    private val allDays = DayOfWeek.entries.toSet()

    private fun plan(cap: Long = 10_000_000_000L, rollover: Rollover = Rollover.None, zone: ZoneId = utc) =
        Plan(1L, "Main", null, cap, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, zone), rollover)

    private fun slice(start: String, hours: Long, bytes: Long, pkg: String? = null): UsageSlice {
        val s = Instant.parse(start)
        return UsageSlice(s, s.plus(hours, ChronoUnit.HOURS), pkg, bytes)
    }

    private fun rule(id: Long, days: Set<DayOfWeek>, start: LocalTime, end: LocalTime, pkg: String? = null) =
        FreeRule(id, 1L, "rule$id", days, start, end, pkg)

    private fun state(
        plan: Plan = plan(),
        usage: List<UsageSlice> = emptyList(),
        addOns: List<AddOn> = emptyList(),
        rules: List<FreeRule> = emptyList(),
        previousUnused: Long = 0L,
        approximate: Boolean = false,
        now: Instant = at,
    ): PlanState = PlanCalculator.state(plan, now, usage, addOns, rules, previousUnused, approximate)

    @Test fun usage_inside_the_window_is_counted_and_the_rest_remains() {
        val s = state(usage = listOf(slice("2026-10-10T10:00:00Z", 1, 3_000_000_000L)))
        assertEquals(3_000_000_000L, s.usedBytes)
        assertEquals(7_000_000_000L, s.remainingBytes)
        assertEquals(10_000_000_000L, s.effectiveCapBytes)
        assertEquals(0.3f, s.fractionUsed, 1e-6f)
        assertEquals(17, s.daysLeft)
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), s.window.start)
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), s.window.end)
    }

    @Test fun a_slice_that_straddles_the_window_start_counts_only_its_overlap() {
        val s = state(usage = listOf(slice("2026-09-30T23:00:00Z", 2, 1000)))
        assertEquals(500L, s.usedBytes)
    }

    @Test fun slices_outside_the_window_are_ignored() {
        val s = state(usage = listOf(slice("2026-09-30T20:00:00Z", 1, 1000), slice("2026-11-01T00:00:00Z", 1, 1000)))
        assertEquals(0L, s.usedBytes)
    }

    @Test fun usage_above_the_cap_gives_zero_remaining_and_a_fraction_above_one() {
        val s = state(plan = plan(cap = 1_000_000_000L), usage = listOf(slice("2026-10-10T10:00:00Z", 1, 1_500_000_000L)))
        assertEquals(0L, s.remainingBytes)
        assertEquals(1.5f, s.fractionUsed, 1e-6f)
    }

    @Test fun add_ons_overlapping_the_window_raise_the_cap_and_others_are_ignored() {
        val inside = AddOn(1, 1, "Boost", 2_000_000_000L, Instant.parse("2026-10-05T00:00:00Z"), Instant.parse("2026-10-20T00:00:00Z"))
        val before = AddOn(2, 1, "Old", 1_000_000_000L, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-30T00:00:00Z"))
        val s = state(addOns = listOf(inside, before))
        assertEquals(2_000_000_000L, s.addOnBytes)
        assertEquals(12_000_000_000L, s.effectiveCapBytes)
    }

    @Test fun rollover_none_full_and_capped() {
        assertEquals(0L, state(plan = plan(rollover = Rollover.None), previousUnused = 4_000_000_000L).rolledOverBytes)
        val full = state(plan = plan(rollover = Rollover.Full), previousUnused = 4_000_000_000L)
        assertEquals(4_000_000_000L, full.rolledOverBytes)
        assertEquals(14_000_000_000L, full.effectiveCapBytes)
        assertEquals(1_500_000_000L, state(plan = plan(rollover = Rollover.Capped(1_500_000_000L)), previousUnused = 4_000_000_000L).rolledOverBytes)
        assertEquals(4_000_000_000L, state(plan = plan(rollover = Rollover.Capped(9_000_000_000L)), previousUnused = 4_000_000_000L).rolledOverBytes)
    }

    @Test fun an_all_apps_free_rule_excludes_its_traffic() {
        val night = rule(1, allDays, LocalTime.of(0, 0), LocalTime.of(6, 0))
        val s = state(
            usage = listOf(slice("2026-10-10T02:00:00Z", 1, 1_000_000_000L), slice("2026-10-10T07:00:00Z", 1, 500)),
            rules = listOf(night),
        )
        assertEquals(1_000_000_000L, s.freeBytes)
        assertEquals(500L, s.usedBytes)
    }

    @Test fun a_slice_half_inside_a_free_window_is_split_by_time() {
        val night = rule(1, allDays, LocalTime.of(0, 0), LocalTime.of(6, 0))
        val s = state(usage = listOf(slice("2026-10-10T05:30:00Z", 1, 1000)), rules = listOf(night))
        assertEquals(500L, s.freeBytes)
        assertEquals(500L, s.usedBytes)
    }

    @Test fun a_free_window_crossing_midnight_belongs_to_the_day_it_starts() {
        val fridayNight = rule(1, setOf(DayOfWeek.FRIDAY), LocalTime.of(22, 0), LocalTime.of(6, 0))
        val s = state(
            usage = listOf(
                slice("2026-10-09T23:00:00Z", 1, 100),   // Friday 23:00: free
                slice("2026-10-10T01:00:00Z", 1, 200),   // Saturday 01:00: free (the Friday window)
                slice("2026-10-10T23:00:00Z", 1, 400),   // Saturday 23:00: not free
                slice("2026-10-11T01:00:00Z", 1, 800),   // Sunday 01:00: not free
            ),
            rules = listOf(fridayNight),
        )
        assertEquals(300L, s.freeBytes)
        assertEquals(1200L, s.usedBytes)
    }

    @Test fun an_end_of_midnight_is_valid() {
        val evening = rule(1, allDays, LocalTime.of(22, 0), LocalTime.MIDNIGHT)
        val s = state(usage = listOf(slice("2026-10-10T23:00:00Z", 1, 700)), rules = listOf(evening))
        assertEquals(700L, s.freeBytes)
    }

    @Test fun a_rule_with_equal_start_and_end_is_empty() {
        val none = rule(1, allDays, LocalTime.of(8, 0), LocalTime.of(8, 0))
        val s = state(usage = listOf(slice("2026-10-10T08:00:00Z", 1, 1000)), rules = listOf(none))
        assertEquals(0L, s.freeBytes)
    }

    @Test fun an_app_scoped_free_rule_only_excludes_that_app() {
        val video = rule(1, allDays, LocalTime.of(21, 0), LocalTime.of(23, 0), pkg = "com.video")
        val s = state(
            usage = listOf(
                slice("2026-10-10T21:00:00Z", 1, 1000),
                slice("2026-10-10T21:00:00Z", 1, 700, "com.video"),
                slice("2026-10-10T21:00:00Z", 1, 300, "com.other"),
            ),
            rules = listOf(video),
        )
        assertEquals(700L, s.freeBytes)
        assertEquals(300L, s.usedBytes)
    }

    @Test fun overlapping_all_apps_and_app_rules_are_not_double_counted() {
        val everyone = rule(1, allDays, LocalTime.of(21, 30), LocalTime.of(22, 0))
        val video = rule(2, allDays, LocalTime.of(21, 0), LocalTime.of(23, 0), pkg = "com.video")
        val s = state(
            usage = listOf(slice("2026-10-10T21:00:00Z", 1, 1000), slice("2026-10-10T21:00:00Z", 1, 700, "com.video")),
            rules = listOf(everyone, video),
        )
        assertEquals(850L, s.freeBytes)       // 500 by the all-apps rule + 350 by the app rule outside it
        assertEquals(150L, s.usedBytes)
    }

    @Test fun per_app_slices_are_never_added_to_the_total() {
        val s = state(usage = listOf(slice("2026-10-10T21:00:00Z", 1, 1000), slice("2026-10-10T21:00:00Z", 1, 700, "com.video")))
        assertEquals(1000L, s.usedBytes)
    }

    @Test fun a_plan_in_another_zone_evaluates_free_windows_on_its_own_clock() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val night = rule(1, allDays, LocalTime.of(0, 0), LocalTime.of(6, 0))
        // 2026-10-10T19:00Z..20:00Z is 2026-10-11 00:30..01:30 in Kolkata: inside the free night
        val s = state(plan = plan(zone = kolkata), usage = listOf(slice("2026-10-10T19:00:00Z", 1, 1000)), rules = listOf(night))
        assertEquals(1000L, s.freeBytes)
        assertEquals(0L, s.usedBytes)
    }

    @Test fun days_left_rounds_up_and_never_goes_negative() {
        assertEquals(1, state(now = Instant.parse("2026-10-31T00:00:00Z")).daysLeft)
        assertEquals(2, state(now = Instant.parse("2026-10-30T23:59:59Z")).daysLeft)
        assertEquals(1, state(now = Instant.parse("2026-10-31T23:59:59Z")).daysLeft)
    }

    @Test fun the_approximate_flag_is_passed_through() {
        assertTrue(state(approximate = true).isApproximate)
        assertFalse(state(approximate = false).isApproximate)
    }
}
