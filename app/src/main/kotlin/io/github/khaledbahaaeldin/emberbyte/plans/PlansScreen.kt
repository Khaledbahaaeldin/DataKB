package io.github.khaledbahaaeldin.emberbyte.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.khaledbahaaeldin.emberbyte.ui.design.plan.PlanCard
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.BentoTile

@Composable
fun PlansScreen(
    viewModel: PlansViewModel,
    onOpenPlan: (Long) -> Unit,
    onNewPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlansContent(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenPlan = onOpenPlan,
        onNewPlan = onNewPlan,
        modifier = modifier,
    )
}

@Composable
internal fun PlansContent(
    state: PlansUiState,
    onEvent: (PlansEvent) -> Unit,
    onOpenPlan: (Long) -> Unit,
    onNewPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Plans", style = MaterialTheme.typography.headlineMedium)
                FilledTonalButton(
                    onClick = onNewPlan,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text("New plan")
                }
            }
        }
        if (state.loaded && state.cards.isEmpty() && state.archivedCards.isEmpty()) {
            item {
                BentoTile(title = "No plans yet", modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Add your cap and renewal day to see what is left and when it runs out.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    Button(
                        onClick = onNewPlan,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Text("Set up your first plan")
                    }
                }
            }
        } else {
            items(state.cards, key = { it.id }) { card ->
                PlanCard(model = card, onClick = { onOpenPlan(card.id) })
            }
            if (state.archivedCards.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = { onEvent(PlansEvent.ToggleArchived) },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Text(if (state.showArchived) "Hide archived" else "Show archived (${state.archivedCards.size})")
                    }
                }
                if (state.showArchived) {
                    items(state.archivedCards, key = { it.id }) { card ->
                        PlanCard(model = card, onClick = { onOpenPlan(card.id) })
                    }
                }
            }
        }
    }
}
