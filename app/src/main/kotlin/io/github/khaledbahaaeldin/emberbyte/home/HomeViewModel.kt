package io.github.khaledbahaaeldin.emberbyte.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.khaledbahaaeldin.emberbyte.data.PermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.util.DayClock
import io.github.khaledbahaaeldin.emberbyte.data.util.SystemZoneClock
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus
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

private data class HomeBundle(val data: HomeData, val coverage: CoverageStatus, val permissions: PermissionState)

/**
 * @param dates emits the current local date and again after every midnight; the ranges below follow it.
 *   Tests MUST pass a finite flow (for example `flowOf(date)`): the default never completes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    usage: UsageRepository,
    plans: PlanRepository,
    settings: SettingsRepository,
    permissions: PermissionRepository,
    private val clock: Clock = SystemZoneClock(),
    private val locale: Locale = Locale.getDefault(),
    dates: Flow<LocalDate> = DayClock(clock).dates(),
) : ViewModel() {

    private val selectedDay = MutableStateFlow<Int?>(null)

    private val live: Flow<LiveSpeed?> = usage.observeLiveSpeed().map<LiveSpeed, LiveSpeed?> { it }.onStart { emit(null) }

    private val planAndForecast: Flow<Pair<PlanState?, Forecast?>> =
        plans.observeActivePlanStates().flatMapLatest { states ->
            val state = states.firstOrNull()
            if (state == null) flowOf<Pair<PlanState?, Forecast?>>(null to null)
            else plans.observeForecast(state.plan.id).map { state to it }
        }

    private val data: Flow<HomeData> = dates.flatMapLatest { todayDate ->
        val zone = clock.zone
        val endOfDay = todayDate.plusDays(1).atStartOfDay(zone).toInstant()
        val todayRange = DateRange(todayDate.atStartOfDay(zone).toInstant(), endOfDay)
        val weekRange = DateRange(todayDate.minusDays(6).atStartOfDay(zone).toInstant(), endOfDay)
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

    private val bundle: Flow<HomeBundle> =
        combine(data, usage.observeCoverage(), permissions.observe()) { d, coverage, perms -> HomeBundle(d, coverage, perms) }

    val uiState: StateFlow<HomeUiState> = combine(bundle, settings.observe(), selectedDay) { b, s, selected ->
        buildHomeUiState(
            today = b.data.today,
            live = b.data.live,
            planState = b.data.planState,
            forecast = b.data.forecast,
            apps = b.data.apps,
            week = b.data.week,
            units = s.unitSystem,
            selectedDay = selected,
            now = clock.instant(),
            zone = clock.zone,
            locale = locale,
            coverage = b.coverage,
            permissions = b.permissions,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.SelectDay -> selectedDay.update { current -> if (current == event.index) null else event.index }
        }
    }
}
