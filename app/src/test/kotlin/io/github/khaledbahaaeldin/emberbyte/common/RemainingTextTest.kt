package io.github.khaledbahaaeldin.emberbyte.common

import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import org.junit.Assert.assertEquals
import org.junit.Test

class RemainingTextTest {
    @Test fun zero_bytes_reads_no_data_left() {
        assertEquals("No data left", remainingText(0L, ByteUnits.DECIMAL))
        assertEquals("No data left", remainingText(-100L, ByteUnits.DECIMAL))
    }

    @Test fun positive_bytes_formats_with_left() {
        assertEquals("3.80 GB left", remainingText(3_800_000_000L, ByteUnits.DECIMAL))
        assertEquals("1.00 GiB left", remainingText(1_073_741_824L, ByteUnits.BINARY))
    }
}
