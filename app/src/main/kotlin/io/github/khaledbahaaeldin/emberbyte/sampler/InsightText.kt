package io.github.khaledbahaaeldin.emberbyte.sampler

import io.github.khaledbahaaeldin.emberbyte.common.plainBytes
import io.github.khaledbahaaeldin.emberbyte.common.spokenBytes
import io.github.khaledbahaaeldin.emberbyte.data.budget.BudgetNotice
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetState
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.util.Locale

data class InsightText(val title: String, val text: String, val contentDescription: String = "")

fun budgetText(notice: BudgetNotice, units: ByteUnits): InsightText {
    val amounts = "${plainBytes(notice.usedBytes, units)} of ${plainBytes(notice.budget.limitBytes, units)} used ${notice.periodLabel}"
    val spokenAmounts = "${spokenBytes(notice.usedBytes, units)} of ${spokenBytes(notice.budget.limitBytes, units)} used ${notice.periodLabel}"
    val title = when (notice.state) {
        BudgetState.EXCEEDED -> "${notice.label}: data budget exceeded"
        else -> "${notice.label}: nearly at its data budget"
    }
    val spoken = "$title. $spokenAmounts."
    return InsightText(title, amounts, spoken)
}

fun spikeText(event: SpikeEvent, label: String, units: ByteUnits): InsightText {
    val title = "$label used a lot of data"
    val plain = "${plainBytes(event.bytes, units)} in two hours, ${String.format(Locale.ROOT, "%.1f", event.multiple)}x its usual"
    val spoken = "$title: ${spokenBytes(event.bytes, units)} in two hours, ${String.format(Locale.ROOT, "%.1f", event.multiple)} times its usual."
    return InsightText(title, plain, spoken)
}
