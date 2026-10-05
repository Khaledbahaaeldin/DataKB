package io.github.khaledbahaaeldin.emberbyte.plans

import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSimInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryPlanStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.plan.RoomPlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.source.SimInfo
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlansViewModelTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun loaded_becomes_true_with_empty_lists_before_any_plan() = runTest(dispatcher) {
        val planStore = InMemoryPlanStore()
        val usageStore = InMemoryUsageStore()
        val repo = RoomPlanRepository(planStore, usageStore, clock, ticks = flowOf(now))
        val settings = FakeSettingsRepository()
        val sims = FakeSimInfoSource()
        val vm = PlansViewModel(repo, settings, sims, clock, Locale.ENGLISH)

        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.loaded)
        assertTrue(vm.uiState.value.cards.isEmpty())
        assertTrue(vm.uiState.value.archivedCards.isEmpty())
        assertFalse(vm.uiState.value.showArchived)
        job.cancel()
    }

    @Test fun after_upsert_plan_the_card_has_used_text_derived_from_seeded_minute_row() = runTest(dispatcher) {
        val planStore = InMemoryPlanStore()
        val usageStore = InMemoryUsageStore()
        val repo = RoomPlanRepository(planStore, usageStore, clock, ticks = flowOf(now))
        val settings = FakeSettingsRepository()
        val sims = FakeSimInfoSource()
        val vm = PlansViewModel(repo, settings, sims, clock, Locale.ENGLISH)

        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        usageStore.addMinute(MinuteTotal(now.minusSeconds(3600), NetworkKind.MOBILE, 5, 2_000_000_000L, 0L))
        val plan = Plan(0L, "Main SIM", 5, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)
        repo.upsertPlan(plan)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.cards.size)
        val card = vm.uiState.value.cards.first()
        assertEquals("Main SIM", card.name)
        assertEquals("2.00 GB of 10.0 GB", card.usedText)
        assertEquals("8.00 GB left", card.remainingText)
        job.cancel()
    }

    @Test fun archived_plan_moves_to_archived_cards_and_is_hidden_until_toggle_archived() = runTest(dispatcher) {
        val planStore = InMemoryPlanStore()
        val usageStore = InMemoryUsageStore()
        val repo = RoomPlanRepository(planStore, usageStore, clock, ticks = flowOf(now))
        val settings = FakeSettingsRepository()
        val sims = FakeSimInfoSource()
        val vm = PlansViewModel(repo, settings, sims, clock, Locale.ENGLISH)

        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val plan = Plan(0L, "Old SIM", null, 5_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None, archived = true)
        repo.upsertPlan(plan)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.cards.isEmpty())
        assertEquals(1, vm.uiState.value.archivedCards.size)
        assertFalse(vm.uiState.value.showArchived)
        assertEquals("Old SIM", vm.uiState.value.archivedCards.first().name)

        vm.onEvent(PlansEvent.ToggleArchived)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showArchived)

        vm.onEvent(PlansEvent.ToggleArchived)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showArchived)
        job.cancel()
    }

    @Test fun sim_label_reflects_installed_sims_unbound_and_missing() = runTest(dispatcher) {
        val planStore = InMemoryPlanStore()
        val usageStore = InMemoryUsageStore()
        val repo = RoomPlanRepository(planStore, usageStore, clock, ticks = flowOf(now))
        val settings = FakeSettingsRepository()
        val sims = FakeSimInfoSource(
            sims = listOf(SimInfo(subscriptionId = 5, slotIndex = 0, displayName = "Alpha")),
        )
        val vm = PlansViewModel(repo, settings, sims, clock, Locale.ENGLISH)

        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val p1 = Plan(0L, "Plan 1", 5, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)
        val p2 = Plan(0L, "Plan 2", null, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)
        val p3 = Plan(0L, "Plan 3", 99, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)

        repo.upsertPlan(p1)
        repo.upsertPlan(p2)
        repo.upsertPlan(p3)
        advanceUntilIdle()

        val cards = vm.uiState.value.cards.associateBy { it.name }
        assertEquals("SIM 1 · Alpha", cards["Plan 1"]?.simLabel)
        assertNull(cards["Plan 2"]?.simLabel)
        assertEquals("SIM (not found)", cards["Plan 3"]?.simLabel)
        job.cancel()
    }

    @Test fun sim_label_falls_back_to_default_data_sim_when_sims_not_listed() = runTest(dispatcher) {
        val planStore = InMemoryPlanStore()
        val usageStore = InMemoryUsageStore()
        val repo = RoomPlanRepository(planStore, usageStore, clock, ticks = flowOf(now))
        val settings = FakeSettingsRepository()
        val sims = FakeSimInfoSource(
            sims = emptyList(),
            defaultId = 5,
        )
        val vm = PlansViewModel(repo, settings, sims, clock, Locale.ENGLISH)

        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val p1 = Plan(0L, "Plan Default", 5, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)
        val p2 = Plan(0L, "Plan Other", 99, 10_000_000_000L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)
        repo.upsertPlan(p1)
        repo.upsertPlan(p2)
        advanceUntilIdle()

        val cards = vm.uiState.value.cards.associateBy { it.name }
        assertEquals("Default data SIM", cards["Plan Default"]?.simLabel)
        assertEquals("SIM (not found)", cards["Plan Other"]?.simLabel)
        job.cancel()
    }

    @Test fun settings_binary_unit_system_formats_cap_in_gib() = runTest(dispatcher) {
        val planStore = InMemoryPlanStore()
        val usageStore = InMemoryUsageStore()
        val repo = RoomPlanRepository(planStore, usageStore, clock, ticks = flowOf(now))
        val settings = FakeSettingsRepository()
        val sims = FakeSimInfoSource()
        val vm = PlansViewModel(repo, settings, sims, clock, Locale.ENGLISH)

        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val plan = Plan(0L, "Main SIM", null, 10_737_418_240L, Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)
        repo.upsertPlan(plan)
        advanceUntilIdle()

        settings.update { it.copy(unitSystem = UnitSystem.BINARY) }
        advanceUntilIdle()

        val card = vm.uiState.value.cards.first()
        assertTrue(card.usedText.contains("10.0 GiB"))
        job.cancel()
    }
}
