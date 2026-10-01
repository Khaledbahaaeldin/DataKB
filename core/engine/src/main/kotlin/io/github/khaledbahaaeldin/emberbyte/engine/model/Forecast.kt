package io.github.khaledbahaaeldin.emberbyte.engine.model

import java.time.Instant

enum class Confidence { LOW, MEDIUM, HIGH }

/** All three runOut* values are null when the plan is projected NOT to run out this cycle. */
data class Forecast(
    val planId: Long,
    val runOutEarliest: Instant?,
    val runOutExpected: Instant?,
    val runOutLatest: Instant?,
    val projectedCycleEndBytes: Long,
    val confidence: Confidence,
    val historyDays: Int,
)
