package io.github.khaledbahaaeldin.emberbyte.apps

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.khaledbahaaeldin.emberbyte.common.plainBytes
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppSort
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ForceLtr
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PermissionPromptUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.AppRow
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.PermissionPrompt

private val USAGE_ACCESS_PROMPT = PermissionPromptUi(
    id = "usage_access",
    title = "Allow usage access",
    body = "Per-app numbers need usage access. Totals keep working without it.",
    actionLabel = "Open settings",
)

@Composable
fun AppsScreen(
    viewModel: AppsViewModel,
    onOpenApp: (String) -> Unit,
    onPromptAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppsContent(state, viewModel::onEvent, onOpenApp, onPromptAction, modifier)
}

@Composable
internal fun AppsContent(
    state: AppsUiState,
    onEvent: (AppsEvent) -> Unit,
    onOpenApp: (String) -> Unit,
    onPromptAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Apps", style = MaterialTheme.typography.headlineMedium) }
        item {
            ChipRow {
                ChoiceChip("Today", state.range == AppsRange.TODAY) { onEvent(AppsEvent.SetRange(AppsRange.TODAY)) }
                ChoiceChip("7 days", state.range == AppsRange.LAST_7_DAYS) { onEvent(AppsEvent.SetRange(AppsRange.LAST_7_DAYS)) }
                ChoiceChip("This month", state.range == AppsRange.THIS_MONTH) { onEvent(AppsEvent.SetRange(AppsRange.THIS_MONTH)) }
            }
        }
        item {
            ChipRow {
                ChoiceChip("All", state.network == AppsNetwork.ALL) { onEvent(AppsEvent.SetNetwork(AppsNetwork.ALL)) }
                ChoiceChip("Mobile", state.network == AppsNetwork.MOBILE) { onEvent(AppsEvent.SetNetwork(AppsNetwork.MOBILE)) }
                ChoiceChip("Wi-Fi", state.network == AppsNetwork.WIFI) { onEvent(AppsEvent.SetNetwork(AppsNetwork.WIFI)) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onEvent(AppsEvent.SetQuery(it)) },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                SortMenu(state.sort) { onEvent(AppsEvent.SetSort(it)) }
            }
        }
        if (state.needsUsageAccess) {
            item { PermissionPrompt(USAGE_ACCESS_PROMPT, onAction = { onPromptAction(USAGE_ACCESS_PROMPT.id) }) }
        }
        item {
            ForceLtr {
                Text(
                    "${plainBytes(state.totalBytes, state.units)} in total",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.loaded && state.apps.isEmpty()) {
            item {
                Text(
                    "No app data yet. Per-app numbers appear within about 15 minutes once usage access is allowed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(state.apps, key = { it.packageName }) { app ->
                AppRow(app, state.units, onClick = { onOpenApp(app.packageName) })
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun SortMenu(sort: AppSort, onSort: (AppSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("Sort: ${sortLabel(sort)}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(AppSort.BYTES_DESC, AppSort.BYTES_ASC, AppSort.NAME).forEach { option ->
                DropdownMenuItem(text = { Text(sortLabel(option)) }, onClick = { onSort(option); open = false })
            }
        }
    }
}

private fun sortLabel(sort: AppSort): String = when (sort) {
    AppSort.BYTES_DESC, AppSort.SCREEN_TIME_DESC -> "Most data"
    AppSort.BYTES_ASC -> "Least data"
    AppSort.NAME -> "Name"
}
