package io.github.khaledbahaaeldin.emberbyte.engine.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test
    fun usagePoint_total_is_mobile_plus_wifi() {
        val point = UsagePoint(Instant.EPOCH, mobileBytes = 300L, wifiBytes = 200L)
        assertEquals(500L, point.totalBytes)
    }

    @Test
    fun dayUsage_total_is_mobile_plus_wifi() {
        val day = DayUsage(LocalDate.of(2026, 10, 1), mobileBytes = 940_000_000L, wifiBytes = 300_000_000L)
        assertEquals(1_240_000_000L, day.totalBytes)
    }

    @Test
    fun appUsage_total_is_mobile_plus_wifi() {
        val app = AppUsage("com.example", "Example", 10_001, mobileBytes = 5L, wifiBytes = 7L, screenTimeMs = null)
        assertEquals(12L, app.totalBytes)
    }

    @Test
    fun outcome_distinguishes_success_and_failure() {
        val ok: Outcome<Int> = Outcome.Success(1)
        val bad: Outcome<Int> = Outcome.Failure(EmberbyteError.NotFound)
        assertTrue(ok is Outcome.Success)
        assertTrue(bad is Outcome.Failure)
    }

    @Test
    fun contract_version_is_one() {
        assertEquals(1, CONTRACT_VERSION)
    }
}
