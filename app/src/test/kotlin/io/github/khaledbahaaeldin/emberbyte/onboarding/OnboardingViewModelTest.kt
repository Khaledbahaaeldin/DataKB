package io.github.khaledbahaaeldin.emberbyte.onboarding

import io.github.khaledbahaaeldin.emberbyte.data.OnboardingRepository
import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryPlanStore
import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore
import io.github.khaledbahaaeldin.emberbyte.data.plan.RoomPlanRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
        val permsRepo = FakePermissionRepository(PermissionState(usageAccess = false, notifications = false, phoneState = false))
        val plansRepo = FakePlanRepository()
        val vm = OnboardingViewModel(onboardingRepo, permsRepo, plansRepo)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.usageAccess)
        assertFalse(vm.uiState.value.notifications)

        permsRepo.set(PermissionState(usageAccess = true, notifications = true, phoneState = false))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.usageAccess)
        assertTrue(vm.uiState.value.notifications)

        job.cancel()
    }

    @Test fun finish_completes_onboarding_and_invokes_callback() = runTest(dispatcher) {
        val onboardingRepo = FakeOnboardingRepository()
        val permsRepo = FakePermissionRepository()
        val plansRepo = FakePlanRepository()
        val vm = OnboardingViewModel(onboardingRepo, permsRepo, plansRepo)

        var callbackCalled = false
        vm.finish { callbackCalled = true }
        advanceUntilIdle()

        assertTrue(onboardingRepo.completeCalled)
        assertTrue(callbackCalled)
        assertEquals(true, onboardingRepo.observeCompleted().first())
    }

    @Test fun has_plan_follows_the_plan_repository() = runTest(dispatcher) {
        val onboardingRepo = FakeOnboardingRepository()
        val permsRepo = FakePermissionRepository()
        val clock = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC)
        val emptyRepo = RoomPlanRepository(InMemoryPlanStore(), InMemoryUsageStore(), clock)
        val vmEmpty = OnboardingViewModel(onboardingRepo, permsRepo, emptyRepo)
        val job1 = launch { vmEmpty.uiState.collect {} }
        advanceUntilIdle()
        assertFalse(vmEmpty.uiState.value.hasPlan)
        job1.cancel()

        val seededRepo = FakePlanRepository(clock)
        val vmSeeded = OnboardingViewModel(onboardingRepo, permsRepo, seededRepo)
        val job2 = launch { vmSeeded.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vmSeeded.uiState.value.hasPlan)
        job2.cancel()
    }
}
