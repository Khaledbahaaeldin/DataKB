package io.github.khaledbahaaeldin.emberbyte.apps

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.khaledbahaaeldin.emberbyte.common.ScreenHeader
import io.github.khaledbahaaeldin.emberbyte.common.spokenBytes
import io.github.khaledbahaaeldin.emberbyte.common.weekdayBars
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.util.DayClock
import io.github.khaledbahaaeldin.emberbyte.data.util.SystemZoneClock
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import io.github.khaledbahaaeldin.emberbyte.home.toByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BarUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.number.MorphingNumber
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.BentoTile
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.TileContainer
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.UsageBarRow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class AppDetailUiState(
    val packageName: String,
    val label: String = "",
    val totalBytes: Long = 0L,
    val mobileBytes: Long = 0L,
    val wifiBytes: Long = 0L,
    val bars: List<BarUi> = emptyList(),
    val units: ByteUnits = ByteUnits.DECIMAL,
    val loaded: Boolean = false,
)

internal fun buildAppDetailUiState(
    packageName: String,
    apps: List<AppUsage>,
    series: List<UsagePoint>,
    units: ByteUnits,
    zone: ZoneId,
    locale: Locale,
): AppDetailUiState {
    val app = apps.firstOrNull { it.packageName == packageName }
    return AppDetailUiState(
        packageName = packageName,
        label = app?.label ?: packageName,
        totalBytes = app?.totalBytes ?: series.sumOf { it.totalBytes },
        mobileBytes = app?.mobileBytes ?: series.sumOf { it.mobileBytes },
        wifiBytes = app?.wifiBytes ?: series.sumOf { it.wifiBytes },
        bars = weekdayBars(series, units, zone, locale),
        units = units,
        loaded = true,
    )
}

/** @param dates tests MUST pass a finite flow. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppDetailViewModel(
    private val packageName: String,
    usage: UsageRepository,
    settings: SettingsRepository,
    private val clock: Clock = SystemZoneClock(),
    private val locale: Locale = Locale.getDefault(),
    dates: Flow<LocalDate> = DayClock(clock).dates(),
) : ViewModel() {
    val uiState: StateFlow<AppDetailUiState> = dates.flatMapLatest { date ->
        val zone = clock.zone
        val range = DateRange(
            date.minusDays(6).atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant(),
        )
        combine(
            usage.observeApps(range),
            usage.observeAppSeries(packageName, range, Granularity.DAY),
            settings.observe(),
        ) { apps, series, s -> buildAppDetailUiState(packageName, apps, series, s.unitSystem.toByteUnits(), zone, locale) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDetailUiState(packageName))
}

@Composable
fun AppDetailScreen(viewModel: AppDetailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(state.label.ifEmpty { state.packageName }, onBack) }
        item {
            Text(
                "Last 7 days",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MorphingNumber(
                bytes = state.totalBytes,
                throughputBps = 0L,
                units = state.units,
                contentDescription = "${spokenBytes(state.totalBytes, state.units)} used in the last 7 days",
                animate = false,
            )
        }
        item {
            val mobile = formatBytes(state.mobileBytes, state.units)
            val wifi = formatBytes(state.wifiBytes, state.units)
            BentoTile(title = "By network", modifier = Modifier.fillMaxWidth(), container = TileContainer.Primary) {
                Text(
                    "Mobile ${mobile.value} ${mobile.unit}  ·  Wi-Fi ${wifi.value} ${wifi.unit}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        item { UsageBarRow(points = state.bars, selectedIndex = null, onSelect = {}) }
        if (!state.packageName.startsWith("uid:")) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    TextButton(onClick = { openAppSettings(context, state.packageName) }) { Text("App settings") }
                }
            }
        }
    }
}

private fun openAppSettings(context: android.content.Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No settings screen to open; nothing to do.
    }
}
