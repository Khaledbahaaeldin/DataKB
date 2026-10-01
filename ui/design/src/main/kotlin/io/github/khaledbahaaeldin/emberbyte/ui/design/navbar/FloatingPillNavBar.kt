@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.LocalReduceMotion

enum class NavAccent { Primary, Secondary, Tertiary, Error }

data class NavBarItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val accent: NavAccent,
)

@Composable
fun FloatingPillNavBar(
    items: List<NavBarItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
    glass: GlassSource? = null,
) {
    val reduceMotion = LocalReduceMotion.current
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    val selectedIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val shownIndex = previewIndex ?: selectedIndex

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduceMotion) {
            fadeIn(tween(150))
        } else {
            slideInVertically(motion.defaultSpatialSpec<IntOffset>()) { it } + fadeIn(motion.defaultEffectsSpec())
        },
        exit = if (reduceMotion) {
            fadeOut(tween(150))
        } else {
            slideOutVertically(motion.defaultSpatialSpec<IntOffset>()) { it } + fadeOut(motion.defaultEffectsSpec())
        },
    ) {
        Surface(
            modifier = Modifier
                .testTag("pill_nav_bar")
                .semantics { contentDescription = "Navigation" }
                .then(
                    if (glass != null) {
                        Modifier
                            .clip(CircleShape)
                            .hazeBlur(
                                input = HazeInput.Sources(glass.state),
                                style = HazeBlurStyle { blurRadius(24.dp) },
                            )
                    } else {
                        Modifier
                    },
                )
                .height(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .pointerInput(items, selectedIndex, hapticsEnabled) {
                        val width = { size.width.toFloat() }
                        detectHorizontalDragGestures(
                            onDragStart = { offset -> previewIndex = indexAt(offset.x, width(), items.size) },
                            onDragEnd = {
                                previewIndex?.let { if (it != selectedIndex) onSelect(items[it].id) }
                                previewIndex = null
                            },
                            onDragCancel = { previewIndex = null },
                            onHorizontalDrag = { change, _ ->
                                val index = indexAt(change.position.x, width(), items.size)
                                if (index != previewIndex) {
                                    previewIndex = index
                                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                            },
                        )
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    NavItem(
                        item = item,
                        selected = index == shownIndex,
                        onClick = { onSelect(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(item: NavBarItem, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = item.accent.colors(scheme)
    val reduceMotion = LocalReduceMotion.current
    val motion = MaterialTheme.motionScheme

    val background by animateColorAsState(
        targetValue = if (selected) container else Color.Transparent,
        animationSpec = if (reduceMotion) snap() else motion.fastEffectsSpec(),
        label = "navItemBackground",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) content else scheme.onSurfaceVariant,
        animationSpec = if (reduceMotion) snap() else motion.fastEffectsSpec(),
        label = "navItemForeground",
    )

    Row(
        modifier = Modifier
            .testTag("nav_${item.id}")
            .height(48.dp)
            .clip(CircleShape)
            .background(background)
            .animateContentSize(if (reduceMotion) snap<IntSize>() else motion.fastSpatialSpec<IntSize>())
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = item.icon, contentDescription = null, tint = foreground)
        if (selected) {
            Text(
                text = item.label,
                color = foreground,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

private fun NavAccent.colors(scheme: ColorScheme): Pair<Color, Color> = when (this) {
    NavAccent.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
    NavAccent.Secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
    NavAccent.Tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    NavAccent.Error -> scheme.errorContainer to scheme.onErrorContainer
}
