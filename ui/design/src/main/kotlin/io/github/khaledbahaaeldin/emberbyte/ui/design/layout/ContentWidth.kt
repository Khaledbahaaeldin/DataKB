package io.github.khaledbahaaeldin.emberbyte.ui.design.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

fun Modifier.contentWidth(): Modifier = this.widthIn(max = 600.dp).fillMaxWidth()
