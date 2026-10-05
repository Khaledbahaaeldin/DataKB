package io.github.khaledbahaaeldin.emberbyte.engine.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

object PlanCalculator {
    fun state(
        plan: Plan,
        at: Instant,
        usage: List<UsageSlice>,
        addOns: List<AddOn>,
        freeRules: List<FreeRule>,
        previousCycleUnusedBytes: Long,
        isApproximate: Boolean,
    ): PlanState {
        val window = CycleCalculator.windowAt(plan.cycle, at)
        val totals = usage.filter { it.packageName == null }
        val perApp = usage.filter { it.packageName != null }
        val totalBytes = totals.sumOf { Slices.clippedBytes(it, window.start, window.end) }
        val freeBytes = FreeWindows.freeBytes(totals, perApp, freeRules, window, zoneOf(plan.cycle))
        val used = maxOf(0L, totalBytes - freeBytes)
        val rolledOver = when (val rollover = plan.rollover) {
            Rollover.None -> 0L
            Rollover.Full -> previousCycleUnusedBytes
            is Rollover.Capped -> minOf(previousCycleUnusedBytes, rollover.maxBytes)
        }.coerceAtLeast(0L)
        val addOnBytes = addOns.filter { it.validFrom < window.end && it.validUntil > window.start }.sumOf { it.bytes }
        val effective = plan.capBytes + rolledOver + addOnBytes
        val secondsLeft = Duration.between(at, window.end).seconds
        return PlanState(
            plan = plan,
            window = window,
            effectiveCapBytes = effective,
            usedBytes = used,
            remainingBytes = maxOf(0L, effective - used),
            rolledOverBytes = rolledOver,
            addOnBytes = addOnBytes,
            freeBytes = freeBytes,
            daysLeft = if (secondsLeft <= 0L) 0 else ((secondsLeft + 86_399L) / 86_400L).toInt(),   // Math.ceilDiv needs Java 18
            fractionUsed = if (effective > 0L) used.toFloat() / effective else 0f,
            isApproximate = isApproximate,
        )
    }

    fun zoneOf(cycle: Cycle): ZoneId = when (cycle) {
        is Cycle.MonthlyOnDay -> cycle.zone
        is Cycle.EveryNDays -> cycle.zone
    }
}

fun PlanState.percentUsed(): Int =
    if (effectiveCapBytes <= 0L) 0 else (usedBytes * 100 / effectiveCapBytes).coerceAtMost(9_999L).toInt()

