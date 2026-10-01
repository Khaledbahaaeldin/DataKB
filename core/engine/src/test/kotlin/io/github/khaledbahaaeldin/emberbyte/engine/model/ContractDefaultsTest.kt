package io.github.khaledbahaaeldin.emberbyte.engine.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ContractDefaultsTest {
    @Test
    fun forecast_with_null_runOut_means_never_runs_out() {
        val f = Forecast(1L, null, null, null, 10L, Confidence.HIGH, 30)
        assertNull(f.runOutEarliest)
        assertNull(f.runOutExpected)
        assertNull(f.runOutLatest)
    }

    @Test
    fun plan_is_not_archived_by_default_and_usageFilter_defaults_to_all() {
        val plan = Plan(0L, "P", null, 1L, Cycle.MonthlyOnDay(31, LocalTime.MIDNIGHT, ZoneOffset.UTC), Rollover.None)
        assertFalse(plan.archived)
        assertEquals(UsageFilter(null, null), UsageFilter())
    }

    @Test
    fun liveSpeed_null_network_means_offline() {
        assertNull(LiveSpeed(0L, 0L, null, Instant.EPOCH).network)
    }
}
