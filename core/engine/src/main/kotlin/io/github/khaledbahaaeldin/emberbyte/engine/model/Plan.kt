package io.github.khaledbahaaeldin.emberbyte.engine.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class Plan(
    val id: Long, // 0 = not yet persisted
    val name: String,
    val subscriptionId: Int?, // null = not bound to a SIM
    val capBytes: Long,
    val cycle: Cycle,
    val rollover: Rollover,
    val archived: Boolean = false,
)

sealed interface Cycle {
    /** Renews on the same day each month; days beyond the month length clamp to the last day. */
    data class MonthlyOnDay(val dayOfMonth: Int, val time: LocalTime, val zone: ZoneId) : Cycle

    /** Renews every N days from an anchor date. */
    data class EveryNDays(val days: Int, val anchor: LocalDate, val time: LocalTime, val zone: ZoneId) : Cycle
}

sealed interface Rollover {
    data object None : Rollover
    data object Full : Rollover
    data class Capped(val maxBytes: Long) : Rollover
}

data class AddOn(
    val id: Long,
    val planId: Long,
    val label: String,
    val bytes: Long,
    val validFrom: Instant,
    val validUntil: Instant,
)

/** Traffic matching a rule is NOT counted against the plan. A window may cross midnight. */
data class FreeRule(
    val id: Long,
    val planId: Long,
    val label: String,
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
    val packageName: String?, // null = all apps
)

data class CycleWindow(val start: Instant, val end: Instant)

data class PlanState(
    val plan: Plan,
    val window: CycleWindow,
    val effectiveCapBytes: Long,
    val usedBytes: Long,
    val remainingBytes: Long,
    val rolledOverBytes: Long,
    val addOnBytes: Long,
    val freeBytes: Long,
    val daysLeft: Int,
    val fractionUsed: Float,
    val isApproximate: Boolean,
)
