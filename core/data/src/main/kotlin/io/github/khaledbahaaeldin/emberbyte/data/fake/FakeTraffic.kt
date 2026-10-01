package io.github.khaledbahaaeldin.emberbyte.data.fake

import kotlin.math.sin

/** Deterministic pretend traffic: a gentle baseline plus a burst in every third 20-second block. */
object FakeTraffic {
    fun rxBpsAt(elapsedMs: Long): Long {
        val t = elapsedMs / 1000.0
        val base = 600_000.0 + 400_000.0 * sin(t / 5.0)
        val burst = if ((t.toLong() / 20) % 3 == 1L) 8_000_000.0 * (0.5 + 0.5 * sin(t)) else 0.0
        return (base + burst).toLong().coerceAtLeast(0L)
    }

    fun txBpsAt(elapsedMs: Long): Long = rxBpsAt(elapsedMs) / 12
}
