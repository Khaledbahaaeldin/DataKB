package io.github.khaledbahaaeldin.emberbyte.home

import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMappingM2Test {
    private val now = Instant.parse("2026-10-07T12:00:00Z")
    private val zone = ZoneOffset.UTC
    private fun gap(fromMin: Long, toMin: Long, reason: GapReason) =
        CoverageGap(now.minusSeconds(fromMin * 60), now.minusSeconds(toMin * 60), reason)

    private fun build(coverage: CoverageStatus = CoverageStatus(emptyList(), null), permissions: PermissionState? = null) =
        buildHomeUiState(
            today = DayUsage(LocalDate.of(2026, 10, 7), 0, 0), live = null, planState = null, forecast = null,
            apps = emptyList(), week = emptyList(), units = UnitSystem.DECIMAL, selectedDay = null,
            now = now, zone = zone, locale = Locale.ENGLISH, coverage = coverage, permissions = permissions,
        )

    @Test fun no_permission_info_means_no_prompts() = assertTrue(build().prompts.isEmpty())

    @Test fun all_permissions_granted_means_no_prompts() =
        assertTrue(build(permissions = PermissionState(true, true, true, false)).prompts.isEmpty())

    @Test fun missing_usage_access_prompts_to_open_settings() {
        val prompts = build(permissions = PermissionState(false, true, true, false)).prompts
        assertEquals(listOf("usage_access"), prompts.map { it.id })
        assertEquals("Open settings", prompts.single().actionLabel)
    }

    @Test fun missing_notifications_prompts_to_allow() {
        val prompts = build(permissions = PermissionState(true, false, true, false)).prompts
        assertEquals(listOf("notifications"), prompts.map { it.id })
        assertEquals("Allow", prompts.single().actionLabel)
    }

    @Test fun both_missing_gives_usage_access_first() {
        val prompts = build(permissions = PermissionState(false, false, true, false)).prompts
        assertEquals(listOf("usage_access", "notifications"), prompts.map { it.id })
    }

    @Test fun no_gaps_means_no_banner() = assertNull(build().gap)

    @Test fun a_recent_long_gap_is_described_with_clock_times_and_a_reason() {
        val ui = build(coverage = CoverageStatus(listOf(gap(130, 20, GapReason.SERVICE_KILLED)), null))
        assertEquals("Not measured 09:50–11:40 because the app was stopped", ui.gap?.message)
    }

    @Test fun reasons_have_plain_wording() {
        assertEquals("Not measured 09:50–11:40 because the device restarted", build(coverage = CoverageStatus(listOf(gap(130, 20, GapReason.REBOOT)), null)).gap?.message)
        assertEquals("Not measured 09:50–11:40 because usage access was off", build(coverage = CoverageStatus(listOf(gap(130, 20, GapReason.PERMISSION_MISSING)), null)).gap?.message)
    }

    @Test fun very_short_gaps_are_not_worth_a_banner() =
        assertNull(build(coverage = CoverageStatus(listOf(gap(10, 8, GapReason.COUNTER_RESET)), null)).gap)

    @Test fun gaps_older_than_a_day_are_ignored() =
        assertNull(build(coverage = CoverageStatus(listOf(gap(60 * 30, 60 * 29, GapReason.SERVICE_KILLED)), null)).gap)

    @Test fun the_newest_gap_wins() {
        val ui = build(coverage = CoverageStatus(listOf(gap(600, 500, GapReason.REBOOT), gap(130, 20, GapReason.SERVICE_KILLED)), null))
        assertEquals("Not measured 09:50–11:40 because the app was stopped", ui.gap?.message)
    }

    @Test fun hasPlan_follows_the_plan_state() {
        assertFalse(build().hasPlan)
        val plan = io.github.khaledbahaaeldin.emberbyte.engine.model.Plan(
            1, "Main SIM", null, 10_000_000_000L,
            io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle.MonthlyOnDay(12, java.time.LocalTime.MIDNIGHT, zone),
            io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover.None,
        )
        val planState = io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState(
            plan = plan,
            window = io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow(now, now),
            effectiveCapBytes = 10_000_000_000L,
            usedBytes = 6_200_000_000L,
            remainingBytes = 3_800_000_000L,
            rolledOverBytes = 0,
            addOnBytes = 0,
            freeBytes = 0,
            daysLeft = 9,
            fractionUsed = 0.62f,
            isApproximate = false,
        )
        val ui = buildHomeUiState(
            today = DayUsage(LocalDate.of(2026, 10, 7), 0, 0), live = null, planState = planState, forecast = null,
            apps = emptyList(), week = emptyList(), units = UnitSystem.DECIMAL, selectedDay = null,
            now = now, zone = zone, locale = Locale.ENGLISH,
        )
        assertTrue(ui.hasPlan)
    }

    @Test fun counter_reset_gap_reason_is_described_properly() {
        val ui = build(coverage = CoverageStatus(listOf(gap(130, 20, GapReason.COUNTER_RESET)), null))
        assertEquals("Not measured 09:50–11:40 because the counters reset", ui.gap?.message)
    }

    @Test fun five_minute_gap_is_included() {
        val ui = build(coverage = CoverageStatus(listOf(gap(15, 10, GapReason.SERVICE_KILLED)), null))
        assertEquals("Not measured 11:45–11:50 because the app was stopped", ui.gap?.message)
    }

    @Test fun device_asleep_gaps_are_never_shown_in_the_banner() =
        assertNull(build(coverage = CoverageStatus(listOf(gap(130, 20, GapReason.DEVICE_ASLEEP)), null)).gap)
}
