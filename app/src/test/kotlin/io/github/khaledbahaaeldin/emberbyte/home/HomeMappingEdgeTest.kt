package io.github.khaledbahaaeldin.emberbyte.home

import io.github.khaledbahaaeldin.emberbyte.engine.model.Confidence
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeMappingEdgeTest {
    private val now = Instant.parse("2026-10-05T12:00:00Z")

    private fun forecast(earliest: Long?, expected: Long?, latest: Long?) = Forecast(
        planId = 1,
        runOutEarliest = earliest?.let { now.plusSeconds(it * 86_400) },
        runOutExpected = expected?.let { now.plusSeconds(it * 86_400) },
        runOutLatest = latest?.let { now.plusSeconds(it * 86_400) },
        projectedCycleEndBytes = 0,
        confidence = Confidence.LOW,
        historyDays = 3,
    )

    @Test fun forecast_with_only_an_expected_date_has_no_spread() {
        val ui = forecastToUi(forecast(null, 3, null), now, ZoneOffset.UTC, Locale.ENGLISH)
        assertEquals("Thu", ui.headline)
        assertEquals("runs out", ui.detail)
    }

    @Test fun forecast_without_expected_falls_back_to_earliest_and_uses_the_latest_spread() {
        // at = earliest (day 2, Wed); latest is 3 days later
        val ui = forecastToUi(forecast(2, null, 5), now, ZoneOffset.UTC, Locale.ENGLISH)
        assertEquals("Wed", ui.headline)
        assertEquals("runs out (±3 days)", ui.detail)
    }

    @Test fun forecast_weekday_follows_the_zone_and_the_locale() {
        // 12:00Z + 3 days is still Thursday in UTC+13 only after midnight rolls: 12:00Z = 01:00 next day there.
        val ui = forecastToUi(forecast(3, 3, 3), now, ZoneOffset.ofHours(13), Locale.ENGLISH)
        assertEquals("Fri", ui.headline)
        val fr = forecastToUi(forecast(3, 3, 3), now, ZoneOffset.UTC, Locale.FRENCH)
        assertEquals("jeu.", fr.headline)
    }

    @Test fun run_out_exactly_six_days_away_shows_only_the_weekday() {
        val ui = forecastToUi(forecast(6, 6, 6), now, ZoneOffset.UTC, Locale.ENGLISH)
        assertEquals("Sun", ui.headline)
    }

    @Test fun run_out_seven_days_away_adds_the_date() {
        val ui = forecastToUi(forecast(7, 7, 7), now, ZoneOffset.UTC, Locale.ENGLISH)
        assertEquals("Mon 12 Oct", ui.headline)
    }

    @Test fun far_run_out_date_follows_zone_and_locale() {
        val ui = forecastToUi(forecast(10, 10, 10), now, ZoneOffset.ofHours(13), Locale.FRENCH)
        // 12:00Z + 10 days = 01:00 on the 16th in UTC+13
        assertEquals("ven. 16 oct.", ui.headline)
    }
}
