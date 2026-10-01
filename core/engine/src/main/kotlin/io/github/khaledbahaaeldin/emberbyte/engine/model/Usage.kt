package io.github.khaledbahaaeldin.emberbyte.engine.model

import java.time.Instant
import java.time.LocalDate

enum class NetworkKind { MOBILE, WIFI }

/** `network == null` means offline. */
data class LiveSpeed(val rxBps: Long, val txBps: Long, val network: NetworkKind?, val at: Instant)

data class UsageFilter(val network: NetworkKind? = null, val subscriptionId: Int? = null)

enum class Granularity { HOUR, DAY, WEEK, MONTH }

data class DateRange(val from: Instant, val to: Instant)

data class UsagePoint(val start: Instant, val mobileBytes: Long, val wifiBytes: Long) {
    val totalBytes: Long get() = mobileBytes + wifiBytes
}

data class DayUsage(val date: LocalDate, val mobileBytes: Long, val wifiBytes: Long) {
    val totalBytes: Long get() = mobileBytes + wifiBytes
}

data class AppUsage(
    val packageName: String,
    val label: String,
    val uid: Int,
    val mobileBytes: Long,
    val wifiBytes: Long,
    val screenTimeMs: Long?,
) {
    val totalBytes: Long get() = mobileBytes + wifiBytes
}

enum class AppSort { BYTES_DESC, BYTES_ASC, NAME, SCREEN_TIME_DESC }

enum class GapReason { SERVICE_KILLED, REBOOT, COUNTER_RESET, PERMISSION_MISSING }

data class CoverageGap(val from: Instant, val to: Instant, val reason: GapReason)

data class CoverageStatus(val gaps: List<CoverageGap>, val lastSampleAt: Instant?)
