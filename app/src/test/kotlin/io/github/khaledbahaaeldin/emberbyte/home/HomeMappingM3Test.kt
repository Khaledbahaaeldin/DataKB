package io.github.khaledbahaaeldin.emberbyte.home

import io.github.khaledbahaaeldin.emberbyte.common.forecastSentence
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.engine.model.Confidence
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeMappingM3Test {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private fun forecast(early: String? = null, exp: String? = null, late: String? = null) =
        Forecast(1, early?.let(Instant::parse), exp?.let(Instant::parse), late?.let(Instant::parse), 0, Confidence.MEDIUM, 14)
    private fun ui(f: Forecast) = forecastToUi(f, now, ZoneOffset.UTC, Locale.ENGLISH)

    @Test fun a_plan_that_lasts_is_safe() {
        val u = ui(forecast())
        assertEquals(ForecastKind.Safe, u.kind)
        assertEquals("Won't run out this cycle", forecastSentence(u))
    }

    @Test fun a_plan_that_is_already_used_up_says_so() {
        val u = ui(forecast("2026-10-10T12:00:00Z", "2026-10-10T12:00:00Z", "2026-10-10T12:00:00Z"))
        assertEquals(ForecastKind.Out, u.kind)
        assertEquals("Out", u.headline)
        assertEquals("Data used up", forecastSentence(u))
    }

    @Test fun an_expected_miss_with_an_early_scenario_is_probably_safe() {
        val u = ui(forecast(early = "2026-10-12T08:00:00Z"))
        assertEquals(ForecastKind.ProbablySafe, u.kind)
        assertEquals("Probably safe", u.headline)
        assertEquals("Could run out as early as Mon", forecastSentence(u))
    }

    @Test fun a_normal_run_out_reads_like_before() {
        val u = ui(forecast("2026-10-11T12:00:00Z", "2026-10-12T12:00:00Z", "2026-10-13T12:00:00Z"))
        assertEquals(ForecastKind.RunsOut, u.kind)
        assertEquals("runs out Mon (±1 day)", forecastSentence(u))
    }

    @Test fun latest_gap_supports_12_and_24_hour_formats() {
        val coverage = CoverageStatus(
            listOf(
                CoverageGap(
                    Instant.parse("2026-10-10T15:41:00Z"),
                    Instant.parse("2026-10-10T15:47:00Z"),
                    GapReason.SERVICE_KILLED,
                ),
            ),
            null,
        )
        val gap24 = latestGap(coverage, Instant.parse("2026-10-10T15:57:00Z"), ZoneOffset.UTC, is24Hour = true, locale = Locale.US)
        assertEquals("Not measured 15:41–15:47 because the app was stopped", gap24?.message)

        val gap12 = latestGap(coverage, Instant.parse("2026-10-10T15:57:00Z"), ZoneOffset.UTC, is24Hour = false, locale = Locale.US)
        assertEquals("Not measured 3:41 PM–3:47 PM because the app was stopped", gap12?.message)
    }

    @Test fun the_home_subtitle_uses_the_same_sentences() {
        val plan = Plan(1, "Main SIM", null, 10_000_000_000L, Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)
        val today = DayUsage(LocalDate.of(2026, 10, 10), mobileBytes = 940_000_000L, wifiBytes = 300_000_000L)

        val safePlanState = PlanState(
            plan = plan,
            window = CycleWindow(now, now),
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
        val safeUi = buildHomeUiState(
            today = today,
            live = null,
            planState = safePlanState,
            forecast = forecast(),
            apps = emptyList(),
            week = emptyList(),
            units = UnitSystem.DECIMAL,
            selectedDay = null,
            now = now,
            zone = ZoneOffset.UTC,
            locale = Locale.ENGLISH,
        )
        assertEquals("3.80 GB left · won't run out this cycle", safeUi.subtitle)

        val outPlanState = safePlanState.copy(
            usedBytes = 10_000_000_000L,
            remainingBytes = 0L,
            fractionUsed = 1.0f,
        )
        val outUi = buildHomeUiState(
            today = today,
            live = null,
            planState = outPlanState,
            forecast = forecast("2026-10-10T12:00:00Z", "2026-10-10T12:00:00Z", "2026-10-10T12:00:00Z"),
            apps = emptyList(),
            week = emptyList(),
            units = UnitSystem.DECIMAL,
            selectedDay = null,
            now = now,
            zone = ZoneOffset.UTC,
            locale = Locale.ENGLISH,
        )
        assertEquals("0 B left · data used up", outUi.subtitle)
    }
}
