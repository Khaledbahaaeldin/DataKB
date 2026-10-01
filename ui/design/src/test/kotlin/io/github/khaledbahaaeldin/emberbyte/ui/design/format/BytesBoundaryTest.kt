package io.github.khaledbahaaeldin.emberbyte.ui.design.format

import org.junit.Assert.assertEquals
import org.junit.Test

class BytesBoundaryTest {
    private fun d(bytes: Long) = formatBytes(bytes, ByteUnits.DECIMAL)
    private fun b(bytes: Long) = formatBytes(bytes, ByteUnits.BINARY)

    @Test fun min_value_clamps_to_zero() = assertEquals(FormattedBytes("0", "B"), d(Long.MIN_VALUE))
    @Test fun max_value_stays_in_top_unit() {
        assertEquals(FormattedBytes("9223372", "TB"), d(Long.MAX_VALUE))
        assertEquals(FormattedBytes("8388608", "TiB"), b(Long.MAX_VALUE))
    }
    @Test fun top_unit_never_promotes() {
        assertEquals(FormattedBytes("1000", "TB"), d(1_000_000_000_000_000))
        assertEquals(FormattedBytes("1024", "TiB"), b(1L shl 50))
    }
    @Test fun exact_base_boundaries() {
        assertEquals(FormattedBytes("999", "B"), d(999))
        assertEquals(FormattedBytes("1.00", "KB"), d(1000))
        assertEquals(FormattedBytes("1.00", "MB"), d(1_000_000))
        assertEquals(FormattedBytes("1023", "B"), b(1023))
        assertEquals(FormattedBytes("1.00", "KiB"), b(1024))
        assertEquals(FormattedBytes("1.00", "GiB"), b(1L shl 30))
    }
    @Test fun decimal_rounding_promotion() {
        assertEquals(FormattedBytes("1.00", "MB"), d(999_950))
        assertEquals(FormattedBytes("1.00", "MB"), d(999_500))
        assertEquals(FormattedBytes("999", "KB"), d(999_499))
    }
    @Test fun just_below_hundred_and_ten() {
        assertEquals(FormattedBytes("100", "KB"), d(99_950))
        assertEquals(FormattedBytes("99.9", "KB"), d(99_949))
        assertEquals(FormattedBytes("10.0", "KB"), d(9_995))
        assertEquals(FormattedBytes("9.99", "KB"), d(9_994))
        assertEquals(FormattedBytes("100", "KiB"), b(102_349))
        assertEquals(FormattedBytes("10.0", "KiB"), b(10_235))
    }
    @Test fun binary_rounding_promotion() {
        assertEquals(FormattedBytes("1.00", "MiB"), b(1_048_566)) // 1023.99 KiB
        assertEquals(FormattedBytes("1.00", "MiB"), b(1_048_575))
        assertEquals(FormattedBytes("1023", "KiB"), b(1_047_552))
    }
}
