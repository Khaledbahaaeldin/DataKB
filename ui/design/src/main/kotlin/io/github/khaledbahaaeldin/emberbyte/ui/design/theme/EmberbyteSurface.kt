package io.github.khaledbahaaeldin.emberbyte.ui.design.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The root of every screen: paints the theme background (so "AMOLED black" is really #000000) and sets the default content colour
 * (so `Text` and `Icon` without an explicit colour are readable in dark theme).
 */
@Composable
fun EmberbyteSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        content = content,
    )
}
