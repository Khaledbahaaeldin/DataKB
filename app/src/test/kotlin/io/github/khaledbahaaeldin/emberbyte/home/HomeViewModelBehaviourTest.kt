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
class HomeViewModelBehaviourTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun selected_day_survives_live_ticks_and_settings_changes() = runTest(dispatcher) {
        val tick = MutableSharedFlow<Unit>()
        val settings = FakeSettingsRepository()
        val vm = HomeViewModel(FakeUsageRepository(clock, tick), FakePlanRepository(clock), settings, clock, Locale.ENGLISH)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onEvent(HomeEvent.SelectDay(2))
        advanceUntilIdle()
        tick.emit(Unit)
        settings.update { it.copy(unitSystem = UnitSystem.BINARY) }
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.selectedDay)
        assertFalse(vm.uiState.value.isToday)
        assertTrue(vm.uiState.value.throughputBps > 0L)
        job.cancel()
    }

    @Test fun selecting_a_different_day_moves_the_selection_instead_of_clearing_it() = runTest(dispatcher) {
        val vm = HomeViewModel(FakeUsageRepository(clock, MutableSharedFlow()), FakePlanRepository(clock), FakeSettingsRepository(), clock, Locale.ENGLISH)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onEvent(HomeEvent.SelectDay(1))
        vm.onEvent(HomeEvent.SelectDay(4))
        advanceUntilIdle()
        assertEquals(4, vm.uiState.value.selectedDay)
        job.cancel()
    }
}
