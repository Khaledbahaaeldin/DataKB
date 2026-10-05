package io.github.khaledbahaaeldin.emberbyte.widget

import io.github.khaledbahaaeldin.emberbyte.common.forecastSentence
import io.github.khaledbahaaeldin.emberbyte.common.plainBytes
import io.github.khaledbahaaeldin.emberbyte.common.remainingText
import io.github.khaledbahaaeldin.emberbyte.home.forecastToUi
import io.github.khaledbahaaeldin.emberbyte.home.toByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes

data class HeroWidgetModel(val number: String, val unit: String, val caption: String)
data class BentoWidgetModel(val hero: HeroWidgetModel, val forecast: String, val speed: String, val topApps: List<String>, val compactApps: String = "")

internal fun heroWidgetModel(s: WidgetSnapshot): HeroWidgetModel {
    val units = s.units.toByteUnits()
    val today = formatBytes(s.today.totalBytes, units)
    val caption = s.plan?.let { remainingText(it.remainingBytes, units) } ?: "Set up a plan"
    return HeroWidgetModel(today.value, today.unit, caption)
}

internal fun bentoWidgetModel(s: WidgetSnapshot): BentoWidgetModel {
    val units = s.units.toByteUnits()
    val forecast = when {
        s.plan == null -> "No plan yet"
        s.forecast == null -> "Not enough history yet"
        else -> forecastSentence(forecastToUi(s.forecast, s.now, s.zone, s.locale, s.plan.remainingBytes))
    }
    val speedObj = s.speed
    val speed = when {
        speedObj == null -> "—"
        speedObj.network == null -> "Offline"
        else -> "↓ ${plainBytes(speedObj.rxBps, units)}/s"
    }
    val topApps = s.topApps.map { "${it.label} · ${plainBytes(it.totalBytes, units)}" }
    val compactApps = topApps.joinToString("  ·  ")
    return BentoWidgetModel(heroWidgetModel(s), forecast, speed, topApps, compactApps)
}
