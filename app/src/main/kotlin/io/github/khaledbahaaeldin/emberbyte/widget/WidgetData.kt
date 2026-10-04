package io.github.khaledbahaaeldin.emberbyte.widget

import android.content.Context
import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.engine.model.*
import java.time.Clock
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

data class WidgetSnapshot(
    val today: DayUsage,
    val plan: PlanState?,
    val forecast: Forecast?,
    val speed: LiveSpeed?,
    val topApps: List<AppUsage>,
    val units: UnitSystem,
    val now: java.time.Instant,
    val zone: ZoneId,
    val locale: Locale,
)

object WidgetData {
    suspend fun load(
        usage: UsageRepository, plans: PlanRepository, settings: SettingsRepository, clock: Clock,
        locale: Locale = Locale.getDefault(), speedTimeoutMillis: Long = 500L,
    ): WidgetSnapshot {
        val zone = clock.zone
        val today = usage.observeToday().first()
        val plan = plans.observeActivePlanStates().first().firstOrNull()
        val forecast = plan?.let { plans.observeForecast(it.plan.id).firstOrNull() }
        val speed = withTimeoutOrNull(speedTimeoutMillis) { usage.observeLiveSpeed().firstOrNull() }
        val start = today.date.atStartOfDay(zone).toInstant()
        val end = today.date.plusDays(1).atStartOfDay(zone).toInstant()
        val apps = usage.observeApps(DateRange(start, end)).first().sortedByDescending { it.totalBytes }.take(2)
        return WidgetSnapshot(today, plan, forecast, speed, apps, settings.observe().first().unitSystem, clock.instant(), zone, locale)
    }
}
