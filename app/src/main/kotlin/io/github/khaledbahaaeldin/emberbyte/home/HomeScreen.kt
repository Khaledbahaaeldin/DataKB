package io.github.khaledbahaaeldin.emberbyte.home

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.platform.LocalDensity

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onPromptAction: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onSetUpPlan: () -> Unit,
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
        onSetUpPlan = onSetUpPlan,
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
    onSetUpPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.planChips.size >= 2) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.planChips.forEach { chip ->
                        FilterChip(
                            selected = chip.selected,
                            onClick = { onEvent(HomeEvent.SelectPlan(chip.id)) },
                            label = { Text(chip.name) },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        )
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.heroLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
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
                ForceLtr {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (state.isEstimated) EstimatedBadge(Modifier.padding(top = 8.dp))
        }
        state.gap?.let { gap -> item { GapBanner(gap) } }
        items(state.prompts, key = { it.id }) { prompt ->
            PermissionPrompt(prompt, onAction = { onPromptAction(prompt.id) })
        }
        item {
            val fontScale = LocalDensity.current.fontScale
            if (fontScale >= 1.5f) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SpeedTile(
                        rxBps = state.liveRxBps,
                        txBps = state.liveTxBps,
                        network = state.network,
                        units = state.units,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.forecast?.let { ForecastTile(it, Modifier.fillMaxWidth()) }
                }
            } else {
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
        }
        state.spike?.let { spike ->
            item {
                BentoTile(
                    title = "Spike detected",
                    modifier = Modifier.fillMaxWidth(),
                    container = TileContainer.Tertiary,
                    onClick = { onOpenApp(spike.packageName) },
                ) {
                    Text(spike.title, style = MaterialTheme.typography.titleMedium)
                    ForceLtr {
                        Text(
                            spike.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = { onEvent(HomeEvent.DismissSpike(spike.id)) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Text("Dismiss")
                    }
                }
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
                BentoTile(title = "Data plan", modifier = Modifier.fillMaxWidth(), onClick = onSetUpPlan) {
                    Text("Set up your data plan to see what is left and when it runs out.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onSetUpPlan, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Text("Set up your data plan")
                    }
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
            val weekSummary = run {
                val highest = state.week.maxByOrNull { it.bytes }
                if (highest != null) "Daily usage, last 7 days, highest ${highest.description}"
                else "Daily usage, last 7 days"
            }
            UsageBarRow(
                points = state.week,
                selectedIndex = state.selectedDay,
                onSelect = { onEvent(HomeEvent.SelectDay(it)) },
                openAtEnd = false,
                summaryDescription = weekSummary,
            )
        }
    }
}
