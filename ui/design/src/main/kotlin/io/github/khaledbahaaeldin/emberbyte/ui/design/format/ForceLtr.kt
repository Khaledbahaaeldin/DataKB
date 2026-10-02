package io.github.khaledbahaaeldin.emberbyte.ui.design.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** Numbers, units and rates must read the same in every language: lay their content out left-to-right even in an RTL screen (QA D-06). */
@Composable
fun ForceLtr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}
