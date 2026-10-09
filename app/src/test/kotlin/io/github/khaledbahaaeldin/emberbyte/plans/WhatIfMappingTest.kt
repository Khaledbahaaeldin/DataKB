package io.github.khaledbahaaeldin.emberbyte.plans

import io.github.khaledbahaaeldin.emberbyte.engine.model.Confidence
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WhatIfMappingTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val zone = ZoneOffset.UTC
    private val locale = Locale.ENGLISH

    @Test
    fun what_if_sentence_formats_runs_out_with_day_and_spread() {
        // Thursday 15 Oct -> Monday 19 Oct is 4 days away (<= 6 days, headline "Mon")
        // earliest Sunday 18 Oct, latest Tuesday 20 Oct -> spread 1 day ("runs out (±1 day)")
        val early = Instant.parse("2026-10-18T12:00:00Z")
        val expected = Instant.parse("2026-10-19T12:00:00Z")
        val late = Instant.parse("2026-10-20T12:00:00Z")
        val forecast = Forecast(
            planId = 1L,
            runOutEarliest = early,
            runOutExpected = expected,
            runOutLatest = late,
            projectedCycleEndBytes = 0L,
            confidence = Confidence.HIGH,
            historyDays = 14,
        )

        val sentence = whatIfSentence(forecast, remainingBytes = 5_000_000_000L, now, zone, locale)
        assertEquals("runs out Mon (±1 day)", sentence)
    }

    @Test
    fun what_if_sentence_formats_safe_forecast() {
        val forecast = Forecast(
            planId = 1L,
            runOutEarliest = null,
            runOutExpected = null,
            runOutLatest = null,
            projectedCycleEndBytes = 0L,
            confidence = Confidence.HIGH,
            historyDays = 14,
        )
        val sentence = whatIfSentence(forecast, remainingBytes = 10_000_000_000L, now, zone, locale)
        assertEquals("Won't run out this cycle", sentence)
    }

    @Test
    fun what_if_sentence_formats_data_used_up() {
        val past = Instant.parse("2026-10-15T10:00:00Z")
        val forecast = Forecast(
            planId = 1L,
            runOutEarliest = past,
            runOutExpected = past,
            runOutLatest = past,
            projectedCycleEndBytes = 0L,
            confidence = Confidence.HIGH,
            historyDays = 14,
        )
        val sentence = whatIfSentence(forecast, remainingBytes = 0L, now, zone, locale)
        assertEquals("Data used up", sentence)
    }
}
