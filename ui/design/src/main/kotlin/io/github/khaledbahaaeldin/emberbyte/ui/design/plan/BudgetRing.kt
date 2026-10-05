package io.github.khaledbahaaeldin.emberbyte.ui.design.plan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BudgetStateUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.LocalReduceMotion

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BudgetRing(
    fraction: Float,
    state: BudgetStateUi,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    percentText: String? = null,
) {
    val safe = if (fraction.isNaN() || fraction < 0f) 0f else fraction
    val percent = (safe * 100f).toInt()
    val displayText = percentText ?: "$percent%"
    val descNumber = percentText?.removeSuffix("%") ?: percent.toString()
    val reduceMotion = LocalReduceMotion.current
    val sweep by animateFloatAsState(
        targetValue = minOf(safe, 1f),
        animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "ring",
    )
    val scheme = MaterialTheme.colorScheme
    val color = when (state) {
        BudgetStateUi.Ok -> scheme.primary
        BudgetStateUi.Warning -> scheme.tertiary
        BudgetStateUi.Exceeded -> scheme.error
    }
    val track = scheme.surfaceContainerHighest
    val description = buildString {
        append("$descNumber percent of the plan used")
        when (state) {
            BudgetStateUi.Warning -> append(", nearly used up")
            BudgetStateUi.Exceeded -> append(", over the cap")
            BudgetStateUi.Ok -> Unit
        }
    }
    Box(
        modifier.size(size).semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = this.size.minDimension * 0.12f, cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
            if (sweep > 0f) drawArc(color, -90f, 360f * sweep, false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
        }
        Text(displayText, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
        // A state is never colour only: warning and exceeded also show an icon in the corner.
        if (state != BudgetStateUi.Ok) {
            Icon(
                imageVector = if (state == BudgetStateUi.Exceeded) Icons.Rounded.Error else Icons.Rounded.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.align(Alignment.TopEnd).size(size / 4),
            )
        }
    }
}
