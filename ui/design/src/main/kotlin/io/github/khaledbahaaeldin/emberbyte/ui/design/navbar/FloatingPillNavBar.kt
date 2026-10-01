@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.khaledbahaaeldin.emberbyte.ui.design.navbar

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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

/** Opacity of the pill tint: translucent over a live Haze blur (API 31+), a near-opaque flat tint otherwise. */
internal fun glassTintAlpha(hasGlass: Boolean, sdkInt: Int): Float = if (hasGlass && sdkInt >= 31) 0.6f else 0.92f

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
    val content: @Composable () -> Unit = {
        PillSurface(items, selectedId, onSelect, hapticsEnabled, glass, dragEnabled = !reduceMotion)
    }
    if (reduceMotion) {
        // Reduced motion: no hide animation and no scroll-hide at all; the bar always stays visible.
        Box(modifier) { content() }
    } else {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = slideInVertically(motion.defaultSpatialSpec<IntOffset>()) { it } +
                fadeIn(motion.defaultEffectsSpec()),
            exit = slideOutVertically(motion.defaultSpatialSpec<IntOffset>()) { it } +
                fadeOut(motion.defaultEffectsSpec()),
        ) { content() }
    }
}

@Composable
private fun PillSurface(
    items: List<NavBarItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    hapticsEnabled: Boolean,
    glass: GlassSource?,
    dragEnabled: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    val selectedIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var dragDx by remember { mutableFloatStateOf(0f) }
    val shownIndex = previewIndex ?: selectedIndex

    // Measured item bounds (in the row's coordinate space): items differ in width, so equal slots would drift.
    val starts = remember(items.size) { FloatArray(items.size) }
    val ends = remember(items.size) { FloatArray(items.size) }

    // The previewed pill follows the finger with resistance while dragging, then springs home on release.
    val followDx by animateFloatAsState(
        targetValue = if (previewIndex != null) dragDx else 0f,
        animationSpec = if (previewIndex != null) snap() else motion.fastSpatialSpec(),
        label = "navPillFollow",
    )

    Surface(
        modifier = Modifier
            .testTag("pill_nav_bar")
            .semantics { contentDescription = "Navigation" }
            .selectableGroup()
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh
            .copy(alpha = glassTintAlpha(glass != null, Build.VERSION.SDK_INT)),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .then(
                    if (dragEnabled) {
                        Modifier.pointerInput(items, selectedIndex, hapticsEnabled) {
                            fun resolve(x: Float) {
                                val index = itemIndexAt(x, starts.asList(), ends.asList())
                                val center = (starts[index] + ends[index]) / 2f
                                val half = (ends[index] - starts[index]) / 2f
                                dragDx = dragResistance((x - center).coerceIn(-half, half))
                                if (index != previewIndex) {
                                    previewIndex = index
                                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                            }
                            detectHorizontalDragGestures(
                                onDragStart = { offset -> resolve(offset.x) },
                                onDragEnd = {
                                    previewIndex?.let { if (it != selectedIndex) onSelect(items[it].id) }
                                    previewIndex = null
                                },
                                onDragCancel = { previewIndex = null },
                                onHorizontalDrag = { change, _ -> resolve(change.position.x) },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                NavItem(
                    item = item,
                    selected = index == shownIndex,
                    dragOffset = { if (index == shownIndex) followDx else 0f },
                    onBounds = { start, end ->
                        starts[index] = start
                        ends[index] = end
                    },
                    onClick = { onSelect(item.id) },
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    item: NavBarItem,
    selected: Boolean,
    dragOffset: () -> Float,
    onBounds: (start: Float, end: Float) -> Unit,
    onClick: () -> Unit,
) {
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
            .onGloballyPositioned { coords ->
                val x = coords.positionInParent().x
                onBounds(x, x + coords.size.width)
            }
            .graphicsLayer { translationX = dragOffset() }
            .height(48.dp)
            .clip(CircleShape)
            .background(background)
            .animateContentSize(if (reduceMotion) snap<IntSize>() else motion.fastSpatialSpec<IntSize>())
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The selected item is named by its visible label; the others need an explicit name for TalkBack.
        Icon(imageVector = item.icon, contentDescription = if (selected) null else item.label, tint = foreground)
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
