package io.github.khaledbahaaeldin.emberbyte.engine.spike

import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.engine.plan.UsageSlice
import java.time.Instant

object SpikeDetector {
    private const val FLOOR_BYTES = 1_000_000L
    fun detect(
        recent: List<UsageSlice>,
        baselinePerWindowBytes: Map<String, Long>,
        detectedAt: Instant,
        windowMinutes: Int = 120,
        minBytes: Long = 100_000_000L,
        minMultiple: Float = 5f,
    ): List<SpikeEvent> = recent.mapNotNull { slice ->
        val pkg = slice.packageName ?: return@mapNotNull null
        val baseline = baselinePerWindowBytes[pkg] ?: return@mapNotNull null
        val effectiveBaseline = maxOf(baseline, FLOOR_BYTES)
        val needed = Math.ceil(minMultiple.toDouble() * effectiveBaseline).toLong()
        if (slice.bytes < minBytes || slice.bytes < needed) return@mapNotNull null
        val multiple = slice.bytes.toFloat() / effectiveBaseline
        SpikeEvent(0L, pkg, slice.start, detectedAt, windowMinutes, slice.bytes, baseline, multiple, false)
    }
}
