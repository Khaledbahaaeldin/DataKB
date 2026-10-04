package io.github.khaledbahaaeldin.emberbyte.widget

import io.github.khaledbahaaeldin.emberbyte.common.forecastSentence
import io.github.khaledbahaaeldin.emberbyte.home.forecastToUi
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.engine.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetModelsTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private fun planState(remaining: Long = 3_800_000_000L) = PlanState(
        Plan(1, "Main", null, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None),
        CycleWindow(now.minusSeconds(86_400), now.plusSeconds(86_400)), 10_000_000_000L, 10_000_000_000L - remaining, remaining, 0, 0, 0, 9, 0.62f, false,
    )
    private fun snapshot(plan: PlanState? = planState(), forecast: Forecast? = null, speed: LiveSpeed? = null, apps: List<AppUsage> = emptyList()) =
        WidgetSnapshot(DayUsage(LocalDate.of(2026, 10, 10), 1_240_000_000L, 0), plan, forecast, speed, apps, UnitSystem.DECIMAL, now, ZoneOffset.UTC, Locale.ENGLISH)

    @Test fun hero_shows_today_and_the_remaining_data() {
        assertEquals(HeroWidgetModel("1.24", "GB", "3.80 GB left"), heroWidgetModel(snapshot()))
    }
    @Test fun hero_without_a_plan_asks_for_one() {
        assertEquals("Set up a plan", heroWidgetModel(snapshot(plan = null)).caption)
    }
    @Test fun hero_uses_binary_units_when_asked() {
        val m = heroWidgetModel(snapshot().copy(units = UnitSystem.BINARY))
        assertEquals("GiB", m.unit)
    }
    @Test fun bento_forecast_uses_the_shared_sentence() {
        val f = Forecast(1, now.plusSeconds(86_400), now.plusSeconds(2 * 86_400), now.plusSeconds(3 * 86_400), 0, Confidence.MEDIUM, 14)
        assertEquals("runs out Mon (±1 day)", bentoWidgetModel(snapshot(forecast = f)).forecast)      // 12 Oct 2026 is a Monday
        assertEquals("Won't run out this cycle", bentoWidgetModel(snapshot(forecast = Forecast(1, null, null, null, 0, Confidence.HIGH, 28))).forecast)
    }
    @Test fun bento_forecast_without_a_plan_or_forecast_has_a_plain_fallback() {
        assertEquals("No plan yet", bentoWidgetModel(snapshot(plan = null)).forecast)
        assertEquals("Not enough history yet", bentoWidgetModel(snapshot(forecast = null)).forecast)
    }
    @Test fun bento_speed_says_offline_or_unknown_or_the_rate() {
        assertEquals("—", bentoWidgetModel(snapshot(speed = null)).speed)
        assertEquals("Offline", bentoWidgetModel(snapshot(speed = LiveSpeed(0, 0, null, now))).speed)
        assertEquals("↓ 4.20 MB/s", bentoWidgetModel(snapshot(speed = LiveSpeed(4_200_000, 100, NetworkKind.WIFI, now))).speed)
    }
    @Test fun bento_lists_the_top_apps_with_their_totals() {
        val apps = listOf(AppUsage("com.video", "Video", 10001, 600_000_000L, 10_000_000L, null), AppUsage("com.chat", "Chat", 10002, 50_000_000L, 0, null))
        assertEquals(listOf("Video · 610 MB", "Chat · 50.0 MB"), bentoWidgetModel(snapshot(apps = apps)).topApps)
    }
}
