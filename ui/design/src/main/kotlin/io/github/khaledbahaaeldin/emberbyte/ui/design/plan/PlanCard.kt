package io.github.khaledbahaaeldin.emberbyte.ui.design.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ForceLtr
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BudgetStateUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PlanCardUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.EstimatedBadge

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanCard(model: PlanCardUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val summary = buildString {
        append("${model.name}, ${model.percentText.removeSuffix("%")} percent used, ${model.remainingText}, ${model.daysLeftText}")
        when (model.ringState) {
            BudgetStateUi.Warning -> append(", nearly used up")
            BudgetStateUi.Exceeded -> append(", over the cap")
            BudgetStateUi.Ok -> Unit
        }
        if (model.isEstimated) {
            append(", estimated")
        }
        if (model.archived) {
            append(", archived")
        }
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = summary },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BudgetRing(model.fraction, model.ringState, percentText = model.percentText)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(model.name, style = MaterialTheme.typography.titleMedium)
                model.simLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                ForceLtr { Text(model.usedText, style = MaterialTheme.typography.bodyMedium) }
                ForceLtr {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(model.remainingText, style = MaterialTheme.typography.bodyMedium)
                        Text("·", style = MaterialTheme.typography.bodyMedium)
                        Text(model.daysLeftText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                model.forecastText?.let {
                    ForceLtr {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                if (model.isEstimated) EstimatedBadge()
                if (model.archived) Text("Archived", style = MaterialTheme.typography.labelMedium)
                if (model.chips.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        model.chips.forEach { AssistChip(onClick = {}, label = { Text(it, maxLines = 1) }) }
                    }
                }
            }
        }
    }
}
