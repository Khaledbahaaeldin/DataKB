package io.github.khaledbahaaeldin.emberbyte.engine.plan

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/** packageName == null: total mobile traffic; otherwise a per-app breakdown used only for package-scoped free rules (contract 10.1). */
data class UsageSlice(val start: Instant, val end: Instant, val packageName: String?, val bytes: Long)

internal object Slices {
    fun overlapMillis(aStart: Instant, aEnd: Instant, bStart: Instant, bEnd: Instant): Long {
        val start = maxOf(aStart, bStart)
        val end = minOf(aEnd, bEnd)
        return if (end > start) Duration.between(start, end).toMillis() else 0L
    }

    /** Bytes of [slice] inside `[from, to)`, clipped proportionally to the time overlap. */
    fun clippedBytes(slice: UsageSlice, from: Instant, to: Instant): Long {
        val length = Duration.between(slice.start, slice.end).toMillis()
        if (length <= 0L || slice.bytes <= 0L) return 0L
        val overlap = overlapMillis(slice.start, slice.end, from, to)
        if (overlap <= 0L) return 0L
        if (overlap >= length) return slice.bytes
        return (slice.bytes.toDouble() * overlap / length).roundToLong()
    }
}
