package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.GapUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PermissionPromptUi

@Composable
fun PermissionPrompt(model: PermissionPromptUi, onAction: () -> Unit, modifier: Modifier = Modifier) {
    BentoTile(title = model.title, modifier = modifier.fillMaxWidth(), container = TileContainer.Tertiary) {
        Text(model.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        Button(onClick = onAction, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
            Text(model.actionLabel)
        }
    }
}

@Composable
fun GapBanner(model: GapUi, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null)
            Text(model.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
