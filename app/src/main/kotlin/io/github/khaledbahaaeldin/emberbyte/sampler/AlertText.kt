package io.github.khaledbahaaeldin.emberbyte.sampler

import io.github.khaledbahaaeldin.emberbyte.common.plainBytes
import io.github.khaledbahaaeldin.emberbyte.data.alert.AlertNotice
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class AlertText(val title: String, val text: String)

fun alertText(notice: AlertNotice, units: ByteUnits, zone: ZoneId, locale: Locale): AlertText {
    val used = plainBytes(notice.usedBytes, units)
    val cap = plainBytes(notice.effectiveCapBytes, units)
    if (notice.thresholdPercent >= 100) {
        return AlertText("${notice.planName}: all your data is used", "$used of $cap used")
    }
    val parts = mutableListOf("$used of $cap used", "${plainBytes(notice.remainingBytes, units)} left")
    notice.runOutExpected?.let { parts += "runs out ${it.atZone(zone).dayOfWeek.getDisplayName(TextStyle.SHORT, locale)}" }
    return AlertText("${notice.planName}: ${notice.thresholdPercent}% of your data used", parts.joinToString(" · "))
}
