package io.github.khaledbahaaeldin.emberbyte.engine.forecast

import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class HourCurvesTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val uniform = DoubleArray(24) { 1.0 / 24 }

    private fun row(day: Int, hour: Int, bytes: Long, network: NetworkKind = NetworkKind.MOBILE) =
        MinuteTotal(Instant.parse("2026-10-%02dT%02d:00:00Z".format(day, hour)), network, 1, bytes, 0)

    @Test fun fewer_than_three_days_of_traffic_keeps_the_base_curve() {
        val rows = listOf(row(8, 20, 1000), row(9, 20, 1000))
        assertArrayEquals(uniform, HourCurves.personal(rows, now, ZoneOffset.UTC, uniform), 1e-12)
    }
    @Test fun seven_days_of_evening_traffic_become_the_curve() {
        val rows = (3..9).map { row(it, 20, 1_000_000) }
        val c = HourCurves.personal(rows, now, ZoneOffset.UTC, uniform)
        assertEquals(1.0, c[20], 1e-9)
        assertEquals(0.0, c[3], 1e-9)
    }
    @Test fun four_days_blend_with_the_base_by_four_sevenths() {
        val rows = (6..9).map { row(it, 20, 1_000_000) }
        val c = HourCurves.personal(rows, now, ZoneOffset.UTC, uniform)
        assertEquals(4.0 / 7 + 3.0 / 7 / 24, c[20], 1e-9)
        assertEquals(3.0 / 7 / 24, c[3], 1e-9)
        assertEquals(1.0, c.sum(), 1e-9)
    }
    @Test fun wifi_and_today_and_older_than_seven_days_are_ignored() {
        val rows = (3..9).map { row(it, 20, 1_000_000) } +
            row(9, 5, 9_000_000_000L, NetworkKind.WIFI) + row(10, 5, 9_000_000_000L) + row(1, 5, 9_000_000_000L)
        val c = HourCurves.personal(rows, now, ZoneOffset.UTC, uniform)
        assertEquals(0.0, c[5], 1e-9)
    }
    @Test fun hours_are_local_hours_of_the_given_zone() {
        val kolkata = ZoneId.of("Asia/Kolkata") // 20:00 UTC is 01:30 the next day: hour 1 (the row of 9 Oct falls on "today" there and is ignored)
        val rows = (2..9).map { row(it, 20, 1_000_000) }
        assertEquals(1.0, HourCurves.personal(rows, now, kolkata, uniform)[1], 1e-9)
    }
    @Test fun a_base_curve_that_does_not_sum_to_one_is_normalised() {
        val base = DoubleArray(24) { 2.0 }
        assertEquals(1.0, HourCurves.personal(emptyList(), now, ZoneOffset.UTC, base).sum(), 1e-9)
    }
}
