package io.github.khaledbahaaeldin.emberbyte.plans

import io.github.khaledbahaaeldin.emberbyte.common.forecastSentence
import io.github.khaledbahaaeldin.emberbyte.common.plainBytes
import io.github.khaledbahaaeldin.emberbyte.common.remainingText
import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.plan.percentUsed
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
    val percent = state.percentUsed()
    return PlanCardUi(
        id = state.plan.id,
        name = state.plan.name,
        simLabel = simLabel,
        fraction = state.fractionUsed,
        ringState = when {
            percent >= 100 -> BudgetStateUi.Exceeded
            percent >= 80 -> BudgetStateUi.Warning
            else -> BudgetStateUi.Ok
        },
        percentText = "$percent%",
        usedText = "${plainBytes(state.usedBytes, units)} of ${plainBytes(state.effectiveCapBytes, units)}",
        remainingText = remainingText(state.remainingBytes, units),
        daysLeftText = when (state.daysLeft) {
            0 -> "Renews today"
            1 -> "1 day left"
            else -> "${state.daysLeft} days left"
        },
        forecastText = forecast?.let { forecastSentence(forecastToUi(it, now, zone, locale, state.remainingBytes)) },
        isEstimated = state.isApproximate,
        chips = addOns.filter { it.validFrom < state.window.end && it.validUntil > state.window.start }
            .map { "${it.label} +${plainBytes(it.bytes, units)}" },
        archived = state.plan.archived,
    )
}
