package io.github.khaledbahaaeldin.emberbyte.sampler

import io.github.khaledbahaaeldin.emberbyte.data.alert.AlertNotice
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertTextTest {
    private fun notice(threshold: Int, used: Long, cap: Long = 10_000_000_000L, remaining: Long = maxOf(0, cap - used), runOut: Instant? = null) =
        AlertNotice(7, "Main", threshold, used, cap, remaining, runOut)
    private fun text(n: AlertNotice) = alertText(n, ByteUnits.DECIMAL, ZoneOffset.UTC, Locale.ENGLISH)

    @Test fun a_threshold_notice_names_the_plan_and_the_percentage() {
        val t = text(notice(80, 8_100_000_000L))
        assertEquals("Main: 80% of your data used", t.title)
        assertEquals("8.10 GB of 10.0 GB used · 1.90 GB left", t.text)
    }

    @Test fun the_run_out_day_is_added_when_a_forecast_exists() {
        // 2026-10-15 is a Thursday
        val t = text(notice(80, 8_100_000_000L, runOut = Instant.parse("2026-10-15T08:00:00Z")))
        assertEquals("8.10 GB of 10.0 GB used · 1.90 GB left · runs out Thu", t.text)
    }

    @Test fun reaching_the_cap_says_the_data_is_used_up() {
        val t = text(notice(100, 10_500_000_000L, runOut = Instant.parse("2026-10-15T08:00:00Z")))
        assertEquals("Main: all your data is used", t.title)
        assertEquals("10.5 GB of 10.0 GB used", t.text)
    }
}
