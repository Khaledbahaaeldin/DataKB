package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanStateAssemblerTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private fun plan(sub: Int? = null, rollover: Rollover = Rollover.None) =
        Plan(1, "Main", sub, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), rollover)
    private fun minute(at: String, bytes: Long, sub: Int = 1, network: NetworkKind = NetworkKind.MOBILE) =
        MinuteTotal(Instant.parse(at), network, sub, bytes, 0)
    private fun rows(minutes: List<MinuteTotal> = emptyList(), hourly: List<HourlyUsage> = emptyList(), meta: List<AppMeta> = emptyList(), gaps: List<CoverageGap> = emptyList()) =
        PlanRows(minutes, hourly, meta, gaps)
    private fun state(p: Plan = plan(), rows: PlanRows, rules: List<FreeRule> = emptyList()) =
        PlanStateAssembler.state(p, emptyList(), rules, now, rows)

    @Test fun mobile_minutes_in_the_window_are_the_used_bytes() {
        val s = state(rows = rows(listOf(minute("2026-10-10T10:00:00Z", 1_000_000_000L), minute("2026-10-12T10:00:00Z", 500_000_000L))))
        assertEquals(1_500_000_000L, s.usedBytes)
    }

    @Test fun wifi_and_traffic_before_the_window_do_not_count() {
        val s = state(rows = rows(listOf(
            minute("2026-10-10T10:00:00Z", 9_000_000_000L, network = NetworkKind.WIFI),
            minute("2026-09-20T10:00:00Z", 9_000_000_000L),
        )))
        assertEquals(0L, s.usedBytes)
    }

    @Test fun a_bound_plan_counts_only_its_sim_and_is_marked_approximate_when_another_sim_was_used() {
        val r = rows(listOf(
            minute("2026-10-01T00:00:00Z", 0L, sub = 2),
            minute("2026-10-10T10:00:00Z", 1_000_000_000L, sub = 1),
            minute("2026-10-11T10:00:00Z", 400_000_000L, sub = 2),
        ))
        val bound = state(plan(sub = 2), r)
        assertEquals(400_000_000L, bound.usedBytes)
        assertTrue(bound.isApproximate)
        val unbound = state(plan(sub = null), r)
        assertEquals(1_400_000_000L, unbound.usedBytes)
        assertFalse(unbound.isApproximate)
    }

    @Test fun a_bound_plan_whose_only_traffic_is_its_own_sim_is_exact() {
        val s = state(plan(sub = 2), rows(listOf(
            minute("2026-10-01T00:00:00Z", 0L, sub = 2),
            minute("2026-10-11T10:00:00Z", 400_000_000L, sub = 2),
        )))
        assertFalse(s.isApproximate)
    }

    @Test fun kill_reboot_and_permission_gaps_inside_the_window_make_it_approximate() {
        fun gap(reason: GapReason, from: String = "2026-10-10T10:00:00Z", to: String = "2026-10-10T11:00:00Z") =
            CoverageGap(Instant.parse(from), Instant.parse(to), reason)
        assertTrue(state(rows = rows(gaps = listOf(gap(GapReason.SERVICE_KILLED)))).isApproximate)
        assertTrue(state(rows = rows(gaps = listOf(gap(GapReason.REBOOT)))).isApproximate)
        assertTrue(state(rows = rows(gaps = listOf(gap(GapReason.PERMISSION_MISSING)))).isApproximate)
        assertFalse(state(rows = rows(gaps = listOf(gap(GapReason.DEVICE_ASLEEP)))).isApproximate)
        assertFalse(state(rows = rows(gaps = listOf(gap(GapReason.COUNTER_RESET)))).isApproximate)
        assertFalse(state(rows = rows(gaps = listOf(gap(GapReason.SERVICE_KILLED, "2026-09-10T10:00:00Z", "2026-09-10T11:00:00Z")))).isApproximate)
    }

    @Test fun full_rollover_carries_the_unused_part_of_the_previous_cycle() {
        val r = rows(listOf(minute("2026-09-10T10:00:00Z", 6_000_000_000L)))
        val s = state(plan(rollover = Rollover.Full), r)
        assertEquals(4_000_000_000L, s.rolledOverBytes)
        assertEquals(14_000_000_000L, s.effectiveCapBytes)
        assertEquals(1_000_000_000L, state(plan(rollover = Rollover.Capped(1_000_000_000L)), r).rolledOverBytes)
    }

    @Test fun nothing_rolls_over_when_the_previous_cycle_was_overspent() {
        val r = rows(listOf(minute("2026-09-10T10:00:00Z", 12_000_000_000L)))
        assertEquals(0L, state(plan(rollover = Rollover.Full), r).rolledOverBytes)
    }

    @Test fun an_app_scoped_free_rule_uses_the_two_hour_window_rows_of_that_app() {
        val video = FreeRule(1, 1, "Video", DayOfWeek.entries.toSet(), LocalTime.of(21, 0), LocalTime.of(23, 0), "com.video")
        val hourly = listOf(
            HourlyUsage(Instant.parse("2026-10-10T20:00:00Z"), 10001, NetworkKind.MOBILE, 1, 700_000_000L, 0),
            HourlyUsage(Instant.parse("2026-10-10T20:00:00Z"), 10002, NetworkKind.MOBILE, 1, 300_000_000L, 0),
        )
        val meta = listOf(AppMeta(10001, "com.video", "Video"), AppMeta(10002, "com.other", "Other"))
        val s = state(rows = rows(hourly = hourly, meta = meta), rules = listOf(video))
        // the app's window row [20:00, 22:00) overlaps the rule [21:00, 23:00) by half: 350 MB are free
        assertEquals(350_000_000L, s.freeBytes)
        assertEquals(1_000_000_000L - 350_000_000L, s.usedBytes)
    }

    @Test fun the_history_has_one_mobile_entry_per_day_without_leading_empty_days() {
        val r = rows(listOf(minute("2026-10-05T10:00:00Z", 1_000_000_000L), minute("2026-10-07T10:00:00Z", 2_000_000_000L)))
        val h = PlanStateAssembler.history(plan(), emptyList(), now, r)
        assertEquals(LocalDate.of(2026, 10, 5), h.first().date)
        assertEquals(LocalDate.of(2026, 10, 15), h.last().date)           // today is included; the forecast engine ignores it
        assertEquals(11, h.size)
        assertEquals(listOf(1_000_000_000L, 0L, 2_000_000_000L), h.take(3).map { it.mobileBytes })
    }

    @Test fun no_traffic_gives_an_empty_history() {
        assertTrue(PlanStateAssembler.history(plan(), emptyList(), now, rows()).isEmpty())
    }

    @Test fun history_excludes_traffic_inside_free_windows() {
        val night = FreeRule(1, 1, "Night", DayOfWeek.entries.toSet(), LocalTime.of(0, 0), LocalTime.of(6, 0), null)
        val r = rows(listOf(
            minute("2026-10-05T02:00:00Z", 1_000_000_000L),
            minute("2026-10-05T10:00:00Z", 2_000_000_000L),
        ))
        val h = PlanStateAssembler.history(plan(), listOf(night), now, r)
        val oct5 = h.single { it.date == LocalDate.of(2026, 10, 5) }
        assertEquals(2_000_000_000L, oct5.mobileBytes)
    }

    @Test fun history_without_rules_is_unchanged() {
        val r = rows(listOf(
            minute("2026-10-05T02:00:00Z", 1_000_000_000L),
            minute("2026-10-05T10:00:00Z", 2_000_000_000L),
        ))
        val h = PlanStateAssembler.history(plan(), emptyList(), now, r)
        val oct5 = h.single { it.date == LocalDate.of(2026, 10, 5) }
        assertEquals(3_000_000_000L, oct5.mobileBytes)
    }

    @Test fun history_excludes_package_scoped_free_traffic() {
        val video = FreeRule(1, 1, "Video", DayOfWeek.entries.toSet(), LocalTime.of(21, 0), LocalTime.of(23, 0), "com.video")
        val hourly = listOf(
            HourlyUsage(Instant.parse("2026-10-05T20:00:00Z"), 10001, NetworkKind.MOBILE, 1, 700_000_000L, 0),
            HourlyUsage(Instant.parse("2026-10-05T20:00:00Z"), 10002, NetworkKind.MOBILE, 1, 300_000_000L, 0),
        )
        val meta = listOf(AppMeta(10001, "com.video", "Video"), AppMeta(10002, "com.other", "Other"))
        val r = rows(hourly = hourly, meta = meta)
        val h = PlanStateAssembler.history(plan(), listOf(video), now, r)
        val oct5 = h.single { it.date == LocalDate.of(2026, 10, 5) }
        assertEquals(650_000_000L, oct5.mobileBytes)
    }

    @Test fun rows_are_requested_from_before_the_history_and_the_previous_window() {
        val from = PlanStateAssembler.rowsFrom(plan(), now)
        assertTrue(from <= Instant.parse("2026-09-17T00:00:00Z").minusSeconds(7200))   // 28 days before 15 Oct
        assertTrue(from <= Instant.parse("2026-09-01T00:00:00Z").minusSeconds(7200))   // start of the previous window
    }

    @Test fun rollover_excludes_add_ons_from_the_previous_cycle() {
        val plan = Plan(1, "Main", null, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.Full)
        // Add-on of 2 GB spanning renewal (2026-09-20 to 2026-10-10)
        val spanningAddOn = AddOn(1, 1, "Boost", 2_000_000_000L, Instant.parse("2026-09-20T00:00:00Z"), Instant.parse("2026-10-10T00:00:00Z"))
        // Usage in previous window (Sept) is 3 GB
        val r = rows(listOf(minute("2026-09-10T10:00:00Z", 3_000_000_000L)))
        val s = PlanStateAssembler.state(plan, listOf(spanningAddOn), emptyList(), now, r)
        assertEquals(7_000_000_000L, s.rolledOverBytes)
        assertEquals(2_000_000_000L, s.addOnBytes)
        assertEquals(19_000_000_000L, s.effectiveCapBytes)

        // Add-on valid ONLY in Sept (2026-09-01 to 2026-09-30)
        val septAddOn = AddOn(2, 1, "Sept Boost", 2_000_000_000L, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-30T00:00:00Z"))
        val s2 = PlanStateAssembler.state(plan, listOf(septAddOn), emptyList(), now, r)
        assertEquals(7_000_000_000L, s2.rolledOverBytes)
        assertEquals(0L, s2.addOnBytes)
    }

    @Test fun a_bound_plan_with_unknown_subscription_is_marked_approximate() {
        val rMinute = rows(listOf(
            minute("2026-10-01T00:00:00Z", 0L, sub = 2),
            minute("2026-10-10T10:00:00Z", 100_000_000L, sub = -1),
        ))
        assertTrue(state(plan(sub = 2), rMinute).isApproximate)
        assertFalse(state(plan(sub = null), rMinute).isApproximate)

        val rHourly = rows(
            minutes = listOf(minute("2026-10-01T00:00:00Z", 0L, sub = 2)),
            hourly = listOf(HourlyUsage(Instant.parse("2026-10-10T10:00:00Z"), 10001, NetworkKind.MOBILE, -1, 100_000_000L, 0)),
        )
        assertTrue(state(plan(sub = 2), rHourly).isApproximate)
        assertFalse(state(plan(sub = null), rHourly).isApproximate)

        val rSub2Only = rows(listOf(
            minute("2026-10-01T00:00:00Z", 0L, sub = 2),
            minute("2026-10-10T10:00:00Z", 100_000_000L, sub = 2),
        ))
        assertFalse(state(plan(sub = 2), rSub2Only).isApproximate)
    }

    @Test fun history_older_than_first_measurement_is_approximate() {
        // window starts 2026-10-01T00:00:00Z
        // rows start 10 days after window start (> 2h after window start)
        val lateRows = rows(listOf(minute("2026-10-11T10:00:00Z", 100_000_000L)))
        assertTrue(state(plan(), lateRows).isApproximate)

        // rows cover window start (earliest <= window.start + 2h)
        val coveringRows = rows(listOf(minute("2026-10-01T01:00:00Z", 100_000_000L)))
        assertFalse(state(plan(), coveringRows).isApproximate)

        // no rows at all -> false
        assertFalse(state(plan(), rows()).isApproximate)
    }
}
