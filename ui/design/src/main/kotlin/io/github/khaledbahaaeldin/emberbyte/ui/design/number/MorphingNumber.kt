@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.khaledbahaaeldin.emberbyte.ui.design.number

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ForceLtr
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import io.github.khaledbahaaeldin.emberbyte.ui.design.R
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.LocalReduceMotion
import kotlin.math.ln
import kotlin.math.roundToInt

internal const val REST_BPS = 10_000L
internal const val MAX_BPS = 20_000_000L
private const val WEIGHT_REST = 520f
private const val WEIGHT_MAX = 900f
private const val WIDTH_REST = 100f
private const val WIDTH_MAX = 125f
private const val AXIS_STEPS = 32

internal data class NumberAxes(val weight: Float, val width: Float)

/** 0 at or below 10 KB/s, 1 at or above 20 MB/s, log-scaled in between. */
internal fun throughputIntensity(bps: Long): Float {
    if (bps <= REST_BPS) return 0f
    val t = (ln(bps.toDouble()) - ln(REST_BPS.toDouble())) /
        (ln(MAX_BPS.toDouble()) - ln(REST_BPS.toDouble()))
    return t.coerceIn(0.0, 1.0).toFloat()
}

internal fun axesFor(intensity: Float): NumberAxes = NumberAxes(
    weight = WEIGHT_REST + (WEIGHT_MAX - WEIGHT_REST) * intensity,
    width = WIDTH_REST + (WIDTH_MAX - WIDTH_REST) * intensity,
)

private fun robotoFlexFamily(axes: NumberAxes): FontFamily {
    val weight = axes.weight.roundToInt()
    return FontFamily(
        Font(
            resId = R.font.roboto_flex,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.width(axes.width),
            ),
        ),
    )
}

@Composable
fun MorphingNumber(
    bytes: Long,
    throughputBps: Long,
    units: ByteUnits,
    contentDescription: String,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val live = animate && !LocalReduceMotion.current
    val formatted = remember(bytes, units) { formatBytes(bytes, units) }
    val motion = MaterialTheme.motionScheme

    val intensity by animateFloatAsState(
        targetValue = if (live) throughputIntensity(throughputBps) else 0f,
        animationSpec = motion.defaultEffectsSpec(),
        label = "numberIntensity",
    )
    val step = (intensity * AXIS_STEPS).roundToInt()
    val family = remember(step) { robotoFlexFamily(axesFor(step / AXIS_STEPS.toFloat())) }
    val rollSpatial = motion.defaultSpatialSpec<IntOffset>()
    val rollEffects = motion.defaultEffectsSpec<Float>()

    ForceLtr {
        Column(modifier.clearAndSetSemantics { this.contentDescription = contentDescription }) {
            Row {
                val text = formatted.value
                text.forEachIndexed { index, char ->
                    // Keyed from the right so existing digits keep their identity when the length changes.
                    key(text.length - index) {
                        AnimatedContent(
                            targetState = char,
                            transitionSpec = {
                                if (live) {
                                    (slideInVertically(rollSpatial) { it } + fadeIn(rollEffects)) togetherWith
                                        (slideOutVertically(rollSpatial) { -it } + fadeOut(rollEffects))
                                } else {
                                    EnterTransition.None togetherWith ExitTransition.None
                                }
                            },
                            label = "digit",
                        ) { digit ->
                            Text(
                                text = digit.toString(),
                                modifier = Modifier.clearAndSetSemantics { },
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontFamily = family,
                                    fontSize = 72.sp,
                                    lineHeight = 72.sp,
                                    letterSpacing = (-2).sp,
                                ),
                            )
                        }
                    }
                }
            }
            Text(
                text = formatted.unit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
