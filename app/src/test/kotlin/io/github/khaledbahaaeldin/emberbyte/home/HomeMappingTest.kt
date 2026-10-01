package io.github.khaledbahaaeldin.emberbyte.home

import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Confidence
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.NetworkKindUi
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMappingTest {
    private val now = Instant.parse("2026-10-05T12:00:00Z") // a Monday
    private val zone = ZoneOffset.UTC
    private val locale = Locale.ENGLISH

    private fun forecast(earliest: Long?, expected: Long?, latest: Long?, confidence: Confidence = Confidence.MEDIUM) =
        Forecast(
            planId = 1,
            runOutEarliest = earliest?.let { now.plusSeconds(it * 86_400) },
            runOutExpected = expected?.let { now.plusSeconds(it * 86_400) },
            runOutLatest = latest?.let { now.plusSeconds(it * 86_400) },
            projectedCycleEndBytes = 0,
            confidence = confidence,
            historyDays = 14,
        )

    private val plan = Plan(1, "Main SIM", null, 10_000_000_000L, Cycle.MonthlyOnDay(12, LocalTime.MIDNIGHT, zone), Rollover.None)
    private val planState = PlanState(
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
    private val today = DayUsage(LocalDate.of(2026, 10, 5), mobileBytes = 940_000_000L, wifiBytes = 300_000_000L)
    private val week = (0..6).map { i ->
        UsagePoint(
            start = now.minusSeconds((6 - i) * 86_400L),
            mobileBytes = (i + 1) * 100_000_000L,
            wifiBytes = 0L,
        )
    }

    private fun build(
        live: LiveSpeed? = null,
        plan: PlanState? = planState,
        forecast: Forecast? = forecast(2, 3, 4),
        apps: List<AppUsage> = emptyList(),
        selectedDay: Int? = null,
        approximate: Boolean = false,
    ) = buildHomeUiState(
        today = today,
        live = live,
        planState = plan?.copy(isApproximate = approximate),
        forecast = forecast,
        apps = apps,
        week = week,
        units = UnitSystem.DECIMAL,
        selectedDay = selectedDay,
        now = now,
        zone = zone,
        locale = locale,
    )

    // ---- forecastToUi ----

    @Test fun forecast_with_a_range_shows_weekday_and_plus_minus_days() {
        val ui = forecastToUi(forecast(2, 3, 4), now, zone, locale)
        assertEquals(ForecastUi("Thu", "runs out (±1 day)", "Medium confidence"), ui)
    }

    @Test fun forecast_uses_the_wider_side_of_the_range() {
        val ui = forecastToUi(forecast(1, 3, 6), now, zone, locale)
        assertEquals("runs out (±3 days)", ui.detail)
    }

    @Test fun forecast_without_a_range_has_no_plus_minus() {
        val ui = forecastToUi(forecast(3, 3, 3), now, zone, locale)
        assertEquals("runs out", ui.detail)
    }

    @Test fun forecast_that_never_runs_out_says_so() {
        val ui = forecastToUi(forecast(null, null, null, Confidence.HIGH), now, zone, locale)
        assertEquals(ForecastUi("Safe", "Won't run out this cycle", "High confidence"), ui)
    }

    @Test fun missing_forecast_is_null() = assertNull(forecastToUi(null, now, zone, locale))

    // ---- buildHomeUiState ----

    @Test fun hero_defaults_to_today() {
        val ui = build()
        assertTrue(ui.isToday)
        assertEquals("Today", ui.heroLabel)
        assertEquals(1_240_000_000L, ui.heroBytes)
    }

    @Test fun hero_description_is_plain_language_with_remaining_data() {
        assertEquals("1.24 gigabytes used today, 3.80 gigabytes left", build().heroDescription)
    }

    @Test fun subtitle_combines_remaining_data_and_forecast() {
        assertEquals("3.80 GB left · runs out Thu (±1 day)", build().subtitle)
    }

    @Test fun subtitle_for_a_safe_plan() {
        val ui = build(forecast = forecast(null, null, null))
        assertEquals("3.80 GB left · won't run out this cycle", ui.subtitle)
    }

    @Test fun no_plan_means_no_subtitle_and_no_remaining_in_the_description() {
        val ui = build(plan = null, forecast = null)
        assertNull(ui.subtitle)
        assertEquals("1.24 gigabytes used today", ui.heroDescription)
    }

    @Test fun approximate_plan_is_flagged() {
        assertTrue(build(approximate = true).isEstimated)
        assertFalse(build(approximate = false).isEstimated)
    }

    @Test fun live_speed_fills_the_speed_fields() {
        val ui = build(live = LiveSpeed(4_200_000L, 350_000L, NetworkKind.WIFI, now))
        assertEquals(4_200_000L, ui.liveRxBps)
        assertEquals(350_000L, ui.liveTxBps)
        assertEquals(4_550_000L, ui.throughputBps)
        assertEquals(NetworkKindUi.Wifi, ui.network)
    }

    @Test fun no_live_speed_means_offline_and_no_throughput() {
        val ui = build(live = null)
        assertEquals(0L, ui.throughputBps)
        assertNull(ui.network)
    }

    // The fixture week runs Tue 2026-09-29 .. Mon 2026-10-05, so index 2 is Thursday 2026-10-01.
    @Test fun week_has_seven_bars_labelled_by_weekday() {
        val ui = build()
        assertEquals(7, ui.week.size)
        assertEquals("Tue", ui.week.first().label)
        assertEquals("Mon", ui.week.last().label)
        assertEquals("Tuesday, 100 megabytes", ui.week.first().description)
    }

    @Test fun selecting_a_day_switches_the_hero_to_that_day() {
        val ui = build(selectedDay = 2)
        assertFalse(ui.isToday)
        assertEquals(300_000_000L, ui.heroBytes)
        assertEquals("Thursday", ui.heroLabel)
        assertEquals(2, ui.selectedDay)
        assertEquals("300 megabytes used on Thursday", ui.heroDescription)
        assertNull(ui.subtitle)
    }

    @Test fun out_of_range_selection_falls_back_to_today() {
        val ui = build(selectedDay = 99)
        assertTrue(ui.isToday)
        assertNull(ui.selectedDay)
    }

    @Test fun top_apps_are_the_three_largest() {
        val apps = listOf(
            AppUsage("a", "A", 1, 10, 0, null),
            AppUsage("b", "B", 2, 400, 0, null),
            AppUsage("c", "C", 3, 300, 0, null),
            AppUsage("d", "D", 4, 200, 0, null),
        )
        assertEquals(listOf("B", "C", "D"), build(apps = apps).topApps.map { it.label })
    }

    @Test fun binary_units_are_mapped() {
        val ui = buildHomeUiState(today, null, null, null, emptyList(), week, UnitSystem.BINARY, null, now, zone, locale)
        assertEquals(ByteUnits.BINARY, ui.units)
    }
}
