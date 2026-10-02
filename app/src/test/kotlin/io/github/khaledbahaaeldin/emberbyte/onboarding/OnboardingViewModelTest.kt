package io.github.khaledbahaaeldin.emberbyte.onboarding

import io.github.khaledbahaaeldin.emberbyte.data.OnboardingRepository
import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePermissionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeOnboardingRepository : OnboardingRepository {
        private val state = MutableStateFlow<Boolean?>(false)
        var completeCalled = false

        override fun observeCompleted(): Flow<Boolean?> = state
        override suspend fun complete() {
            completeCalled = true
            state.value = true
        }
    }

    @Test fun ui_state_observes_permission_changes() = runTest(dispatcher) {
        val onboardingRepo = FakeOnboardingRepository()
        val permsRepo = FakePermissionRepository(PermissionState(usageAccess = false, notifications = false, phoneState = false, vpnConsentGranted = false))
        val vm = OnboardingViewModel(onboardingRepo, permsRepo)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.usageAccess)
        assertFalse(vm.uiState.value.notifications)

        permsRepo.set(PermissionState(usageAccess = true, notifications = true, phoneState = false, vpnConsentGranted = false))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.usageAccess)
        assertTrue(vm.uiState.value.notifications)

        job.cancel()
    }

    @Test fun finish_completes_onboarding_and_invokes_callback() = runTest(dispatcher) {
        val onboardingRepo = FakeOnboardingRepository()
        val permsRepo = FakePermissionRepository()
        val vm = OnboardingViewModel(onboardingRepo, permsRepo)

        var callbackCalled = false
        vm.finish { callbackCalled = true }
        advanceUntilIdle()

        assertTrue(onboardingRepo.completeCalled)
        assertTrue(callbackCalled)
        assertEquals(true, onboardingRepo.observeCompleted().first())
    }
}
