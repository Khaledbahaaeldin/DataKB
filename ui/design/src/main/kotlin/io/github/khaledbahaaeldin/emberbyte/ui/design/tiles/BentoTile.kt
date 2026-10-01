package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class TileContainer { Default, Primary, Tertiary }

@Composable
fun BentoTile(
    title: String,
    modifier: Modifier = Modifier,
    container: TileContainer = TileContainer.Default,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (background, foreground) = when (container) {
        TileContainer.Default -> scheme.surfaceContainerHigh to scheme.onSurface
        TileContainer.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
        TileContainer.Tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = foreground.copy(alpha = 0.72f))
            content()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = background,
            contentColor = foreground,
            content = body,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = background,
            contentColor = foreground,
            content = body,
        )
    }
}
