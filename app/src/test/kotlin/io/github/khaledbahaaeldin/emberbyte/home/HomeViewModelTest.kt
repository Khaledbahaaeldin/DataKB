package io.github.khaledbahaaeldin.emberbyte.home

import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
class HomeViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        tick: MutableSharedFlow<Unit> = MutableSharedFlow(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ) = HomeViewModel(FakeUsageRepository(clock, tick), FakePlanRepository(clock), settings, clock, Locale.ENGLISH)

    @Test fun emits_today_total_plan_and_forecast() = runTest(dispatcher) {
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(1_240_000_000L, state.heroBytes)
        assertEquals("3.80 GB left · runs out Thu (±1 day)", state.subtitle)
        assertEquals(7, state.week.size)
        assertEquals(3, state.topApps.size)
        job.cancel()
    }

    @Test fun live_speed_updates_throughput() = runTest(dispatcher) {
        val tick = MutableSharedFlow<Unit>()
        val vm = viewModel(tick)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(0L, vm.uiState.value.throughputBps)
        tick.emit(Unit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.throughputBps > 0L)
        job.cancel()
    }

    @Test fun selecting_a_day_switches_the_hero_and_selecting_it_again_returns_to_today() = runTest(dispatcher) {
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onEvent(HomeEvent.SelectDay(1))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isToday)
        assertEquals(1, vm.uiState.value.selectedDay)

        vm.onEvent(HomeEvent.SelectDay(1))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isToday)
        job.cancel()
    }

    @Test fun unit_setting_is_applied() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        settings.update { it.copy(unitSystem = UnitSystem.BINARY) }
        advanceUntilIdle()
        assertEquals(io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits.BINARY, vm.uiState.value.units)
        job.cancel()
    }
}
