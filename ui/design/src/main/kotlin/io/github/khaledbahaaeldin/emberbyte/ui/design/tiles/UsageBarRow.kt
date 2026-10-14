package io.github.khaledbahaaeldin.emberbyte.ui.design.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ForceLtr
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BarUi

@Composable
fun UsageBarRow(
    points: List<BarUi>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollKey: Any? = null,
    openAtEnd: Boolean = true,
    summaryDescription: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 4.dp),
) {
    val max = (points.maxOfOrNull { it.bytes } ?: 0L).coerceAtLeast(1L)
    val summary = summaryDescription ?: run {
        if (points.isNotEmpty()) {
            if (points.size == 7) {
                val highest = points.maxByOrNull { it.bytes }
                if (highest != null) "Daily usage, last 7 days, highest ${highest.description}"
                else "Daily usage, last 7 days"
            } else {
                "Daily usage, last ${points.size} periods"
            }
        } else "Daily usage"
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = summary },
    ) {

        val count = points.size
        val totalNeeded = (count * 48).dp + ((count - 1).coerceAtLeast(0) * 8).dp
        val scrollable = totalNeeded > maxWidth
        val scrollState = rememberScrollState()

        if (scrollable && openAtEnd) {
            LaunchedEffect(points.size, scrollKey) {
                scrollState.scrollTo(scrollState.maxValue)
            }
        }

        Row(
            modifier = if (scrollable) {
                Modifier.fillMaxWidth().horizontalScroll(scrollState).padding(contentPadding)
            } else {
                Modifier.fillMaxWidth().padding(contentPadding)
            },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEachIndexed { index, bar ->
                val isSelected = index == selectedIndex
                Column(
                    modifier = if (scrollable) {
                        Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(role = Role.Tab) { onSelect(index) }
                            .semantics {
                                contentDescription = bar.description
                                selected = isSelected
                            }
                    } else {
                        Modifier
                            .weight(1f)
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(role = Role.Tab) { onSelect(index) }
                            .semantics {
                                contentDescription = bar.description
                                selected = isSelected
                            }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        Modifier
                            .widthIn(max = 24.dp)
                            .fillMaxWidth()
                            .height((8 + 56 * (bar.bytes.toFloat() / max)).dp)
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                    ForceLtr {
                        Text(
                            bar.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
