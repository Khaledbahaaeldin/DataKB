package io.github.khaledbahaaeldin.emberbyte.ui.design.format

import java.math.BigDecimal
import java.math.RoundingMode

enum class ByteUnits(val base: Long, val labels: List<String>) {
    DECIMAL(1000L, listOf("B", "KB", "MB", "GB", "TB")),
    BINARY(1024L, listOf("B", "KiB", "MiB", "GiB", "TiB")),
}

data class FormattedBytes(val value: String, val unit: String)

fun formatBytes(bytes: Long, units: ByteUnits): FormattedBytes {
    val safe = bytes.coerceAtLeast(0L)
    var scaled = safe.toDouble()
    var index = 0
    while (index < units.labels.lastIndex && scaled >= units.base) {
        scaled /= units.base
        index++
    }
    if (index == 0) return FormattedBytes(safe.toString(), units.labels[0])

    var rounded = roundForDisplay(scaled)
    if (index < units.labels.lastIndex && rounded >= BigDecimal(units.base)) {
        scaled /= units.base
        index++
        rounded = roundForDisplay(scaled)
    }
    return FormattedBytes(rounded.toPlainString(), units.labels[index])
}

private fun decimalsFor(value: Double): Int = when {
    value >= 100 -> 0
    value >= 10 -> 1
    else -> 2
}

private fun roundForDisplay(value: Double): BigDecimal {
    var decimals = decimalsFor(value)
    var rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)
    // Rounding can cross a threshold (99.96 -> 100.0, 9.996 -> 10.00): re-pick the decimals.
    val recheck = decimalsFor(rounded.toDouble())
    if (recheck < decimals) {
        decimals = recheck
        rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)
    }
    return rounded
}

fun spokenUnit(unit: String): String = when (unit) {
    "B" -> "bytes"
    "KB" -> "kilobytes"
    "MB" -> "megabytes"
    "GB" -> "gigabytes"
    "TB" -> "terabytes"
    "KiB" -> "kibibytes"
    "MiB" -> "mebibytes"
    "GiB" -> "gibibytes"
    "TiB" -> "tebibytes"
    else -> unit
}
