package io.github.khaledbahaaeldin.emberbyte.home

import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelM3Test {
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun plan_chips_and_selection_behaviour() = runTest(dispatcher) {
        val plans = FakePlanRepository(clock)
        val vm = HomeViewModel(
            usage = FakeUsageRepository(clock, MutableSharedFlow()),
            plans = plans,
            settings = FakeSettingsRepository(),
            permissions = FakePermissionRepository(),
            clock = clock,
            locale = Locale.ENGLISH,
            dates = flowOf(LocalDate.of(2026, 10, 5)),
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // (a) with one plan uiState.planChips is empty
        assertTrue(vm.uiState.value.planChips.isEmpty())
        assertTrue(vm.uiState.value.hasPlan)

        // Add a second plan
        val plan2 = Plan(
            id = 2L,
            name = "Work SIM",
            subscriptionId = null,
            capBytes = 5_000_000_000L,
            cycle = Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneOffset.UTC),
            rollover = Rollover.None,
        )
        plans.upsertPlan(plan2)
        advanceUntilIdle()

        // (b) with two plans it has two chips, the first selected
        assertEquals(2, vm.uiState.value.planChips.size)
        assertEquals(1L, vm.uiState.value.planChips[0].id)
        assertTrue(vm.uiState.value.planChips[0].selected)
        assertEquals(2L, vm.uiState.value.planChips[1].id)
        assertFalse(vm.uiState.value.planChips[1].selected)

        // (c) after onEvent(HomeEvent.SelectPlan(2L)) the second chip is selected and the first is not
        vm.onEvent(HomeEvent.SelectPlan(2L))
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.planChips.size)
        assertFalse(vm.uiState.value.planChips[0].selected)
        assertTrue(vm.uiState.value.planChips[1].selected)

        // (d) selecting an unknown id falls back to the first plan
        vm.onEvent(HomeEvent.SelectPlan(999L))
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.planChips.size)
        assertTrue(vm.uiState.value.planChips[0].selected)
        assertFalse(vm.uiState.value.planChips[1].selected)

        // (e) with no plan hasPlan is false and planChips empty
        plans.archivePlan(1L)
        plans.archivePlan(2L)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasPlan)
        assertTrue(vm.uiState.value.planChips.isEmpty())

        job.cancel()
    }
}
