package io.github.khaledbahaaeldin.emberbyte.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.khaledbahaaeldin.emberbyte.data.PermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.util.DayClock
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.home.toByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.AppRowUi
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class AppsRange { TODAY, LAST_7_DAYS, THIS_MONTH }

enum class AppsNetwork { ALL, MOBILE, WIFI }

data class AppsUiState(
    val range: AppsRange = AppsRange.TODAY,
    val network: AppsNetwork = AppsNetwork.ALL,
    val sort: AppSort = AppSort.BYTES_DESC,
    val query: String = "",
    val apps: List<AppRowUi> = emptyList(),
    val totalBytes: Long = 0L,
    val units: ByteUnits = ByteUnits.DECIMAL,
    val needsUsageAccess: Boolean = false,
    val loaded: Boolean = false,
)

sealed interface AppsEvent {
    data class SetRange(val range: AppsRange) : AppsEvent
    data class SetNetwork(val network: AppsNetwork) : AppsEvent
    data class SetSort(val sort: AppSort) : AppsEvent
    data class SetQuery(val query: String) : AppsEvent
}

/** The range ends at the end of [today] so rows written later in the day are included. */
internal fun rangeFor(range: AppsRange, today: LocalDate, zone: ZoneId): DateRange {
    val end = today.plusDays(1).atStartOfDay(zone).toInstant()
    val start = when (range) {
        AppsRange.TODAY -> today
        AppsRange.LAST_7_DAYS -> today.minusDays(6)
        AppsRange.THIS_MONTH -> today.withDayOfMonth(1)
    }.atStartOfDay(zone).toInstant()
    return DateRange(start, end)
}

internal fun filterFor(network: AppsNetwork): UsageFilter = UsageFilter(
    network = when (network) {
        AppsNetwork.ALL -> null
        AppsNetwork.MOBILE -> NetworkKind.MOBILE
        AppsNetwork.WIFI -> NetworkKind.WIFI
    },
)

internal fun buildAppsUiState(
    apps: List<AppUsage>,
    range: AppsRange,
    network: AppsNetwork,
    sort: AppSort,
    query: String,
    units: ByteUnits,
    needsUsageAccess: Boolean,
): AppsUiState {
    val text = query.trim()
    val shown = if (text.isEmpty()) {
        apps
    } else {
        apps.filter { it.label.contains(text, ignoreCase = true) || it.packageName.contains(text, ignoreCase = true) }
    }
    return AppsUiState(
        range = range,
        network = network,
        sort = sort,
        query = query,
        apps = shown.map { AppRowUi(it.packageName, it.label, it.mobileBytes, it.wifiBytes) },
        totalBytes = shown.sumOf { it.totalBytes },
        units = units,
        needsUsageAccess = needsUsageAccess,
        loaded = true,
    )
}

private data class Selection(
    val range: AppsRange = AppsRange.TODAY,
    val network: AppsNetwork = AppsNetwork.ALL,
    val sort: AppSort = AppSort.BYTES_DESC,
)

/** @param dates tests MUST pass a finite flow (for example `flowOf(date)`). */
@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModel(
    usage: UsageRepository,
    settings: SettingsRepository,
    permissions: PermissionRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    dates: Flow<LocalDate> = DayClock(clock).dates(),
) : ViewModel() {
    private val selection = MutableStateFlow(Selection())
    private val query = MutableStateFlow("")

    private val apps: Flow<List<AppUsage>> = combine(dates, selection) { date, sel -> date to sel }
        .flatMapLatest { (date, sel) ->
            usage.observeApps(rangeFor(sel.range, date, clock.zone), filterFor(sel.network), sel.sort)
        }

    val uiState: StateFlow<AppsUiState> =
        combine(apps, selection, query, settings.observe(), permissions.observe()) { list, sel, text, s, perm ->
            buildAppsUiState(list, sel.range, sel.network, sel.sort, text, s.unitSystem.toByteUnits(), !perm.usageAccess)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    fun onEvent(event: AppsEvent) {
        when (event) {
            is AppsEvent.SetRange -> selection.update { it.copy(range = event.range) }
            is AppsEvent.SetNetwork -> selection.update { it.copy(network = event.network) }
            is AppsEvent.SetSort -> selection.update { it.copy(sort = event.sort) }
            is AppsEvent.SetQuery -> query.value = event.query
        }
    }
}
