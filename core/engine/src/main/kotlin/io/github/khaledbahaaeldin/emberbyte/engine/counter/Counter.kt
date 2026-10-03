package io.github.khaledbahaaeldin.emberbyte.engine.counter

import java.time.Instant

data class CounterReading(val at: Instant, val rxBytes: Long, val txBytes: Long, val bootId: String)

data class CounterDelta(val rxBytes: Long, val txBytes: Long, val wasReset: Boolean)

object CounterReconciler {
    /**
     * previous == null -> zero delta, not a reset.
     * A different bootId means the counters restarted from zero: the delta is the current reading (wasReset = true).
     * In the SAME boot a decreasing counter means an interface vanished (for example mobile data went away): the delta is 0
     * (wasReset = true) so nothing is invented; when the interface returns, the caller clamps the jump (see SamplerEngine).
     */
    fun delta(previous: CounterReading?, current: CounterReading): CounterDelta {
        if (previous == null) return CounterDelta(0L, 0L, wasReset = false)
        if (previous.bootId != current.bootId) {
            return CounterDelta(current.rxBytes.coerceAtLeast(0L), current.txBytes.coerceAtLeast(0L), wasReset = true)
        }
        if (current.rxBytes < previous.rxBytes || current.txBytes < previous.txBytes) {
            return CounterDelta(0L, 0L, wasReset = true)
        }
        return CounterDelta(current.rxBytes - previous.rxBytes, current.txBytes - previous.txBytes, wasReset = false)
    }
}
