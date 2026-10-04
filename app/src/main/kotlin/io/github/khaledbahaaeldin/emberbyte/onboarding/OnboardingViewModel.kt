package io.github.khaledbahaaeldin.emberbyte.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.khaledbahaaeldin.emberbyte.data.OnboardingRepository
import io.github.khaledbahaaeldin.emberbyte.data.PermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val usageAccess: Boolean = false,
    val notifications: Boolean = false,
    val hasPlan: Boolean = false,
)

class OnboardingViewModel(
    private val onboarding: OnboardingRepository,
    permissions: PermissionRepository,
    plans: PlanRepository,
) : ViewModel() {
    val uiState: StateFlow<OnboardingUiState> =
        combine(permissions.observe(), plans.observePlans()) { p, list ->
            OnboardingUiState(p.usageAccess, p.notifications, list.isNotEmpty())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    /** Marks the first-run flow as done, then calls [onDone] (on the main thread). */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            onboarding.complete()
            onDone()
        }
    }
}
