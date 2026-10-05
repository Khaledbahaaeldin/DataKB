package io.github.khaledbahaaeldin.emberbyte.engine.model

import java.time.Instant

data class SpikeEvent(
    val id: Long,
    val packageName: String,
    val windowStart: Instant,
    val detectedAt: Instant,
    val windowMinutes: Int,
    val bytes: Long,
    val baselineBytes: Long,
    val multiple: Float,
    val dismissed: Boolean,
)
