package io.github.khaledbahaaeldin.emberbyte.engine.spike

import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.engine.plan.UsageSlice
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpikeDetectorTest {
    private val start = Instant.parse("2026-10-05T10:00:00Z")
    private val end = start.plusSeconds(7200)
    private val detectedAt = Instant.parse("2026-10-05T11:05:00Z")
    private fun slice(pkg: String?, bytes: Long) = UsageSlice(start, end, pkg, bytes)
    private fun detect(slices: List<UsageSlice>, baselines: Map<String, Long>) = SpikeDetector.detect(slices, baselines, detectedAt)

    @Test fun a_window_far_above_the_baseline_is_a_spike_with_all_fields() {
        val e = detect(listOf(slice("com.video", 600_000_000L)), mapOf("com.video" to 40_000_000L)).single()
        assertEquals(SpikeEvent(0, "com.video", start, detectedAt, 120, 600_000_000L, 40_000_000L, 15f, false), e)
    }
    @Test fun the_minimum_size_applies_even_for_a_huge_multiple() {
        assertTrue(detect(listOf(slice("a", 99_999_999L)), mapOf("a" to 0L)).isEmpty())
        assertEquals(1, detect(listOf(slice("a", 100_000_000L)), mapOf("a" to 0L)).size)
    }
    @Test fun exactly_five_times_the_baseline_is_a_spike_and_just_below_is_not() {
        assertEquals(1, detect(listOf(slice("a", 150_000_000L)), mapOf("a" to 30_000_000L)).size)
        assertTrue(detect(listOf(slice("a", 149_000_000L)), mapOf("a" to 30_000_000L)).isEmpty())
    }
    @Test fun a_thousand_deterministic_pairs_at_five_times_baseline_are_spikes_and_one_byte_below_is_not() {
        for (baseline in 1_000_000L..200_000_000L step 199_000L) {
            val exact = 5L * baseline
            val spikes = SpikeDetector.detect(listOf(slice("a", exact)), mapOf("a" to baseline), detectedAt, minBytes = 0L)
            assertEquals("baseline=$baseline exact should spike", 1, spikes.size)
            assertEquals(exact.toFloat() / baseline.toFloat(), spikes.single().multiple, 0.0001f)

            val below = exact - 1L
            val noSpikes = SpikeDetector.detect(listOf(slice("a", below)), mapOf("a" to baseline), detectedAt, minBytes = 0L)
            assertTrue("baseline=$baseline below should not spike", noSpikes.isEmpty())
        }
    }
    @Test fun a_zero_baseline_is_floored_at_one_megabyte() {
        val e = detect(listOf(slice("a", 200_000_000L)), mapOf("a" to 0L)).single()
        assertEquals(200f, e.multiple, 0.001f)
    }
    @Test fun an_app_without_a_baseline_is_skipped() {
        assertTrue(detect(listOf(slice("new.app", 900_000_000L)), emptyMap()).isEmpty())
    }
    @Test fun slices_without_a_package_are_ignored_and_each_app_is_judged_on_its_own() {
        val events = detect(
            listOf(slice(null, 900_000_000L), slice("a", 500_000_000L), slice("b", 200_000_000L)),
            mapOf("a" to 50_000_000L, "b" to 150_000_000L),
        )
        assertEquals(listOf("a"), events.map { it.packageName })
    }
}
