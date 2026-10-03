package io.github.khaledbahaaeldin.emberbyte.home

import io.github.khaledbahaaeldin.emberbyte.common.plainBytes
import io.github.khaledbahaaeldin.emberbyte.common.spokenBytes
import io.github.khaledbahaaeldin.emberbyte.common.weekdayBars
import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import io.github.khaledbahaaeldin.emberbyte.data.UnitSystem
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Confidence
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageStatus
import io.github.khaledbahaaeldin.emberbyte.engine.model.DayUsage
import io.github.khaledbahaaeldin.emberbyte.engine.model.Forecast
import io.github.khaledbahaaeldin.emberbyte.engine.model.GapReason
import io.github.khaledbahaaeldin.emberbyte.engine.model.LiveSpeed
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.UsagePoint
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.AppRowUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.GapUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.NetworkKindUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PermissionPromptUi
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

internal fun UnitSystem.toByteUnits(): ByteUnits = when (this) {
    UnitSystem.DECIMAL -> ByteUnits.DECIMAL
    UnitSystem.BINARY -> ByteUnits.BINARY
}

private fun confidenceLabel(confidence: Confidence): String = when (confidence) {
    Confidence.LOW -> "Low confidence"
    Confidence.MEDIUM -> "Medium confidence"
    Confidence.HIGH -> "High confidence"
}

private fun daysBetween(a: Instant, b: Instant): Long = abs(a.epochSecond - b.epochSecond) / 86_400

private fun Forecast.neverRunsOut(): Boolean =
    runOutEarliest == null && runOutExpected == null && runOutLatest == null

@JvmName("forecastToUiOrNull")
internal fun forecastToUi(forecast: Forecast?, now: Instant, zone: ZoneId, locale: Locale): ForecastUi? =
    forecast?.let { forecastToUi(it, now, zone, locale) }

internal fun forecastToUi(forecast: Forecast, now: Instant, zone: ZoneId, locale: Locale): ForecastUi {
    if (forecast.neverRunsOut()) {
        return ForecastUi("Safe", "Won't run out this cycle", confidenceLabel(forecast.confidence))
    }
    val expected = forecast.runOutExpected
    val at = expected ?: forecast.runOutEarliest ?: forecast.runOutLatest!!
    val zoned = at.atZone(zone)
    // Spec section 8: weekday, plus the date once the run-out is more than 6 days away.
    val headline = if (Duration.between(now, at) > Duration.ofDays(6)) {
        DateTimeFormatter.ofPattern("EEE d MMM", locale).format(zoned)
    } else {
        zoned.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    }
    val spread = max(
        forecast.runOutEarliest?.let { daysBetween(at, it) } ?: 0L,
        forecast.runOutLatest?.let { daysBetween(at, it) } ?: 0L,
    )
    val detail = when (spread) {
        0L -> "runs out"
        1L -> "runs out (±1 day)"
        else -> "runs out (±$spread days)"
    }
    return ForecastUi(headline, detail, confidenceLabel(forecast.confidence))
}

