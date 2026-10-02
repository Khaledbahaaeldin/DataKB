package io.github.khaledbahaaeldin.emberbyte.apps

import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(permissions: FakePermissionRepository = FakePermissionRepository()) = AppsViewModel(
        usage = FakeUsageRepository(clock, MutableSharedFlow()),
        settings = FakeSettingsRepository(),
        permissions = permissions,
        clock = clock,
        dates = flowOf(LocalDate.of(2026, 10, 5)),
    )

    @Test fun lists_the_apps_with_their_total() = runTest(dispatcher) {
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(5, state.apps.size)
        assertEquals("YouTube", state.apps.first().label)
        assertEquals(1_022_000_000L, state.totalBytes)
        job.cancel()
    }

    @Test fun events_change_the_selection_and_the_search() = runTest(dispatcher) {
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onEvent(AppsEvent.SetRange(AppsRange.LAST_7_DAYS))
        vm.onEvent(AppsEvent.SetNetwork(AppsNetwork.WIFI))
        vm.onEvent(AppsEvent.SetSort(AppSort.NAME))
        vm.onEvent(AppsEvent.SetQuery("chr"))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(AppsRange.LAST_7_DAYS, state.range)
        assertEquals(AppsNetwork.WIFI, state.network)
        assertEquals(AppSort.NAME, state.sort)
        assertEquals("chr", state.query)
        assertEquals(listOf("Chrome"), state.apps.map { it.label })
        job.cancel()
    }

    @Test fun missing_usage_access_is_reported() = runTest(dispatcher) {
        val permissions = FakePermissionRepository(PermissionState(false, true, true, false))
        val vm = viewModel(permissions)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.needsUsageAccess)
        permissions.set(PermissionState(true, true, true, false))
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.needsUsageAccess)
        job.cancel()
    }
}
