package io.github.khaledbahaaeldin.emberbyte.engine.forecast

import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.HashSet

object HourCurves {
    fun personal(minuteRows: List<MinuteTotal>, now: Instant, zone: ZoneId, base: DoubleArray = ForecastEngine.DEFAULT_HOUR_CURVE): DoubleArray {
        val baseSum = base.sum()
        val normalisedBase = DoubleArray(24) { base[it] / baseSum }
        val today = now.atZone(zone).toLocalDate()
        val from = today.minusDays(7).atStartOfDay(zone).toInstant()
        val to = today.atStartOfDay(zone).toInstant()
        val perHour = DoubleArray(24)
        val days = HashSet<LocalDate>()
        for (row in minuteRows) {
            if (row.network != NetworkKind.MOBILE || row.minuteStart < from || row.minuteStart >= to || row.totalBytes <= 0L) continue
            val local = row.minuteStart.atZone(zone)
            perHour[local.hour] += row.totalBytes.toDouble()
            days += local.toLocalDate()
        }
        val total = perHour.sum()
        if (days.size < 3 || total <= 0.0) return normalisedBase
        val weight = minOf(days.size, 7) / 7.0
        return DoubleArray(24) { h -> weight * perHour[h] / total + (1 - weight) * normalisedBase[h] }
    }
}
