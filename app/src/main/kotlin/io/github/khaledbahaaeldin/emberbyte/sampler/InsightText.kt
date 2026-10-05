package io.github.khaledbahaaeldin.emberbyte.sampler

import io.github.khaledbahaaeldin.emberbyte.common.plainBytes
import io.github.khaledbahaaeldin.emberbyte.data.budget.BudgetNotice
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.util.Locale

data class InsightText(val title: String, val text: String)

fun budgetText(notice: BudgetNotice, units: ByteUnits): InsightText {
    val period = if (notice.budget.period == BudgetPeriod.DAILY) "today" else "this cycle"
    val amounts = "${plainBytes(notice.usedBytes, units)} of ${plainBytes(notice.budget.limitBytes, units)} used $period"
    val title = when (notice.state) {
        BudgetState.EXCEEDED -> "${notice.label}: data budget exceeded"
        else -> "${notice.label}: nearly at its data budget"
    }
    return InsightText(title, amounts)
}

fun spikeText(event: SpikeEvent, label: String, units: ByteUnits): InsightText =
    InsightText(
        "$label used a lot of data",
        "${plainBytes(event.bytes, units)} in two hours, ${String.format(Locale.ROOT, "%.1f", event.multiple)}x its usual",
    )
