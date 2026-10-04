package io.github.khaledbahaaeldin.emberbyte.plans

import io.github.khaledbahaaeldin.emberbyte.common.forecastSentence
import io.github.khaledbahaaeldin.emberbyte.common.plainBytes
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.home.forecastToUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BudgetStateUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PlanCardUi
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

internal fun planCardUi(
    state: PlanState,
    forecast: Forecast?,
    addOns: List<AddOn>,
    simLabel: String?,
    units: ByteUnits,
    now: Instant,
    zone: ZoneId,
    locale: Locale,
): PlanCardUi {
    val percent = (state.fractionUsed * 100f).toInt().coerceAtLeast(0)
    return PlanCardUi(
        id = state.plan.id,
        name = state.plan.name,
        simLabel = simLabel,
        fraction = state.fractionUsed,
        ringState = when {
            state.fractionUsed >= 1f -> BudgetStateUi.Exceeded
            state.fractionUsed >= 0.8f -> BudgetStateUi.Warning
            else -> BudgetStateUi.Ok
        },
        percentText = "$percent%",
        usedText = "${plainBytes(state.usedBytes, units)} of ${plainBytes(state.effectiveCapBytes, units)}",
        remainingText = if (state.remainingBytes == 0L) "No data left" else "${plainBytes(state.remainingBytes, units)} left",
        daysLeftText = when (state.daysLeft) {
            0 -> "Renews today"
            1 -> "1 day left"
            else -> "${state.daysLeft} days left"
        },
        forecastText = forecast?.let { forecastSentence(forecastToUi(it, now, zone, locale)) },
        isEstimated = state.isApproximate,
        chips = addOns.filter { it.validFrom < state.window.end && it.validUntil > state.window.start }
            .map { "${it.label} +${plainBytes(it.bytes, units)}" },
        archived = state.plan.archived,
    )
}
