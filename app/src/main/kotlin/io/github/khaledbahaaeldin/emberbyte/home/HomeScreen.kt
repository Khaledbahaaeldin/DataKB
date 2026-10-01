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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes
import io.github.khaledbahaaeldin.emberbyte.ui.design.number.MorphingNumber
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.AppRow
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.BentoTile
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.EstimatedBadge
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.ForecastTile
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.SpeedTile
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.TileContainer
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.UsageBarRow

@Composable
fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
internal fun HomeContent(state: HomeUiState, onEvent: (HomeEvent) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                state.heroLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                Text(
                    "Mobile ${mobile.value} ${mobile.unit}  ·  Wi-Fi ${wifi.value} ${wifi.unit}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        item {
            BentoTile(title = "Top apps today", modifier = Modifier.fillMaxWidth()) {
                state.topApps.forEach { app -> AppRow(app, state.units, onClick = {}) }
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
