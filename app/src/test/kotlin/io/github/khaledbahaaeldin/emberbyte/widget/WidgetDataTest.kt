package io.github.khaledbahaaeldin.emberbyte.widget

import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.plan.RoomPlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryPlanStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.data.Settings
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.engine.model.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetDataTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC)

    private class FakeUsageRepo(
        private val today: DayUsage = DayUsage(LocalDate.of(2026, 10, 10), 100, 0),
        private val apps: List<AppUsage> = emptyList(),
        private val speed: LiveSpeed? = null,
        private val speedFlow: Flow<LiveSpeed> = speed?.let { flowOf(it) } ?: emptyFlow()
    ) : UsageRepository {
        override fun observeLiveSpeed(): Flow<LiveSpeed> = speedFlow
        override fun observeToday(filter: UsageFilter): Flow<DayUsage> = flowOf(today)
        override fun observeSeries(range: DateRange, granularity: Granularity, filter: UsageFilter): Flow<List<UsagePoint>> = emptyFlow()
        override fun observeApps(range: DateRange, filter: UsageFilter, sort: AppSort): Flow<List<AppUsage>> = flowOf(apps)
        override fun observeAppSeries(packageName: String, range: DateRange, granularity: Granularity): Flow<List<UsagePoint>> = emptyFlow()
        override fun observeCoverage(): Flow<CoverageStatus> = emptyFlow()
        override suspend fun refreshNow(): Outcome<Unit> = Outcome.Success(Unit)
    }

    @Test fun load_without_plan() = runBlocking {
        val usage = FakeUsageRepo()
        val plans = RoomPlanRepository(InMemoryPlanStore(), InMemoryUsageStore(), clock, ticks = flowOf(clock.instant()))
        val settings = FakeSettingsRepository()
        val snapshot = WidgetData.load(usage, plans, settings, clock)
        assertNull(snapshot.plan)
        assertNull(snapshot.forecast)
    }

    @Test fun load_speed_timeout() = runBlocking {
        val usage = FakeUsageRepo(speedFlow = emptyFlow())
        val plans = RoomPlanRepository(InMemoryPlanStore(), InMemoryUsageStore(), clock, ticks = flowOf(clock.instant()))
        val settings = FakeSettingsRepository()
        val snapshot = WidgetData.load(usage, plans, settings, clock, speedTimeoutMillis = 50L)
        assertNull(snapshot.speed)
    }

    @Test fun load_top_apps_sorted_and_capped() = runBlocking {
        val apps = listOf(
            AppUsage("a", "App A", 1, 100L, 0, null),
            AppUsage("b", "App B", 2, 300L, 0, null),
            AppUsage("c", "App C", 3, 200L, 0, null),
        )
        val usage = FakeUsageRepo(apps = apps)
        val plans = RoomPlanRepository(InMemoryPlanStore(), InMemoryUsageStore(), clock, ticks = flowOf(clock.instant()))
        val settings = FakeSettingsRepository()
        val snapshot = WidgetData.load(usage, plans, settings, clock)
        assertEquals(2, snapshot.topApps.size)
        assertEquals("App B", snapshot.topApps[0].label)
        assertEquals("App C", snapshot.topApps[1].label)
    }

    @Test fun load_units_from_settings() = runBlocking {
        val usage = FakeUsageRepo()
        val plans = RoomPlanRepository(InMemoryPlanStore(), InMemoryUsageStore(), clock, ticks = flowOf(clock.instant()))
        val settings = FakeSettingsRepository()
        settings.update { it.copy(unitSystem = UnitSystem.BINARY) }
        val snapshot = WidgetData.load(usage, plans, settings, clock)
        assertEquals(UnitSystem.BINARY, snapshot.units)
    }
}
