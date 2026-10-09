package io.github.khaledbahaaeldin.emberbyte.home

import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeMappingM4Test {
    @Test
    fun spike_tile_formats_title_and_detail_reusing_spike_text() {
        val event = SpikeEvent(
            id = 42L,
            packageName = "com.video",
            windowStart = Instant.parse("2026-10-05T10:00:00Z"),
            detectedAt = Instant.parse("2026-10-05T12:00:00Z"),
            windowMinutes = 120,
            bytes = 1_240_000_000L,
            baselineBytes = 150_000_000L,
            multiple = 8.2667f,
            dismissed = false,
        )
        val tile = spikeTile(event, "Video", ByteUnits.DECIMAL)
        assertEquals(42L, tile.id)
        assertEquals("com.video", tile.packageName)
        assertEquals("Video used a lot of data", tile.title)
        assertEquals("1.24 GB in two hours, 8.3x its usual", tile.detail)
    }
}
