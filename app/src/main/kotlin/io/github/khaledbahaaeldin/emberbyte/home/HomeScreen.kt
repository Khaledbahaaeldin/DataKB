package io.github.khaledbahaaeldin.emberbyte.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ForceLtr
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes
import io.github.khaledbahaaeldin.emberbyte.ui.design.number.MorphingNumber
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.AppRow
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.BentoTile
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.EstimatedBadge
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.ForecastTile
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.GapBanner
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.PermissionPrompt
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.SpeedTile
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.TileContainer
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.UsageBarRow

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onPromptAction: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenSettings = onOpenSettings,
        onOpenHistory = onOpenHistory,
        onPromptAction = onPromptAction,
        onOpenApp = onOpenApp,
        modifier = modifier,
    )
}

@Composable
internal fun HomeContent(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onPromptAction: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.heroLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }
            MorphingNumber(
                bytes = state.heroBytes,
                throughputBps = state.throughputBps,
                units = state.units,
                contentDescription = state.heroDescription,
                animate = state.isToday,
            )
            state.subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (state.isEstimated) EstimatedBadge(Modifier.padding(top = 8.dp))
        }
        state.gap?.let { gap -> item { GapBanner(gap) } }
        items(state.prompts, key = { it.id }) { prompt ->
            PermissionPrompt(prompt, onAction = { onPromptAction(prompt.id) })
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SpeedTile(
                    rxBps = state.liveRxBps,
                    txBps = state.liveTxBps,
                    network = state.network,
                    units = state.units,
                    modifier = Modifier.weight(1f),
                )
                state.forecast?.let { ForecastTile(it, Modifier.weight(1f)) }
            }
        }
        item {
            val mobile = formatBytes(state.mobileBytes, state.units)
            val wifi = formatBytes(state.wifiBytes, state.units)
            BentoTile(
                title = "Today by network",
                modifier = Modifier.fillMaxWidth(),
                container = TileContainer.Primary,
            ) {
                ForceLtr {
                    Text(
                        "Mobile ${mobile.value} ${mobile.unit}  ·  Wi-Fi ${wifi.value} ${wifi.unit}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        if (!state.hasPlan) {
            item {
                BentoTile(title = "Data plan", modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Plans, caps and run-out forecasts arrive in an upcoming update. Until then Emberbyte tracks your usage.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            BentoTile(title = "Top apps today", modifier = Modifier.fillMaxWidth()) {
                when {
                    state.topAppsLocked -> Text("Allow usage access to see which apps use your data.", style = MaterialTheme.typography.bodyMedium)
                    state.topApps.isEmpty() -> Text("No per-app data yet. It appears within about 5 minutes once usage access is allowed.", style = MaterialTheme.typography.bodyMedium)
                    else -> state.topApps.forEach { app -> AppRow(app, state.units, onClick = { onOpenApp(app.packageName) }) }
                }
            }
        }
        item {
            BentoTile(title = "History", modifier = Modifier.fillMaxWidth(), onClick = onOpenHistory) {
                Text("Daily, weekly and monthly usage", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            UsageBarRow(
                points = state.week,
                selectedIndex = state.selectedDay,
                onSelect = { onEvent(HomeEvent.SelectDay(it)) },
            )
        }
    }
}
