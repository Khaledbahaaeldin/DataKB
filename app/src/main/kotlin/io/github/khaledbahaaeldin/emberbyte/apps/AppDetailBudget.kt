package io.github.khaledbahaaeldin.emberbyte.apps

import io.github.khaledbahaaeldin.emberbyte.common.plainBytes
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetStatus
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.plans.CapUnit
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BudgetStateUi
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BudgetUi(
    val id: Long,
    val fraction: Float,
    val ringState: BudgetStateUi,
    val percentText: String,
    val usedText: String,
    val periodText: String,
    val enabled: Boolean,
)

data class SpikeRowUi(
    val id: Long,
    val whenText: String,
    val bytesText: String,
    val multipleText: String,
)

data class PlanOptionUi(
    val id: Long,
    val name: String,
)

data class BudgetEditorUi(
    val limitText: String,
    val unit: CapUnit,
    val period: BudgetPeriod,
    val planId: Long?,
    val warnAtPercent: Int,
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val isEdit: Boolean = false,
)

sealed interface AppDetailEvent {
    data object OpenBudgetEditor : AppDetailEvent
    data object CloseBudgetEditor : AppDetailEvent
    data class SetBudgetLimit(val text: String) : AppDetailEvent
    data class SetBudgetUnit(val unit: CapUnit) : AppDetailEvent
    data class SetBudgetPeriod(val period: BudgetPeriod) : AppDetailEvent
    data class SetBudgetPlan(val planId: Long?) : AppDetailEvent
    data class SetBudgetWarn(val percent: Int) : AppDetailEvent
    data object SaveBudget : AppDetailEvent
    data object RemoveBudget : AppDetailEvent
    data class DismissSpike(val id: Long) : AppDetailEvent
}

internal fun budgetUi(status: BudgetStatus, planName: String?, units: ByteUnits): BudgetUi {
    val b = status.budget
    val percent = if (b.limitBytes <= 0L) 0 else (status.usedBytes * 100 / b.limitBytes).coerceAtMost(9_999L).toInt()
    return BudgetUi(
        id = b.id,
        fraction = if (b.limitBytes <= 0L) 0f else status.usedBytes.toFloat() / b.limitBytes,
        ringState = when (status.state) {
            BudgetState.OK -> BudgetStateUi.Ok
            BudgetState.WARNING -> BudgetStateUi.Warning
            BudgetState.EXCEEDED -> BudgetStateUi.Exceeded
        },
        percentText = "$percent%",
        usedText = "${plainBytes(status.usedBytes, units)} of ${plainBytes(b.limitBytes, units)}",
        periodText = when {
            b.period == BudgetPeriod.DAILY -> "today"
            planName != null -> "this cycle ($planName)"
            else -> "this month"
        },
        enabled = b.enabled,
    )
}

internal fun spikeRowUi(
    event: SpikeEvent,
    units: ByteUnits,
    zone: ZoneId,
    locale: Locale,
    is24Hour: Boolean,
): SpikeRowUi {
    val startZoned = event.windowStart.atZone(zone)
    val endZoned = event.windowStart.plusSeconds(event.windowMinutes.toLong() * 60).atZone(zone)
    val timePattern = if (is24Hour) "HH:mm" else "h:mm a"
    val timeFormatter = DateTimeFormatter.ofPattern(timePattern, locale)
    val weekday = DateTimeFormatter.ofPattern("EEE", locale).format(startZoned)
    val from = timeFormatter.format(startZoned)
    val to = timeFormatter.format(endZoned)
    return SpikeRowUi(
        id = event.id,
        whenText = "$weekday $from–$to",
        bytesText = plainBytes(event.bytes, units),
        multipleText = "${String.format(Locale.ROOT, "%.1f", event.multiple)}x its usual",
    )
}
