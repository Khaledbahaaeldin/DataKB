package io.github.khaledbahaaeldin.emberbyte.settings

import io.github.khaledbahaaeldin.emberbyte.data.Settings
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun ui_state_reflects_repository_and_updates() = runTest(dispatcher) {
        val repo = FakeSettingsRepository(Settings(amoledBlack = false))
        val vm = SettingsViewModel(repo)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.amoledBlack)

        vm.update { it.copy(amoledBlack = true, unitSystem = UnitSystem.BINARY) }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.amoledBlack)
        assertEquals(UnitSystem.BINARY, vm.uiState.value.unitSystem)
        assertEquals(UnitSystem.BINARY, repo.observe().first().unitSystem)

        job.cancel()
    }
}
