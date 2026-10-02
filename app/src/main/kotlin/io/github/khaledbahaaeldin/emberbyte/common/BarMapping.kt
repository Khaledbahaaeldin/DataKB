package io.github.khaledbahaaeldin.emberbyte.common

import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.spokenUnit
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BarUi
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** "1.24 gigabytes" - for screen readers. */
internal fun spokenBytes(bytes: Long, units: ByteUnits): String {
    val f = formatBytes(bytes, units)
    return "${f.value} ${spokenUnit(f.unit)}"
}

/** "1.24 GB". */
internal fun plainBytes(bytes: Long, units: ByteUnits): String {
    val f = formatBytes(bytes, units)
    return "${f.value} ${f.unit}"
}

/** One bar per point, labelled with the short weekday and described "Tuesday, 100 megabytes". */
internal fun weekdayBars(points: List<UsagePoint>, units: ByteUnits, zone: ZoneId, locale: Locale): List<BarUi> =
    points.map { point ->
        val day = point.start.atZone(zone).dayOfWeek
        BarUi(
            label = day.getDisplayName(TextStyle.SHORT, locale),
            bytes = point.totalBytes,
            description = "${day.getDisplayName(TextStyle.FULL, locale)}, ${spokenBytes(point.totalBytes, units)}",
        )
    }
