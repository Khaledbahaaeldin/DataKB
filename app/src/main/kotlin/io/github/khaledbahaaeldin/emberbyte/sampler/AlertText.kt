package io.github.khaledbahaaeldin.emberbyte.sampler

import io.github.khaledbahaaeldin.emberbyte.common.plainBytes
import io.github.khaledbahaaeldin.emberbyte.common.spokenBytes
import io.github.khaledbahaaeldin.emberbyte.data.alert.AlertNotice
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class AlertText(val title: String, val text: String, val contentDescription: String = "")

fun alertText(notice: AlertNotice, units: ByteUnits, zone: ZoneId, locale: Locale): AlertText {
    val used = plainBytes(notice.usedBytes, units)
    val cap = plainBytes(notice.effectiveCapBytes, units)
    val spokenUsed = spokenBytes(notice.usedBytes, units)
    val spokenCap = spokenBytes(notice.effectiveCapBytes, units)
    if (notice.thresholdPercent >= 100) {
        val title = "${notice.planName}: all your data is used"
        val text = "$used of $cap used"
        val spoken = "$title. $spokenUsed of $spokenCap used."
        return AlertText(title, text, spoken)
    }
    val parts = mutableListOf("$used of $cap used", "${plainBytes(notice.remainingBytes, units)} left")
    val spokenParts = mutableListOf("$spokenUsed of $spokenCap used", "${spokenBytes(notice.remainingBytes, units)} left")
    notice.runOutExpected?.let {
        val shortDay = it.atZone(zone).dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        val fullDay = it.atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        parts += "runs out $shortDay"
        spokenParts += "runs out $fullDay"
    }
    val title = "${notice.planName}: ${notice.thresholdPercent}% of your data used"
    val text = parts.joinToString(" · ")
    val spoken = "$title. ${spokenParts.joinToString(", ")}."
    return AlertText(title, text, spoken)
}
