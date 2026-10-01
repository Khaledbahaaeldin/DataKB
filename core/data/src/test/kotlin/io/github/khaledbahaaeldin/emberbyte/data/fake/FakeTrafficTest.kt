package io.github.khaledbahaaeldin.emberbyte.data.fake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTrafficTest {
    @Test fun is_deterministic() =
        assertEquals(FakeTraffic.rxBpsAt(12_345L), FakeTraffic.rxBpsAt(12_345L))

    @Test fun stays_within_plausible_bounds() {
        for (ms in 0L..200_000L step 1_000L) {
            val rx = FakeTraffic.rxBpsAt(ms)
            assertTrue("rx=$rx at $ms", rx in 0L..9_500_000L)
        }
    }

    @Test fun burst_windows_are_much_faster_than_quiet_ones() =
        assertTrue(FakeTraffic.rxBpsAt(25_000L) > FakeTraffic.rxBpsAt(5_000L) * 2)

    @Test fun upload_is_a_fraction_of_download() {
        val ms = 25_000L
        assertEquals(FakeTraffic.rxBpsAt(ms) / 12, FakeTraffic.txBpsAt(ms))
    }
}
