package io.github.khaledbahaaeldin.emberbyte.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.khaledbahaaeldin.emberbyte.common.ScreenHeader
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ForceLtr
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PermissionPromptUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.plan.PlanCard
import io.github.khaledbahaaeldin.emberbyte.ui.design.tiles.PermissionPrompt
import java.time.DayOfWeek

@Composable
fun PlanEditorScreen(
    viewModel: PlanEditorViewModel,
    onBack: () -> Unit,
    onPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }
    PlanEditorContent(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onPrompt = onPrompt,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlanEditorContent(
    state: PlanEditorUiState,
    onEvent: (PlanEditorEvent) -> Unit,
    onBack: () -> Unit,
    onPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(if (state.isNew) "New plan" else "Edit plan", onBack)

        when {
            state.loading -> {
                Text("Loading plan")
            }
            state.notFound -> {
                Text("This plan no longer exists")
                Button(onClick = onBack) {
                    Text("Back")
                }
            }
            else -> {
                if (state.preview != null) {
                    Text("Preview", style = MaterialTheme.typography.titleMedium)
                    PlanCard(model = state.preview, onClick = {})
                }

                if (state.whatIf != null) {
                    Text("What if I use more?", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.whatIf.amountText,
                        onValueChange = { onEvent(PlanEditorEvent.SetWhatIfAmount(it)) },
                        label = { Text("Extra per day") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.whatIf.error != null,
                        supportingText = state.whatIf.error?.let { { Text(it) } },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (unit in listOf(CapUnit.MB, CapUnit.GB)) {
                            FilterChip(
                                selected = state.whatIf.unit == unit,
                                onClick = { onEvent(PlanEditorEvent.SetWhatIfUnit(unit)) },
                                label = { Text(unit.label, softWrap = false) },
                            )
                        }
                    }
                    ForceLtr {
                        Text("Now: ${state.whatIf.currentText}")
                    }
                    if (state.whatIf.whatIfText != null) {
                        ForceLtr {
                            Text("With extra: ${state.whatIf.whatIfText}")
                        }
                    }
                }

                OutlinedTextField(
                    value = state.form.name,
                    onValueChange = { onEvent(PlanEditorEvent.SetName(it)) },
                    label = { Text("Plan name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = state.errors.containsKey("name"),
                    supportingText = state.errors["name"]?.let { { Text(it) } },
                )

                Text("SIM", style = MaterialTheme.typography.titleMedium)
                if (state.needsPhonePermission) {
                    PermissionPrompt(
                        model = PermissionPromptUi(
                            id = "phone_state",
                            title = "Allow phone access",
                            body = "Lets Emberbyte list your SIMs so a plan can follow one SIM. You can also use the default data SIM below.",
                            actionLabel = "Allow",
                        ),
                        onAction = { onPrompt("phone_state") },
                    )
                }
                for (sim in state.sims) {
                    val isSelected = state.form.subscriptionId == sim.subscriptionId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onEvent(PlanEditorEvent.SetSim(sim.subscriptionId)) },
                            )
                            .semantics(mergeDescendants = true) {}
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                        )
                        Text(sim.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                OutlinedTextField(
                    value = state.form.capText,
                    onValueChange = { onEvent(PlanEditorEvent.SetCap(it)) },
                    label = { Text("Cap") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = state.errors.containsKey("capBytes"),
                    supportingText = state.errors["capBytes"]?.let { { Text(it) } },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (unit in CapUnit.entries) {
                        FilterChip(
                            selected = state.form.capUnit == unit,
                            onClick = { onEvent(PlanEditorEvent.SetCapUnit(unit)) },
                            label = { Text(unit.label, softWrap = false) },
                        )
                    }
                }

                Text("Cycle", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.form.cycleKind == CycleKind.Monthly,
                        onClick = { onEvent(PlanEditorEvent.SetCycleKind(CycleKind.Monthly)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text("Monthly")
                    }
                    SegmentedButton(
                        selected = state.form.cycleKind == CycleKind.EveryNDays,
                        onClick = { onEvent(PlanEditorEvent.SetCycleKind(CycleKind.EveryNDays)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text("Every N days")
                    }
                }

                when (state.form.cycleKind) {
                    CycleKind.Monthly -> {
                        OutlinedTextField(
                            value = if (state.form.dayOfMonth == 0) "" else state.form.dayOfMonth.toString(),
                            onValueChange = { input ->
                                val day = input.filter { it.isDigit() }.toIntOrNull() ?: 0
                                onEvent(PlanEditorEvent.SetDayOfMonth(day))
                            },
                            label = { Text("Day of month") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = state.errors.containsKey("cycle.dayOfMonth"),
                            supportingText = state.errors["cycle.dayOfMonth"]?.let { { Text(it) } },
                        )
                    }
                    CycleKind.EveryNDays -> {
                        OutlinedTextField(
                            value = if (state.form.everyDays == 0) "" else state.form.everyDays.toString(),
                            onValueChange = { input ->
                                val days = input.filter { it.isDigit() }.toIntOrNull() ?: 0
                                onEvent(PlanEditorEvent.SetEveryDays(days))
                            },
                            label = { Text("Number of days") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = state.errors.containsKey("cycle.days"),
                            supportingText = state.errors["cycle.days"]?.let { { Text(it) } },
                        )
                        DateField(
                            label = "Starts on",
                            date = state.form.anchor,
                            onChange = { onEvent(PlanEditorEvent.SetAnchor(it)) },
                        )
                    }
                }
                TimeField(
                    label = "Renews at",
                    time = state.form.renewalTime,
                    onChange = { onEvent(PlanEditorEvent.SetRenewalTime(it)) },
                )

                Text("Rollover", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.form.rolloverKind == RolloverKind.None,
                        onClick = { onEvent(PlanEditorEvent.SetRolloverKind(RolloverKind.None)) },
                        label = { Text("None") },
                    )
                    FilterChip(
                        selected = state.form.rolloverKind == RolloverKind.Full,
                        onClick = { onEvent(PlanEditorEvent.SetRolloverKind(RolloverKind.Full)) },
                        label = { Text("Full") },
                    )
                    FilterChip(
                        selected = state.form.rolloverKind == RolloverKind.Capped,
                        onClick = { onEvent(PlanEditorEvent.SetRolloverKind(RolloverKind.Capped)) },
                        label = { Text("Capped") },
                    )
                }
                if (state.form.rolloverKind == RolloverKind.Capped) {
                    OutlinedTextField(
                        value = state.form.rolloverCapText,
                        onValueChange = { onEvent(PlanEditorEvent.SetRolloverCap(it)) },
                        label = { Text("Maximum rollover") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.errors.containsKey("rollover.maxBytes"),
                        supportingText = state.errors["rollover.maxBytes"]?.let { { Text(it) } },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (unit in CapUnit.entries) {
                            FilterChip(
                                selected = state.form.rolloverCapUnit == unit,
                                onClick = { onEvent(PlanEditorEvent.SetRolloverUnit(unit)) },
                                label = { Text(unit.label, softWrap = false) },
                            )
                        }
                    }
                }

                Text("Add-on packs", style = MaterialTheme.typography.titleMedium)
                state.form.addOns.forEachIndexed { index, addOn ->
                    OutlinedTextField(
                        value = addOn.label,
                        onValueChange = { onEvent(PlanEditorEvent.UpdateAddOn(index, addOn.copy(label = it))) },
                        label = { Text("Label") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.errors.containsKey("addOn.$index.label"),
                        supportingText = state.errors["addOn.$index.label"]?.let { { Text(it) } },
                    )
                    OutlinedTextField(
                        value = addOn.amountText,
                        onValueChange = { onEvent(PlanEditorEvent.UpdateAddOn(index, addOn.copy(amountText = it))) },
                        label = { Text("Amount") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.errors.containsKey("addOn.$index.bytes"),
                        supportingText = state.errors["addOn.$index.bytes"]?.let { { Text(it) } },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (unit in CapUnit.entries) {
                            FilterChip(
                                selected = addOn.unit == unit,
                                onClick = { onEvent(PlanEditorEvent.UpdateAddOn(index, addOn.copy(unit = unit))) },
                                label = { Text(unit.label, softWrap = false) },
                            )
                        }
                    }
                    DateField(
                        label = "Valid from",
                        date = addOn.validFrom,
                        onChange = { onEvent(PlanEditorEvent.UpdateAddOn(index, addOn.copy(validFrom = it))) },
                    )
                    DateField(
                        label = "Valid until",
                        date = addOn.validUntil,
                        onChange = { onEvent(PlanEditorEvent.UpdateAddOn(index, addOn.copy(validUntil = it))) },
                    )
                    state.errors["addOn.$index.validUntil"]?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { onEvent(PlanEditorEvent.RemoveAddOn(index)) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove add-on pack")
                        }
                    }
                }
                OutlinedButton(
                    onClick = { onEvent(PlanEditorEvent.AddAddOn) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Add add-on pack")
                }

                Text("Free windows", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Traffic in a free window does not count toward the plan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.form.freeRules.forEachIndexed { index, rule ->
                    OutlinedTextField(
                        value = rule.label,
                        onValueChange = { onEvent(PlanEditorEvent.UpdateFreeRule(index, rule.copy(label = it))) },
                        label = { Text("Label") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.errors.containsKey("freeRule.$index.label"),
                        supportingText = state.errors["freeRule.$index.label"]?.let { { Text(it) } },
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val dayLabels = listOf(
                            DayOfWeek.MONDAY to "Mon",
                            DayOfWeek.TUESDAY to "Tue",
                            DayOfWeek.WEDNESDAY to "Wed",
                            DayOfWeek.THURSDAY to "Thu",
                            DayOfWeek.FRIDAY to "Fri",
                            DayOfWeek.SATURDAY to "Sat",
                            DayOfWeek.SUNDAY to "Sun",
                        )
                        for ((day, label) in dayLabels) {
                            FilterChip(
                                selected = day in rule.days,
                                onClick = {
                                    val nextDays = if (day in rule.days) rule.days - day else rule.days + day
                                    onEvent(PlanEditorEvent.UpdateFreeRule(index, rule.copy(days = nextDays)))
                                },
                                label = { Text(label, softWrap = false) },
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp),
                            )
                        }
                    }
                    state.errors["freeRule.$index.days"]?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TimeField(
                        label = "From",
                        time = rule.start,
                        onChange = { onEvent(PlanEditorEvent.UpdateFreeRule(index, rule.copy(start = it))) },
                    )
                    TimeField(
                        label = "To",
                        time = rule.end,
                        onChange = { onEvent(PlanEditorEvent.UpdateFreeRule(index, rule.copy(end = it))) },
                    )
                    state.errors["freeRule.$index.time"]?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedTextField(
                        value = rule.packageName.orEmpty(),
                        onValueChange = {
                            val pkg = it.ifEmpty { null }
                            onEvent(PlanEditorEvent.UpdateFreeRule(index, rule.copy(packageName = pkg)))
                        },
                        label = { Text("App package") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.errors.containsKey("freeRule.$index.packageName"),
                        supportingText = state.errors["freeRule.$index.packageName"]?.let { { Text(it) } },
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { onEvent(PlanEditorEvent.RemoveFreeRule(index)) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove free window")
                        }
                    }
                }
                OutlinedButton(
                    onClick = { onEvent(PlanEditorEvent.AddFreeRule) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Add free window")
                }

                Text("Alert me at", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (percent in listOf(50, 80, 90, 100)) {
                        FilterChip(
                            selected = percent in state.form.alertThresholds,
                            onClick = { onEvent(PlanEditorEvent.ToggleThreshold(percent)) },
                            label = { Text("$percent%", softWrap = false, maxLines = 1) },
                        )
                    }
                }

                val consumedKeys = buildSet {
                    add("name")
                    add("capBytes")
                    if (state.form.cycleKind == CycleKind.Monthly) add("cycle.dayOfMonth")
                    if (state.form.cycleKind == CycleKind.EveryNDays) add("cycle.days")
                    if (state.form.rolloverKind == RolloverKind.Capped) add("rollover.maxBytes")
                    state.form.addOns.indices.forEach { index ->
                        add("addOn.$index.label")
                        add("addOn.$index.bytes")
                        add("addOn.$index.validUntil")
                    }
                    state.form.freeRules.indices.forEach { index ->
                        add("freeRule.$index.label")
                        add("freeRule.$index.days")
                        add("freeRule.$index.time")
                        add("freeRule.$index.packageName")
                    }
                }
                val unconsumedErrors = state.errors.filterKeys { it !in consumedKeys }.toSortedMap()
                for ((_, unconsumedMessage) in unconsumedErrors) {
                    Text(
                        text = unconsumedMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Button(
                    onClick = { onEvent(PlanEditorEvent.Save) },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Save")
                }

                if (!state.isNew) {
                    TextButton(
                        onClick = { onEvent(PlanEditorEvent.Archive) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Archive plan")
                    }
                }
            }
        }
    }
}
