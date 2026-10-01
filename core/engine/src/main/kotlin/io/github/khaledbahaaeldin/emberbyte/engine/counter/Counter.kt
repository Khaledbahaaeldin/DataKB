package io.github.khaledbahaaeldin.emberbyte.engine.counter

import java.time.Instant

data class CounterReading(val at: Instant, val rxBytes: Long, val txBytes: Long, val bootId: String)

data class CounterDelta(val rxBytes: Long, val txBytes: Long, val wasReset: Boolean)

object CounterReconciler {
    /**
     * Turns cumulative counter readings into a non-negative delta.
     * previous == null -> zero delta, not a reset. A different bootId, or EITHER counter decreasing, is a reset:
     * the delta is the current reading (both directions) and wasReset is true.
     */
    fun delta(previous: CounterReading?, current: CounterReading): CounterDelta {
        if (previous == null) return CounterDelta(0L, 0L, wasReset = false)
        val reset = previous.bootId != current.bootId ||
            current.rxBytes < previous.rxBytes ||
            current.txBytes < previous.txBytes
        return if (reset) {
            CounterDelta(current.rxBytes.coerceAtLeast(0L), current.txBytes.coerceAtLeast(0L), wasReset = true)
        } else {
            CounterDelta(current.rxBytes - previous.rxBytes, current.txBytes - previous.txBytes, wasReset = false)
        }
    }
}
