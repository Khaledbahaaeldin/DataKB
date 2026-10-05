package io.github.khaledbahaaeldin.emberbyte.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.khaledbahaaeldin.emberbyte.data.PermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.data.alert.AlertRepository
import io.github.khaledbahaaeldin.emberbyte.data.source.SimInfoSource
import io.github.khaledbahaaeldin.emberbyte.data.util.SystemZoneClock
import io.github.khaledbahaaeldin.emberbyte.engine.model.AlertRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.home.toByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PlanCardUi
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PlanEditorViewModel(
    private val planId: Long,
    private val plans: PlanRepository,
    private val alerts: AlertRepository,
    private val settings: SettingsRepository,
    private val permissions: PermissionRepository,
    private val sims: SimInfoSource,
    private val clock: Clock = SystemZoneClock(),
    private val locale: Locale = Locale.getDefault(),
    private val previewDebounceMillis: Long = 300L,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _state = MutableStateFlow(
        PlanEditorUiState(
            planId = planId,
            isNew = planId == 0L,
            form = savedStateHandle.get<String>(KEY_FORM)?.let { PlanEditorFormJson.decode(it) }
                ?: PlanEditorForm(anchor = LocalDate.now(clock)),
        ),
    )
    val uiState: StateFlow<PlanEditorUiState> = _state.asStateFlow()

    private var zone: ZoneId = clock.zone
    private var loadedAddOnIds = emptySet<Long>()
    private var loadedFreeRuleIds = emptySet<Long>()
    private var loadedAlertRules = emptyList<AlertRule>()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch { permissions.observe().collect { refreshSims(it.phoneState) } }
        viewModelScope.launch {
            _state.map { it.form to it.unitSystem }.distinctUntilChanged().debounce(previewDebounceMillis)
                .collectLatest { (form, system) -> _state.update { it.copy(preview = preview(form, system)) } }
        }
    }

    private suspend fun load() {
        val system = settings.observe().first().unitSystem
        _state.update { it.copy(unitSystem = system) }
        if (planId == 0L) {
            _state.update { it.copy(loading = false) }
            return
        }
        val allPlans = plans.observePlans(includeArchived = true).first()
        val plan = allPlans.firstOrNull { it.id == planId }
        if (plan == null) {
            _state.update { it.copy(loading = false, notFound = true) }
            return
        }
        val (capText, capUnit) = textAndUnitOf(plan.capBytes, system)
        val cycleFields = when (val c = plan.cycle) {
            is Cycle.MonthlyOnDay -> {
                zone = c.zone
                CycleFields(CycleKind.Monthly, c.dayOfMonth, 30, LocalDate.now(clock), c.time)
            }
            is Cycle.EveryNDays -> {
                zone = c.zone
                CycleFields(CycleKind.EveryNDays, 1, c.days, c.anchor, c.time)
            }
        }
        val (rolloverKind, rCapText, rCapUnit) = when (val r = plan.rollover) {
            Rollover.None -> Triple(RolloverKind.None, "", CapUnit.GB)
            Rollover.Full -> Triple(RolloverKind.Full, "", CapUnit.GB)
            is Rollover.Capped -> {
                val (t, u) = textAndUnitOf(r.maxBytes, system)
                Triple(RolloverKind.Capped, t, u)
            }
        }
        val addOns = plans.observeAddOns(planId).first()
        val addOnDrafts = addOns.map { a ->
            val (aText, aUnit) = textAndUnitOf(a.bytes, system)
            val fromDate = a.validFrom.atZone(zone).toLocalDate()
            val untilDate = a.validUntil.minusMillis(1).atZone(zone).toLocalDate()
            AddOnDraft(id = a.id, label = a.label, amountText = aText, unit = aUnit, validFrom = fromDate, validUntil = untilDate)
        }
        val freeRules = plans.observeFreeRules(planId).first()
        val freeRuleDrafts = freeRules.map { f ->
            FreeRuleDraft(id = f.id, label = f.label, days = f.days, start = f.start, end = f.end, packageName = f.packageName)
        }
        val alertRules = alerts.observeRules(planId).first()
        val thresholds = alertRules.map { it.thresholdPercent }.toSet()

        loadedAddOnIds = addOns.map { it.id }.toSet()
        loadedFreeRuleIds = freeRules.map { it.id }.toSet()
        loadedAlertRules = alertRules

        val hasRestored = savedStateHandle.get<String>(KEY_FORM)?.let { PlanEditorFormJson.decode(it) } != null
        if (!hasRestored) {
            val form = PlanEditorForm(
                name = plan.name,
                subscriptionId = plan.subscriptionId,
                capText = capText,
                capUnit = capUnit,
                cycleKind = cycleFields.kind,
                dayOfMonth = cycleFields.dayOfMonth,
                everyDays = cycleFields.everyDays,
                anchor = cycleFields.anchor,
                renewalTime = cycleFields.renewalTime,
                rolloverKind = rolloverKind,
                rolloverCapText = rCapText,
                rolloverCapUnit = rCapUnit,
                addOns = addOnDrafts,
                freeRules = freeRuleDrafts,
                alertThresholds = thresholds,
            )
            _state.update { it.copy(form = form, loading = false) }
        } else {
            _state.update { it.copy(loading = false) }
        }
    }

    private fun refreshSims(phoneState: Boolean) {
        val active = sims.activeSims().sortedBy { it.slotIndex }
        val defaultId = sims.defaultDataSubscriptionId()
        val options = mutableListOf(SimOptionUi(null, "Not tied to a SIM"))
        if (defaultId >= 0 && active.none { it.subscriptionId == defaultId }) {
            options.add(SimOptionUi(defaultId, "Default data SIM"))
        }
        active.forEach { sim ->
            options.add(SimOptionUi(sim.subscriptionId, "SIM ${sim.slotIndex + 1} · ${sim.displayName}"))
        }
        val needsPermission = !phoneState && active.isEmpty()
        _state.update { it.copy(sims = options, needsPhonePermission = needsPermission) }
    }

    private suspend fun preview(form: PlanEditorForm, system: UnitSystem): PlanCardUi? {
        val result = form.toDomain(_state.value.planId, zone, system, archived = false)
        if (result !is FormResult.Ok) return null
        val previewOutcome = plans.previewState(result.plan, result.addOns, result.freeRules)
        if (previewOutcome !is Outcome.Success) return null
        val preview = previewOutcome.value
        val units = system.toByteUnits()
        return planCardUi(
            state = preview.state,
            forecast = preview.forecast,
            addOns = result.addOns,
            simLabel = null,
            units = units,
            now = clock.instant(),
            zone = zone,
            locale = locale,
        )
    }

    private fun updateForm(transform: (PlanEditorForm) -> PlanEditorForm) {
        _state.update { current ->
            val nextForm = transform(current.form)
            savedStateHandle[KEY_FORM] = PlanEditorFormJson.encode(nextForm)
            current.copy(form = nextForm, errors = emptyMap())
        }
    }

    fun onEvent(event: PlanEditorEvent) {
        when (event) {
            is PlanEditorEvent.SetName -> updateForm { it.copy(name = event.value) }
            is PlanEditorEvent.SetSim -> updateForm { it.copy(subscriptionId = event.subscriptionId) }
            is PlanEditorEvent.SetCap -> updateForm { it.copy(capText = event.text) }
            is PlanEditorEvent.SetCapUnit -> updateForm { it.copy(capUnit = event.unit) }
            is PlanEditorEvent.SetCycleKind -> updateForm { it.copy(cycleKind = event.kind) }
            is PlanEditorEvent.SetDayOfMonth -> updateForm { it.copy(dayOfMonth = event.day) }
            is PlanEditorEvent.SetEveryDays -> updateForm { it.copy(everyDays = event.days) }
            is PlanEditorEvent.SetAnchor -> updateForm { it.copy(anchor = event.date) }
            is PlanEditorEvent.SetRenewalTime -> updateForm { it.copy(renewalTime = event.time) }
            is PlanEditorEvent.SetRolloverKind -> updateForm { it.copy(rolloverKind = event.kind) }
            is PlanEditorEvent.SetRolloverCap -> updateForm { it.copy(rolloverCapText = event.text) }
            is PlanEditorEvent.SetRolloverUnit -> updateForm { it.copy(rolloverCapUnit = event.unit) }
            PlanEditorEvent.AddAddOn -> {
                val today = LocalDate.now(clock)
                updateForm { it.copy(addOns = it.addOns + AddOnDraft(validFrom = today, validUntil = today.plusDays(30))) }
            }
            is PlanEditorEvent.UpdateAddOn -> {
                updateForm {
                    val list = it.addOns.toMutableList()
                    if (event.index in list.indices) {
                        list[event.index] = event.draft
                    }
                    it.copy(addOns = list)
                }
            }
            is PlanEditorEvent.RemoveAddOn -> {
                updateForm {
                    val list = it.addOns.toMutableList()
                    if (event.index in list.indices) {
                        list.removeAt(event.index)
                    }
                    it.copy(addOns = list)
                }
            }
            PlanEditorEvent.AddFreeRule -> {
                updateForm { it.copy(freeRules = it.freeRules + FreeRuleDraft()) }
            }
            is PlanEditorEvent.UpdateFreeRule -> {
                updateForm {
                    val list = it.freeRules.toMutableList()
                    if (event.index in list.indices) {
                        list[event.index] = event.draft
                    }
                    it.copy(freeRules = list)
                }
            }
            is PlanEditorEvent.RemoveFreeRule -> {
                updateForm {
                    val list = it.freeRules.toMutableList()
                    if (event.index in list.indices) {
                        list.removeAt(event.index)
                    }
                    it.copy(freeRules = list)
                }
            }
            is PlanEditorEvent.ToggleThreshold -> {
                updateForm {
                    val current = it.alertThresholds
                    val next = if (event.percent in current) current - event.percent else current + event.percent
                    it.copy(alertThresholds = next)
                }
            }
            PlanEditorEvent.Save -> {
                viewModelScope.launch { save() }
            }
            PlanEditorEvent.Archive -> {
                viewModelScope.launch { archive() }
            }
        }
    }

    private suspend fun save() {
        if (_state.value.saving) return
        val currentPlanId = _state.value.planId
        val domainResult = _state.value.form.toDomain(currentPlanId, zone, _state.value.unitSystem, archived = false)
        if (domainResult is FormResult.Errors) {
            _state.update { it.copy(errors = domainResult.errors) }
            return
        }
        val ok = domainResult as FormResult.Ok

        // Validate the entire draft first before writing anything. Saving is validated first
        // and then written step by step; it is NOT a database transaction.
        val draftErrors = plans.validateDraft(ok.plan, ok.addOns, ok.freeRules)
        if (draftErrors.isNotEmpty()) {
            _state.update { it.copy(errors = draftErrors) }
            return
        }

        _state.update { it.copy(saving = true) }

        when (val planOutcome = plans.upsertPlan(ok.plan)) {
            is Outcome.Failure -> {
                val err = when (val e = planOutcome.error) {
                    is EmberbyteError.Invalid -> mapOf(e.field to e.reason)
                    else -> mapOf("form" to "Could not save. Try again.")
                }
                _state.update { it.copy(errors = err, saving = false) }
                return
            }
            is Outcome.Success -> {
                val newPlanId = planOutcome.value
                _state.update { it.copy(planId = newPlanId, isNew = false) }
            }
        }
        val effectivePlanId = _state.value.planId

        // Delete add-ons whose id was loaded but is no longer in the form
        val currentFormAddOnIds = _state.value.form.addOns.map { it.id }.filter { it != 0L }.toSet()
        val toDeleteAddOns = loadedAddOnIds - currentFormAddOnIds
        for (delId in toDeleteAddOns) {
            when (plans.deleteAddOn(delId)) {
                is Outcome.Failure -> {
                    _state.update { it.copy(errors = mapOf("form" to "Could not save. Try again."), saving = false) }
                    return
                }
                is Outcome.Success -> {}
            }
        }

        // Upsert each add-on draft and store the returned id back into the draft
        for (i in ok.addOns.indices) {
            val domainAddOn = ok.addOns[i].copy(planId = effectivePlanId)
            when (val res = plans.upsertAddOn(domainAddOn)) {
                is Outcome.Failure -> {
                    _state.update { it.copy(errors = mapOf("form" to "Could not save. Try again."), saving = false) }
                    return
                }
                is Outcome.Success -> {
                    val newAddOnId = res.value
                    _state.update { s ->
                        val updatedAddOns = s.form.addOns.toMutableList()
                        if (i in updatedAddOns.indices) {
                            updatedAddOns[i] = updatedAddOns[i].copy(id = newAddOnId)
                        }
                        s.copy(form = s.form.copy(addOns = updatedAddOns))
                    }
                }
            }
        }

        // Delete free rules whose id was loaded but is no longer in the form
        val currentFormFreeRuleIds = _state.value.form.freeRules.map { it.id }.filter { it != 0L }.toSet()
        val toDeleteFreeRules = loadedFreeRuleIds - currentFormFreeRuleIds
        for (delId in toDeleteFreeRules) {
            when (plans.deleteFreeRule(delId)) {
                is Outcome.Failure -> {
                    _state.update { it.copy(errors = mapOf("form" to "Could not save. Try again."), saving = false) }
                    return
                }
                is Outcome.Success -> {}
            }
        }

        // Upsert each free rule draft and store the returned id back into the draft
        for (i in ok.freeRules.indices) {
            val domainRule = ok.freeRules[i].copy(planId = effectivePlanId)
            when (val res = plans.upsertFreeRule(domainRule)) {
                is Outcome.Failure -> {
                    _state.update { it.copy(errors = mapOf("form" to "Could not save. Try again."), saving = false) }
                    return
                }
                is Outcome.Success -> {
                    val newRuleId = res.value
                    _state.update { s ->
                        val updatedRules = s.form.freeRules.toMutableList()
                        if (i in updatedRules.indices) {
                            updatedRules[i] = updatedRules[i].copy(id = newRuleId)
                        }
                        s.copy(form = s.form.copy(freeRules = updatedRules))
                    }
                }
            }
        }

        // Thresholds: delete loaded rules whose threshold is no longer wanted, upsertRule(AlertRule(existingId or 0, id, threshold, true))
        val wantedThresholds = _state.value.form.alertThresholds
        val toDeleteAlerts = loadedAlertRules.filter { it.thresholdPercent !in wantedThresholds }
        for (rule in toDeleteAlerts) {
            when (alerts.deleteRule(rule.id)) {
                is Outcome.Failure -> {
                    _state.update { it.copy(errors = mapOf("form" to "Could not save. Try again."), saving = false) }
                    return
                }
                is Outcome.Success -> {}
            }
        }
        val updatedAlertRules = mutableListOf<AlertRule>()
        for (threshold in wantedThresholds) {
            val existing = loadedAlertRules.firstOrNull { it.thresholdPercent == threshold }
            val ruleId = existing?.id ?: 0L
            when (val res = alerts.upsertRule(AlertRule(ruleId, effectivePlanId, threshold, true))) {
                is Outcome.Failure -> {
                    _state.update { it.copy(errors = mapOf("form" to "Could not save. Try again."), saving = false) }
                    return
                }
                is Outcome.Success -> {
                    updatedAlertRules.add(AlertRule(res.value, effectivePlanId, threshold, true))
                }
            }
        }

        loadedAddOnIds = _state.value.form.addOns.map { it.id }.toSet()
        loadedFreeRuleIds = _state.value.form.freeRules.map { it.id }.toSet()
        loadedAlertRules = updatedAlertRules
        savedStateHandle.remove<String>(KEY_FORM)
        _state.update { it.copy(saving = false, saved = true) }
    }

    private suspend fun archive() {
        val currentPlanId = _state.value.planId
        if (currentPlanId == 0L) return
        when (plans.archivePlan(currentPlanId)) {
            is Outcome.Success -> {
                savedStateHandle.remove<String>(KEY_FORM)
                _state.update { it.copy(saved = true) }
            }
            is Outcome.Failure -> _state.update { it.copy(errors = mapOf("form" to "Could not archive. Try again.")) }
        }
    }

    private data class CycleFields(
        val kind: CycleKind,
        val dayOfMonth: Int,
        val everyDays: Int,
        val anchor: LocalDate,
        val renewalTime: LocalTime,
    )

    private companion object {
        const val KEY_FORM = "form"
    }
}
