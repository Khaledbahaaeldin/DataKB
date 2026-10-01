package io.github.khaledbahaaeldin.emberbyte.ui.design.format

import org.junit.Assert.assertEquals
import org.junit.Test

class BytesTest {
    private fun d(bytes: Long) = formatBytes(bytes, ByteUnits.DECIMAL)
    private fun b(bytes: Long) = formatBytes(bytes, ByteUnits.BINARY)

    @Test fun zero() = assertEquals(FormattedBytes("0", "B"), d(0))
    @Test fun negative_is_clamped_to_zero() = assertEquals(FormattedBytes("0", "B"), d(-5))
    @Test fun plain_bytes_have_no_decimals() = assertEquals(FormattedBytes("512", "B"), d(512))
    @Test fun below_ten_has_two_decimals() = assertEquals(FormattedBytes("1.50", "KB"), d(1_500))
    @Test fun gigabytes_two_decimals() = assertEquals(FormattedBytes("1.24", "GB"), d(1_240_000_000))
    @Test fun ten_to_ninety_nine_has_one_decimal() = assertEquals(FormattedBytes("12.3", "MB"), d(12_300_000))
    @Test fun hundred_and_up_has_no_decimals() = assertEquals(FormattedBytes("123", "MB"), d(123_456_789))
    @Test fun rounding_to_hundred_drops_the_decimal() = assertEquals(FormattedBytes("100", "MB"), d(99_960_000))
    @Test fun rounding_to_ten_uses_one_decimal() = assertEquals(FormattedBytes("10.0", "MB"), d(9_996_000))
    @Test fun rounding_to_base_promotes_the_unit() = assertEquals(FormattedBytes("1.00", "MB"), d(999_999))
    @Test fun terabytes() = assertEquals(FormattedBytes("2.50", "TB"), d(2_500_000_000_000))
    @Test fun binary_units() = assertEquals(FormattedBytes("1.00", "MiB"), b(1_048_576))
    @Test fun binary_below_base_stays() = assertEquals(FormattedBytes("1000", "B"), b(1000))

    @Test fun spoken_units() {
        assertEquals("bytes", spokenUnit("B"))
        assertEquals("megabytes", spokenUnit("MB"))
        assertEquals("gigabytes", spokenUnit("GB"))
        assertEquals("gibibytes", spokenUnit("GiB"))
    }
}
