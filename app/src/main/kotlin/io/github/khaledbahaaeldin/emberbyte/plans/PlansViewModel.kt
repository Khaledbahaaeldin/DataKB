package io.github.khaledbahaaeldin.emberbyte.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.source.SimInfo
import io.github.khaledbahaaeldin.emberbyte.data.source.SimInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.util.SystemZoneClock
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.home.toByteUnits
import java.time.Clock
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class PlansViewModel(
    private val plans: PlanRepository,
    settings: SettingsRepository,
    private val sims: SimInfoSource,
    private val clock: Clock = SystemZoneClock(),
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {
    private val showArchived = MutableStateFlow(false)

    private data class Entry(val plan: Plan, val state: PlanState?, val forecast: Forecast?, val addOns: List<AddOn>)

    private val entries: Flow<List<Entry>> = plans.observePlans(includeArchived = true).flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyList())
        else combine(list.map { plan ->
            combine(plans.observePlanState(plan.id), plans.observeForecast(plan.id), plans.observeAddOns(plan.id)) { s, f, a -> Entry(plan, s, f, a) }
        }) { it.toList() }
    }

    val uiState: StateFlow<PlansUiState> = combine(entries, settings.observe(), showArchived) { list, s, archivedShown ->
        val units = s.unitSystem.toByteUnits()
        val installed = sims.activeSims()
        val now = clock.instant()
        val cards = list.mapNotNull { e ->
            val state = e.state ?: return@mapNotNull null
            planCardUi(state, e.forecast, e.addOns, simLabel(e.plan.subscriptionId, installed), units, now, clock.zone, locale)
        }
        PlansUiState(cards.filter { !it.archived }, cards.filter { it.archived }, archivedShown, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

    fun onEvent(event: PlansEvent) { when (event) { PlansEvent.ToggleArchived -> showArchived.update { !it } } }

    private fun simLabel(subscriptionId: Int?, installed: List<SimInfo>): String? = when {
        subscriptionId == null -> null
        else -> installed.firstOrNull { it.subscriptionId == subscriptionId }
            ?.let { "SIM ${it.slotIndex + 1} · ${it.displayName}" }
            ?: if (subscriptionId == sims.defaultDataSubscriptionId()) "Default data SIM" else "SIM (not found)"
    }
}
