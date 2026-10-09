package io.github.khaledbahaaeldin.emberbyte.plans

import io.github.khaledbahaaeldin.emberbyte.common.forecastSentence
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.home.forecastToUi
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

data class WhatIfUi(
    val amountText: String,
    val unit: CapUnit,
    val currentText: String,
    val whatIfText: String?,
    val error: String?,
)

fun whatIfSentence(
    forecast: Forecast,
    remainingBytes: Long,
    now: Instant,
    zone: ZoneId,
    locale: Locale,
): String = forecastSentence(forecastToUi(forecast, now, zone, locale, remainingBytes))
