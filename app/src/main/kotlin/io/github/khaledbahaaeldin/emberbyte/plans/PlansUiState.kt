package io.github.khaledbahaaeldin.emberbyte.plans

import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PlanCardUi

data class PlansUiState(
    val cards: List<PlanCardUi> = emptyList(),
    val archivedCards: List<PlanCardUi> = emptyList(),
    val showArchived: Boolean = false,
    val loaded: Boolean = false,
)

sealed interface PlansEvent {
    data object ToggleArchived : PlansEvent
}
