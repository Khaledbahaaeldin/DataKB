@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRepositoriesTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)

    @Test fun today_totals_1_24_gigabytes() = runTest {
        val today = FakeUsageRepository(clock).observeToday().first()
        assertEquals(1_240_000_000L, today.totalBytes)
    }

    @Test fun series_has_seven_daily_points() = runTest {
        val range = io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange(clock.instant(), clock.instant())
        val series = FakeUsageRepository(clock)
            .observeSeries(range, io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity.DAY).first()
        assertEquals(7, series.size)
    }

    @Test fun apps_are_sorted_largest_first() = runTest {
        val range = io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange(clock.instant(), clock.instant())
        val apps = FakeUsageRepository(clock).observeApps(range).first()
        assertTrue(apps.zipWithNext().all { (a, b) -> a.totalBytes >= b.totalBytes })
        assertEquals("YouTube", apps.first().label)
    }

    @Test fun live_speed_emits_once_per_tick() = runTest(UnconfinedTestDispatcher()) {
        val tick = MutableSharedFlow<Unit>()
        val repo = FakeUsageRepository(clock, tick)
        val seen = mutableListOf<Long>()
        val job = launch { repo.observeLiveSpeed().collect { seen += it.rxBps } }
        tick.emit(Unit)
        tick.emit(Unit)
        job.cancel()
        assertEquals(2, seen.size)
    }

    @Test fun live_speed_is_on_mobile_with_a_known_network() = runTest(UnconfinedTestDispatcher()) {
        val tick = MutableSharedFlow<Unit>()
        val repo = FakeUsageRepository(clock, tick)
        var network: NetworkKind? = null
        val job = launch { repo.observeLiveSpeed().collect { network = it.network } }
        tick.emit(Unit)
        job.cancel()
        assertEquals(NetworkKind.MOBILE, network)
    }

    @Test fun seeded_plan_has_3_8_gigabytes_left() = runTest {
        val state = FakePlanRepository(clock).observeActivePlanStates().first().single()
        assertEquals(3_800_000_000L, state.remainingBytes)
        assertEquals(10_000_000_000L, state.effectiveCapBytes)
    }

    @Test fun forecast_exists_for_the_seeded_plan() = runTest {
        val repo = FakePlanRepository(clock)
        val planId = repo.observePlans().first().single().id
        assertNotNull(repo.observeForecast(planId).first())
    }

    @Test fun unknown_plan_has_no_state_or_forecast() = runTest {
        val repo = FakePlanRepository(clock)
        assertNull(repo.observePlanState(999L).first())
        assertNull(repo.observeForecast(999L).first())
    }

    @Test fun upserting_a_new_plan_assigns_an_id() = runTest {
        val repo = FakePlanRepository(clock)
        val result = repo.upsertPlan(
            Plan(0, "Travel", null, 5_000_000_000L, Cycle.EveryNDays(30, java.time.LocalDate.of(2026, 10, 1), LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None),
        )
        assertTrue(result is Outcome.Success)
        assertEquals(2, repo.observePlans().first().size)
    }

    @Test fun archiving_hides_a_plan_from_the_active_list() = runTest {
        val repo = FakePlanRepository(clock)
        val id = repo.observePlans().first().single().id
        repo.archivePlan(id)
        assertTrue(repo.observeActivePlanStates().first().isEmpty())
        assertEquals(1, repo.observePlans(includeArchived = true).first().size)
    }

    @Test fun settings_update_is_observed() = runTest {
        val repo = FakeSettingsRepository()
        repo.update { it.copy(unitSystem = UnitSystem.BINARY, amoledBlack = true) }
        val settings = repo.observe().first()
        assertEquals(UnitSystem.BINARY, settings.unitSystem)
        assertTrue(settings.amoledBlack)
    }

    @Test fun permissions_are_all_granted_in_the_fake() = runTest {
        val state = FakePermissionRepository().observe().first()
        assertTrue(state.usageAccess && state.notifications && state.phoneState)
    }
}