internal fun buildHomeUiState(
    today: DayUsage,
    live: LiveSpeed?,
    planState: PlanState?,
    forecast: Forecast?,
    apps: List<AppUsage>,
    week: List<UsagePoint>,
    units: UnitSystem,
    selectedDay: Int?,
    now: Instant,
    zone: ZoneId,
    locale: Locale,
    coverage: CoverageStatus = CoverageStatus(emptyList(), null),
    permissions: PermissionState? = null,
): HomeUiState {
    val byteUnits = units.toByteUnits()
    val validSelection = selectedDay?.takeIf { it in week.indices }

    val bars = weekdayBars(week, byteUnits, zone, locale)

    val forecastUi = forecastToUi(forecast, now, zone, locale)
    val isToday = validSelection == null

    val heroBytes: Long
    val heroLabel: String
    val description: String
    val subtitle: String?
    if (validSelection == null) {
        heroBytes = today.totalBytes
        heroLabel = "Today"
        description = buildString {
            append("${spokenBytes(heroBytes, byteUnits)} used today")
            if (planState != null) append(", ${spokenBytes(planState.remainingBytes, byteUnits)} left")
        }
        subtitle = planState?.let {
            val left = "${plainBytes(it.remainingBytes, byteUnits)} left"
            when {
                forecastUi == null -> left
                forecast?.neverRunsOut() == true -> "$left · won't run out this cycle"
                // "runs out (±1 day)" + "Thu" -> "runs out Thu (±1 day)"
                else -> "$left · ${forecastUi.detail.replace("runs out", "runs out ${forecastUi.headline}")}"
            }
        }
    } else {
        val point = week[validSelection]
        heroBytes = point.totalBytes
        heroLabel = point.start.atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        description = "${spokenBytes(heroBytes, byteUnits)} used on $heroLabel"
        subtitle = null
    }

    return HomeUiState(
        heroLabel = heroLabel,
        heroBytes = heroBytes,
        heroDescription = description,
        isToday = isToday,
        subtitle = subtitle,
        isEstimated = planState?.isApproximate ?: false,
        throughputBps = (live?.rxBps ?: 0L) + (live?.txBps ?: 0L),
        liveRxBps = live?.rxBps ?: 0L,
        liveTxBps = live?.txBps ?: 0L,
        network = when (live?.network) {
            NetworkKind.MOBILE -> NetworkKindUi.Mobile
            NetworkKind.WIFI -> NetworkKindUi.Wifi
            null -> null
        },
        mobileBytes = today.mobileBytes,
        wifiBytes = today.wifiBytes,
        forecast = forecastUi,
        topApps = apps.sortedByDescending { it.totalBytes }.take(3)
            .map { AppRowUi(it.packageName, it.label, it.mobileBytes, it.wifiBytes) },
        topAppsLocked = permissions?.usageAccess == false,
        week = bars,
        selectedDay = validSelection,
        prompts = permissionPrompts(permissions),
        gap = latestGap(coverage, now, zone),
        hasPlan = planState != null,
        units = byteUnits,
    )
}

internal fun permissionPrompts(permissions: PermissionState?): List<PermissionPromptUi> {
    if (permissions == null) return emptyList()
    return buildList {
        if (!permissions.usageAccess) {
            add(
                PermissionPromptUi(
                    id = "usage_access",
                    title = "Allow usage access",
                    body = "See which apps use your data. Until you allow it, Emberbyte shows totals only.",
                    actionLabel = "Open settings",
                ),
            )
        }
        if (!permissions.notifications) {
            add(
                PermissionPromptUi(
                    id = "notifications",
                    title = "Show live usage",
                    body = "Get today's usage and your current speed in the notification shade.",
                    actionLabel = "Allow",
                ),
            )
        }
    }
}

private val GAP_CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val MIN_GAP_FOR_BANNER: Duration = Duration.ofMinutes(5)
private val GAP_BANNER_WINDOW: Duration = Duration.ofHours(24)

private fun gapReasonText(reason: GapReason): String = when (reason) {
    GapReason.SERVICE_KILLED -> "the app was stopped"
    GapReason.REBOOT -> "the device restarted"
    GapReason.COUNTER_RESET -> "the counters reset"
    GapReason.PERMISSION_MISSING -> "usage access was off"
    GapReason.DEVICE_ASLEEP -> "the device was asleep"
}

/** The newest gap of at least 5 minutes that ended within the last 24 hours, as a banner message. */
internal fun latestGap(coverage: CoverageStatus, now: Instant, zone: ZoneId): GapUi? {
    val gap: CoverageGap = coverage.gaps
        .filter {
            Duration.between(it.from, it.to) >= MIN_GAP_FOR_BANNER &&
                it.to >= now.minus(GAP_BANNER_WINDOW) &&
                it.reason != GapReason.DEVICE_ASLEEP
        }
        .maxByOrNull { it.to } ?: return null
    val from = GAP_CLOCK.format(gap.from.atZone(zone))
    val to = GAP_CLOCK.format(gap.to.atZone(zone))
    return GapUi("Not measured $from–$to because ${gapReasonText(gap.reason)}")
}
