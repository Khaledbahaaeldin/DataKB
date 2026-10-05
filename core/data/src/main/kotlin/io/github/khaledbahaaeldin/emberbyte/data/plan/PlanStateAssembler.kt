package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.Granularity
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsageFilter
import io.github.khaledbahaaeldin.emberbyte.engine.plan.CycleCalculator
import io.github.khaledbahaaeldin.emberbyte.engine.plan.PlanCalculator
import io.github.khaledbahaaeldin.emberbyte.engine.plan.UsageSlice
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import io.github.khaledbahaaeldin.emberbyte.engine.usage.SeriesBuilder
import java.time.Instant
import java.time.ZoneId

internal data class PlanRows(
    val minutes: List<MinuteTotal>,
    val hourly: List<HourlyUsage>,
    val meta: List<AppMeta>,
    val gaps: List<CoverageGap>,
)

internal object PlanStateAssembler {
    const val HISTORY_DAYS = 28L
    private val APPROXIMATE_GAPS = setOf(GapReason.SERVICE_KILLED, GapReason.REBOOT, GapReason.PERMISSION_MISSING)

    private fun zoneOf(plan: Plan): ZoneId = PlanCalculator.zoneOf(plan.cycle)

    /** Earliest instant whose rows are needed: the forecast history or the previous window, whichever is older, plus one platform window. */
    fun rowsFrom(plan: Plan, now: Instant): Instant {
        val zone = zoneOf(plan)
        val historyStart = now.atZone(zone).toLocalDate().minusDays(HISTORY_DAYS).atStartOfDay(zone).toInstant()
        val previous = CycleCalculator.previousWindow(plan.cycle, CycleCalculator.windowAt(plan.cycle, now))
        return minOf(historyStart, previous.start).minusSeconds(SeriesBuilder.WINDOW_SECONDS)
    }

    fun state(plan: Plan, addOns: List<AddOn>, rules: List<FreeRule>, now: Instant, rows: PlanRows): PlanState {
        val window = CycleCalculator.windowAt(plan.cycle, now)
        val slices = slices(plan, rules, window, rows)
        val previousUnused = if (plan.rollover == Rollover.None) 0L else {
            val previous = CycleCalculator.previousWindow(plan.cycle, window)
            // No chained rollover: the previous cycle is evaluated as if it had none (contract 10.1).
            PlanCalculator.state(
                plan.copy(rollover = Rollover.None), previous.end.minusMillis(1),
                slices(plan, rules, previous, rows), emptyList(), rules, 0L, false,
            ).remainingBytes
        }
        return PlanCalculator.state(plan, now, slices, addOns, rules, previousUnused, approximate(plan, window, now, rows))
    }

    fun history(plan: Plan, now: Instant, rows: PlanRows): List<DayUsage> {
        val zone = zoneOf(plan)
        val today = now.atZone(zone).toLocalDate()
        val from = today.minusDays(HISTORY_DAYS).atStartOfDay(zone).toInstant()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant()
        val points = SeriesBuilder.build(
            DateRange(from, to), Granularity.DAY, zone, rows.minutes, rows.hourly,
            UsageFilter(NetworkKind.MOBILE, plan.subscriptionId),
        )
        return points.map { DayUsage(it.start.atZone(zone).toLocalDate(), it.mobileBytes, 0L) }.dropWhile { it.mobileBytes == 0L }
    }

    private fun slices(plan: Plan, rules: List<FreeRule>, window: CycleWindow, rows: PlanRows): List<UsageSlice> {
        val filter = UsageFilter(NetworkKind.MOBILE, plan.subscriptionId)
        val hours = SeriesBuilder.build(DateRange(window.start, window.end), Granularity.HOUR, zoneOf(plan), rows.minutes, rows.hourly, filter)
        val total = hours.filter { it.mobileBytes > 0L }.map { UsageSlice(it.start, it.start.plusSeconds(3_600), null, it.mobileBytes) }
        if (rules.none { it.packageName != null }) return total
        // Per-app slices are the 2-hour window rows (the platform's resolution); only package-scoped free rules use them.
        val packageByUid = rows.meta.associate { it.uid to it.packageName }
        val perApp = rows.hourly
            .filter { it.network == NetworkKind.MOBILE && (plan.subscriptionId == null || it.subscriptionId == plan.subscriptionId) }
            .groupBy { it.hourStart to packageByUid[it.uid] }
            .mapNotNull { (key, list) ->
                val pkg = key.second ?: return@mapNotNull null
                UsageSlice(key.first, key.first.plusSeconds(SeriesBuilder.WINDOW_SECONDS), pkg, list.sumOf { it.totalBytes })
            }
            .filter { it.bytes > 0L }
        return total + perApp
    }

    private fun approximate(plan: Plan, window: CycleWindow, now: Instant, rows: PlanRows): Boolean {
        val to = minOf(now, window.end)
        val bound = plan.subscriptionId
        val otherSim = bound != null && rows.minutes.any {
            it.network == NetworkKind.MOBILE && it.totalBytes > 0L && it.minuteStart >= window.start && it.minuteStart < to && it.subscriptionId != bound
        }
        val gap = rows.gaps.any { it.reason in APPROXIMATE_GAPS && it.to > window.start && it.from < to }
        val unknownSimForBoundPlan = bound != null && (
            rows.minutes.any {
                it.network == NetworkKind.MOBILE && it.totalBytes > 0L && it.minuteStart >= window.start && it.minuteStart < to && it.subscriptionId == -1
            } || rows.hourly.any {
                it.network == NetworkKind.MOBILE && it.totalBytes > 0L && it.hourStart >= SeriesBuilder.windowStart(window.start) && it.hourStart < to && it.subscriptionId == -1
            }
        )
        val startsBeforeFirstMeasurement = run {
            val earliest = listOfNotNull(rows.minutes.minOfOrNull { it.minuteStart }, rows.hourly.minOfOrNull { it.hourStart }).minOrNull()
            earliest != null && earliest > window.start.plusSeconds(SeriesBuilder.WINDOW_SECONDS)
        }
        return otherSim || gap || unknownSimForBoundPlan || startsBeforeFirstMeasurement
    }
}
