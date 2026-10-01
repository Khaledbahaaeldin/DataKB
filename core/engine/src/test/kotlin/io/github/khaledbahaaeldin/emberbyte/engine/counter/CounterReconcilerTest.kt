package io.github.khaledbahaaeldin.emberbyte.engine.counter

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CounterReconcilerTest {
    private val t0 = Instant.parse("2026-10-07T10:00:00Z")
    private fun reading(sec: Long, rx: Long, tx: Long, boot: String = "b1") =
        CounterReading(t0.plusSeconds(sec), rx, tx, boot)

    @Test fun no_previous_reading_means_zero_delta_and_no_reset() =
        assertEquals(CounterDelta(0, 0, false), CounterReconciler.delta(null, reading(0, 500, 100)))

    @Test fun normal_growth_is_the_difference() =
        assertEquals(CounterDelta(300, 50, false), CounterReconciler.delta(reading(0, 1000, 200), reading(1, 1300, 250)))

    @Test fun identical_readings_give_zero() =
        assertEquals(CounterDelta(0, 0, false), CounterReconciler.delta(reading(0, 1000, 200), reading(1, 1000, 200)))

    @Test fun a_decreasing_rx_counter_is_a_reset_and_the_delta_is_the_new_value() =
        assertEquals(CounterDelta(40, 260, true), CounterReconciler.delta(reading(0, 1000, 200), reading(1, 40, 260)))

    @Test fun a_decreasing_tx_counter_alone_is_also_a_reset() =
        assertEquals(CounterDelta(1500, 10, true), CounterReconciler.delta(reading(0, 1000, 200), reading(1, 1500, 10)))

    @Test fun a_different_boot_id_is_a_reset_even_if_counters_grew() =
        assertEquals(CounterDelta(5000, 900, true), CounterReconciler.delta(reading(0, 100, 50, "b1"), reading(1, 5000, 900, "b2")))

    @Test fun both_counters_decreasing_is_a_reset() =
        assertEquals(CounterDelta(50, 20, true), CounterReconciler.delta(reading(0, 1000, 200), reading(1, 50, 20)))

    @Test fun negative_counter_reading_is_clamped_to_zero_on_reset() =
        assertEquals(CounterDelta(0, 0, true), CounterReconciler.delta(reading(0, 1000, 200), reading(1, -1, -5)))
}
