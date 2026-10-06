package io.github.khaledbahaaeldin.emberbyte.plans

import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PlanCardUi
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class CapUnit(val label: String, val power: Int) { MB("MB", 2), GB("GB", 3), TB("TB", 4) }

enum class CycleKind { Monthly, EveryNDays }

enum class RolloverKind { None, Full, Capped }

data class AddOnDraft(
    val id: Long = 0,
    val label: String = "",
    val amountText: String = "",
    val unit: CapUnit = CapUnit.GB,
    val validFrom: LocalDate,
    val validUntil: LocalDate,
)

data class FreeRuleDraft(
    val id: Long = 0,
    val label: String = "",
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val start: LocalTime = LocalTime.MIDNIGHT,
    val end: LocalTime = LocalTime.of(6, 0),
    val packageName: String? = null,
)

data class PlanEditorForm(
    val name: String = "",
    val subscriptionId: Int? = null,
    val capText: String = "",
    val capUnit: CapUnit = CapUnit.GB,
    val cycleKind: CycleKind = CycleKind.Monthly,
    val dayOfMonth: Int = 1,
    val everyDays: Int = 30,
    val anchor: LocalDate = LocalDate.of(2026, 1, 1),
    val renewalTime: LocalTime = LocalTime.MIDNIGHT,
    val rolloverKind: RolloverKind = RolloverKind.None,
    val rolloverCapText: String = "",
    val rolloverCapUnit: CapUnit = CapUnit.GB,
    val addOns: List<AddOnDraft> = emptyList(),
    val freeRules: List<FreeRuleDraft> = emptyList(),
    val alertThresholds: Set<Int> = setOf(80, 100),
)

data class SimOptionUi(val subscriptionId: Int?, val label: String)

data class PlanEditorUiState(
    val form: PlanEditorForm = PlanEditorForm(),
    val planId: Long = 0,
    val isNew: Boolean = true,
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val sims: List<SimOptionUi> = emptyList(),
    val needsPhonePermission: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val preview: PlanCardUi? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val unitSystem: UnitSystem = UnitSystem.DECIMAL,
    val whatIf: WhatIfUi? = null,
    val isDirty: Boolean = false,
)

sealed interface PlanEditorEvent {
    data class SetName(val value: String) : PlanEditorEvent
    data class SetSim(val subscriptionId: Int?) : PlanEditorEvent
    data class SetCap(val text: String) : PlanEditorEvent
    data class SetCapUnit(val unit: CapUnit) : PlanEditorEvent
    data class SetWhatIfAmount(val text: String) : PlanEditorEvent
    data class SetWhatIfUnit(val unit: CapUnit) : PlanEditorEvent
    data class SetCycleKind(val kind: CycleKind) : PlanEditorEvent
    data class SetDayOfMonth(val day: Int) : PlanEditorEvent
    data class SetEveryDays(val days: Int) : PlanEditorEvent
    data class SetAnchor(val date: LocalDate) : PlanEditorEvent
    data class SetRenewalTime(val time: LocalTime) : PlanEditorEvent
    data class SetRolloverKind(val kind: RolloverKind) : PlanEditorEvent
    data class SetRolloverCap(val text: String) : PlanEditorEvent
    data class SetRolloverUnit(val unit: CapUnit) : PlanEditorEvent
    data object AddAddOn : PlanEditorEvent
    data class UpdateAddOn(val index: Int, val draft: AddOnDraft) : PlanEditorEvent
    data class RemoveAddOn(val index: Int) : PlanEditorEvent
    data object AddFreeRule : PlanEditorEvent
    data class UpdateFreeRule(val index: Int, val draft: FreeRuleDraft) : PlanEditorEvent
    data class RemoveFreeRule(val index: Int) : PlanEditorEvent
    data class ToggleThreshold(val percent: Int) : PlanEditorEvent
    data object Save : PlanEditorEvent
    data object Archive : PlanEditorEvent
}
