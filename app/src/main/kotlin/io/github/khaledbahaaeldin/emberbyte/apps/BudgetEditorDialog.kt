package io.github.khaledbahaaeldin.emberbyte.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.plans.CapUnit
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ForceLtr

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetEditorDialog(
    editor: BudgetEditorUi,
    planOptions: List<PlanOptionUi>,
    onEvent: (AppDetailEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(AppDetailEvent.CloseBudgetEditor) },
        title = { Text(if (editor.isEdit) "Edit budget" else "Set budget") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Limit field
                val limitError = editor.errors["limitBytes"]
                OutlinedTextField(
                    value = editor.limitText,
                    onValueChange = { onEvent(AppDetailEvent.SetBudgetLimit(it)) },
                    label = { Text("Limit") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = limitError != null,
                    supportingText = {
                        limitError?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            if (limitError != null) {
                                error(limitError)
                            }
                        },
                )

                // MB / GB chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(CapUnit.MB, CapUnit.GB).forEach { unit ->
                        FilterChip(
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                            selected = editor.unit == unit,
                            onClick = { onEvent(AppDetailEvent.SetBudgetUnit(unit)) },
                            label = {
                                ForceLtr {
                                    Text(unit.label)
                                }
                            },
                        )
                    }
                }

                // Period chips: Daily / Plan cycle
                Text("Period", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        selected = editor.period == BudgetPeriod.DAILY,
                        onClick = { onEvent(AppDetailEvent.SetBudgetPeriod(BudgetPeriod.DAILY)) },
                        label = { Text("Daily") },
                    )
                    FilterChip(
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        selected = editor.period == BudgetPeriod.CYCLE,
                        onClick = { onEvent(AppDetailEvent.SetBudgetPeriod(BudgetPeriod.CYCLE)) },
                        label = { Text("Plan cycle") },
                    )
                }

                // If CYCLE, radio rows
                if (editor.period == BudgetPeriod.CYCLE) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Calendar month (planId = null)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .sizeIn(minHeight = 48.dp)
                                .selectable(
                                    selected = editor.planId == null,
                                    role = Role.RadioButton,
                                    onClick = { onEvent(AppDetailEvent.SetBudgetPlan(null)) },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = editor.planId == null,
                                onClick = null,
                            )
                            Text("Calendar month", modifier = Modifier.padding(start = 8.dp))
                        }

                        // Each plan option
                        planOptions.forEach { opt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .sizeIn(minHeight = 48.dp)
                                    .selectable(
                                        selected = editor.planId == opt.id,
                                        role = Role.RadioButton,
                                        onClick = { onEvent(AppDetailEvent.SetBudgetPlan(opt.id)) },
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = editor.planId == opt.id,
                                    onClick = null,
                                )
                                Text(opt.name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }

                        editor.errors["planId"]?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Warn chips: 50%, 80%, 90%
                Text("Warn at", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(50, 80, 90).forEach { pct ->
                        FilterChip(
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                            selected = editor.warnAtPercent == pct,
                            onClick = { onEvent(AppDetailEvent.SetBudgetWarn(pct)) },
                            label = {
                                ForceLtr {
                                    Text("$pct%")
                                }
                            },
                        )
                    }
                }
                editor.errors["warnAtPercent"]?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                editor.errors["form"]?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(AppDetailEvent.SaveBudget) },
                enabled = !editor.saving,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(AppDetailEvent.CloseBudgetEditor) },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text("Cancel")
            }
        },
    )
}
