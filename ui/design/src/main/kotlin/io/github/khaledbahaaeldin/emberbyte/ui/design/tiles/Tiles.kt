package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ForceLtr
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.NetworkKindUi

@Composable
fun SpeedTile(
    rxBps: Long,
    txBps: Long,
    network: NetworkKindUi?,
    units: ByteUnits,
    modifier: Modifier = Modifier,
) {
    val networkLabel = when (network) {
        NetworkKindUi.Wifi -> "Wi-Fi"
        NetworkKindUi.Mobile -> "Mobile"
        null -> "Offline"
    }
    val down = formatBytes(rxBps, units)
    val up = formatBytes(txBps, units)
    BentoTile(title = "Live · $networkLabel", modifier = modifier.fillMaxHeight()) {
        if (network == null) {
            Text("No connection", style = MaterialTheme.typography.headlineSmall)
        } else {
            ForceLtr {
                Text("↓ ${down.value} ${down.unit}/s", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "↑ ${up.value} ${up.unit}/s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ForecastTile(model: ForecastUi, modifier: Modifier = Modifier) {
    BentoTile(title = "Forecast", modifier = modifier.fillMaxHeight(), container = TileContainer.Tertiary) {
        Text(model.headline, style = MaterialTheme.typography.headlineLarge)
        Text(model.detail, style = MaterialTheme.typography.bodyMedium)
        Text(
            model.confidence,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun EstimatedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            "Estimated",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
