package io.github.khaledbahaaeldin.emberbyte.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private data class HomeData(
    val today: DayUsage,
    val live: LiveSpeed?,
    val planState: PlanState?,
    val forecast: Forecast?,
    val apps: List<AppUsage>,
    val week: List<UsagePoint>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    usage: UsageRepository,
    plans: PlanRepository,
    settings: SettingsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {

    private val selectedDay = MutableStateFlow<Int?>(null)

    private val live: Flow<LiveSpeed?> = usage.observeLiveSpeed().map<LiveSpeed, LiveSpeed?> { it }.onStart { emit(null) }

    private val planAndForecast: Flow<Pair<PlanState?, Forecast?>> =
        plans.observeActivePlanStates().flatMapLatest { states ->
            val state = states.firstOrNull()
            if (state == null) flowOf<Pair<PlanState?, Forecast?>>(null to null)
            else plans.observeForecast(state.plan.id).map { state to it }
        }

    // The ranges are fixed when the ViewModel is created; M2 recomputes them when the date changes.
    private val data: Flow<HomeData> = run {
        val zone = clock.zone
        val todayDate = LocalDate.now(clock)
        val todayRange = DateRange(todayDate.atStartOfDay(zone).toInstant(), clock.instant())
        val weekRange = DateRange(todayDate.minusDays(6).atStartOfDay(zone).toInstant(), clock.instant())
        combine(
            usage.observeToday(),
            live,
            planAndForecast,
            usage.observeApps(todayRange),
            usage.observeSeries(weekRange, Granularity.DAY),
        ) { today, liveSpeed, pf, apps, week ->
            HomeData(today, liveSpeed, pf.first, pf.second, apps, week)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(data, settings.observe(), selectedDay) { d, s, selected ->
        buildHomeUiState(
            today = d.today,
            live = d.live,
            planState = d.planState,
            forecast = d.forecast,
            apps = d.apps,
            week = d.week,
            units = s.unitSystem,
            selectedDay = selected,
            now = clock.instant(),
            zone = clock.zone,
            locale = locale,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.SelectDay -> selectedDay.update { current -> if (current == event.index) null else event.index }
        }
    }
}
