package io.github.khaledbahaaeldin.emberbyte.plans

import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneId

internal fun bytesOf(text: String, unit: CapUnit, system: UnitSystem): Long? {
    val value = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    if (value.signum() <= 0) return null
    val base = if (system == UnitSystem.BINARY) 1024L else 1000L
    val bytes = value.multiply(BigDecimal.valueOf(base).pow(unit.power)).setScale(0, RoundingMode.HALF_UP)
    return if (bytes > BigDecimal.valueOf(Long.MAX_VALUE)) null else bytes.toLong()
}

internal fun textAndUnitOf(bytes: Long, system: UnitSystem): Pair<String, CapUnit> {
    val base = if (system == UnitSystem.BINARY) 1024L else 1000L
    fun scale(unit: CapUnit): Long = BigDecimal.valueOf(base).pow(unit.power).toLong()
    // The largest unit in which the value is at least 1 and exact to three decimals; otherwise MB with up to 8 decimals,
    // which reads back to the same number of bytes (a saved cap must never drift when it is loaded and saved again).
    val unit = CapUnit.entries.sortedByDescending { it.power }.firstOrNull { bytes >= scale(it) && (bytes * 1000) % scale(it) == 0L }
    if (unit != null) {
        return BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(scale(unit)), 3, RoundingMode.UNNECESSARY).stripTrailingZeros().toPlainString() to unit
    }
    return BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(scale(CapUnit.MB)), 8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() to CapUnit.MB
}

internal sealed interface FormResult {
    data class Ok(val plan: Plan, val addOns: List<AddOn>, val freeRules: List<FreeRule>) : FormResult
    data class Errors(val errors: Map<String, String>) : FormResult
}

internal fun PlanEditorForm.toDomain(planId: Long, zone: ZoneId, system: UnitSystem, archived: Boolean): FormResult {
    val errors = LinkedHashMap<String, String>()
    val cap = bytesOf(capText, capUnit, system)
    if (cap == null) errors["capBytes"] = "Enter a number greater than zero"
    val rollover: Rollover = when (rolloverKind) {
        RolloverKind.None -> Rollover.None
        RolloverKind.Full -> Rollover.Full
        RolloverKind.Capped -> bytesOf(rolloverCapText, rolloverCapUnit, system)?.let { Rollover.Capped(it) }
            ?: run { errors["rollover.maxBytes"] = "Enter a number greater than zero"; Rollover.None }
    }
    val addOnBytes = addOns.mapIndexed { index, draft ->
        bytesOf(draft.amountText, draft.unit, system).also { if (it == null) errors["addOn.$index.bytes"] = "Enter a number greater than zero" }
    }
    if (errors.isNotEmpty()) return FormResult.Errors(errors)
    val cycle = when (cycleKind) {
        CycleKind.Monthly -> Cycle.MonthlyOnDay(dayOfMonth, renewalTime, zone)
        CycleKind.EveryNDays -> Cycle.EveryNDays(everyDays, anchor, renewalTime, zone)
    }
    val plan = Plan(planId, name, subscriptionId, cap!!, cycle, rollover, archived)
    val addOnDomain = addOns.mapIndexed { index, d ->
        AddOn(d.id, planId, d.label, addOnBytes[index]!!, d.validFrom.atStartOfDay(zone).toInstant(), d.validUntil.plusDays(1).atStartOfDay(zone).toInstant())
    }
    val ruleDomain = freeRules.map { FreeRule(it.id, planId, it.label, it.days, it.start, it.end, it.packageName?.trim()?.ifEmpty { null }) }
    return FormResult.Ok(plan, addOnDomain, ruleDomain)
}
