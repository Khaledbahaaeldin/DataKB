package io.github.khaledbahaaeldin.emberbyte.ui.design.number

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberAxesTest {
    @Test fun zero_throughput_is_at_rest() {
        assertEquals(0f, throughputIntensity(0L), 0f)
        val axes = axesFor(throughputIntensity(0L))
        assertEquals(520f, axes.weight, 0.001f)
        assertEquals(100f, axes.width, 0.001f)
    }

    @Test fun rest_threshold_is_still_at_rest() =
        assertEquals(0f, throughputIntensity(10_000L), 0f)

    @Test fun max_throughput_is_full_intensity() {
        val axes = axesFor(throughputIntensity(20_000_000L))
        assertEquals(900f, axes.weight, 0.001f)
        assertEquals(125f, axes.width, 0.001f)
    }

    @Test fun above_max_is_clamped() =
        assertEquals(1f, throughputIntensity(500_000_000L), 0f)

    @Test fun geometric_midpoint_is_half_intensity() =
        assertEquals(0.5f, throughputIntensity(447_214L), 0.01f)

    @Test fun intensity_increases_with_throughput() {
        val values = listOf(50_000L, 200_000L, 1_000_000L, 5_000_000L).map(::throughputIntensity)
        assertTrue(values.zipWithNext().all { (a, b) -> a < b })
    }
}
