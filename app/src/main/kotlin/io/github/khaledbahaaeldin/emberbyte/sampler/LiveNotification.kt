package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import io.github.khaledbahaaeldin.emberbyte.R
import io.github.khaledbahaaeldin.emberbyte.common.remainingText
import io.github.khaledbahaaeldin.emberbyte.common.spokenBytes
import io.github.khaledbahaaeldin.emberbyte.nav.DeepLinks
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

data class LiveProgress(val fractionUsed: Float, val chip: String)

data class LiveNotificationText(
    val title: String,
    val text: String?,
    val contentDescription: String = "",
    val progress: LiveProgress? = null,
)

fun formatProgressChip(bytes: Long, units: ByteUnits): String {
    val safe = bytes.coerceAtLeast(0L)
    if (safe == 0L) return "0 B"
    var scaled = safe.toDouble()
    var index = 0
    while (index < units.labels.lastIndex && scaled >= units.base) {
        scaled /= units.base
        index++
    }
    if (index == 0) return "$safe ${units.labels[0]}"

    val (valueStr, finalUnit) = if (scaled < 10.0) {
        val r = BigDecimal.valueOf(scaled).setScale(1, RoundingMode.HALF_UP)
        if (r >= BigDecimal(10)) {
            r.setScale(0, RoundingMode.HALF_UP).toPlainString() to units.labels[index]
        } else {
            r.stripTrailingZeros().toPlainString() to units.labels[index]
        }
    } else {
        var r = BigDecimal.valueOf(scaled).setScale(0, RoundingMode.HALF_UP)
        if (index < units.labels.lastIndex && (r >= BigDecimal.valueOf(units.base) || (units == ByteUnits.BINARY && r >= BigDecimal(1000)))) {
            scaled /= units.base
            index++
            val nextUnit = units.labels[index]
            val nextR = BigDecimal.valueOf(scaled).setScale(1, RoundingMode.HALF_UP)
            val formatted = if (nextR >= BigDecimal(10)) {
                nextR.setScale(0, RoundingMode.HALF_UP)
            } else {
                nextR.stripTrailingZeros()
            }
            formatted.toPlainString() to nextUnit
        } else {
            if (index == units.labels.lastIndex && r >= BigDecimal(1000)) {
                r = BigDecimal(999)
            }
            r.toPlainString() to units.labels[index]
        }
    }
    return "$valueStr $finalUnit"
}

fun liveProgressOf(planState: PlanState?, units: ByteUnits): LiveProgress? {
    if (planState == null) return null
    val isExhausted = planState.remainingBytes <= 0L
    val fraction = if (isExhausted) {
        1f
    } else {
        val clamped = planState.fractionUsed.coerceIn(0f, 1f)
        (kotlin.math.round(clamped * 1000f) / 1000f)
    }
    val chip = if (isExhausted) "0 B" else formatProgressChip(planState.remainingBytes, units)
    return LiveProgress(fractionUsed = fraction, chip = chip)
}

fun liveNotificationText(
    todayBytes: Long,
    rxBps: Long,
    units: ByteUnits,
    showSpeed: Boolean,
    offline: Boolean = false,
    remainingBytes: Long? = null,
    progress: LiveProgress? = null,
): LiveNotificationText {
    val today = formatBytes(todayBytes, units)
    val spokenToday = spokenBytes(todayBytes, units)
    val parts = buildList {
        if (remainingBytes != null) {
            add(remainingText(remainingBytes, units))
        }
        if (showSpeed) {
            val speed = formatBytes(rxBps, units)
            add(if (offline) "No connection" else "↓ ${speed.value} ${speed.unit}/s")
        }
    }
    val spokenParts = buildList {
        add("$spokenToday used today")
        if (remainingBytes != null) {
            add("${spokenBytes(remainingBytes, units)} left")
        }
        if (showSpeed) {
            if (offline) {
                add("No connection")
            } else {
                val speed = formatBytes(rxBps, units)
                val unitSpoken = when (speed.unit) {
                    "GB", "GiB" -> "gigabytes"
                    "MB", "MiB" -> "megabytes"
                    "KB", "KiB" -> "kilobytes"
                    else -> "bytes"
                }
                add("${speed.value} $unitSpoken per second download")
            }
        }
    }
    return LiveNotificationText(
        title = "${today.value} ${today.unit} today",
        text = parts.joinToString(" · ").ifEmpty { null },
        contentDescription = spokenParts.joinToString(". ") + ".",
        progress = progress,
    )
}

enum class LiveNotificationStyle {
    COMPAT,
    PROGRESS_AND_CHIP,
    PROMOTED,
}

fun resolveLiveNotificationStyle(
    sdkInt: Int,
    sdkIntFull: Int,
    hasProgress: Boolean,
): LiveNotificationStyle {
    if (!hasProgress || sdkInt < 36) return LiveNotificationStyle.COMPAT
    return if (sdkInt > 36 || sdkIntFull >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
        LiveNotificationStyle.PROMOTED
    } else {
        LiveNotificationStyle.PROGRESS_AND_CHIP
    }
}

interface PlatformNotificationCaller {
    fun setProgressStyle(builder: Notification.Builder, fractionUsed: Float): Notification.Builder
    fun setShortCriticalText(builder: Notification.Builder, chip: String): Notification.Builder
    fun setRequestPromotedOngoing(builder: Notification.Builder, request: Boolean): Notification.Builder
}

internal object DefaultPlatformNotificationCaller : PlatformNotificationCaller {
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    override fun setProgressStyle(builder: Notification.Builder, fractionUsed: Float): Notification.Builder {
        val progressUnits = (fractionUsed * 1000f).roundToInt()
        val style = Notification.ProgressStyle()
            .setProgress(progressUnits)
            .addProgressSegment(Notification.ProgressStyle.Segment(1000))
        return builder.setStyle(style)
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    override fun setShortCriticalText(builder: Notification.Builder, chip: String): Notification.Builder {
        return builder.setShortCriticalText(chip)
    }

    // Lint NewApi: "Call requires API level 36.1 (current min is 36): android.app.Notification.Builder#setRequestPromotedOngoing"
    // setRequestPromotedOngoing was finalized in Android 16 QPR2 (API 36.1); @RequiresApi only accepts integer API levels.
    @SuppressLint("NewApi")
    override fun setRequestPromotedOngoing(builder: Notification.Builder, request: Boolean): Notification.Builder {
        return builder.setRequestPromotedOngoing(request)
    }
}

open class LiveNotificationBuilder(
    private val context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val sdkIntFull: Int = if (Build.VERSION.SDK_INT >= 36) Build.VERSION.SDK_INT_FULL else 0,
    private val platformCaller: PlatformNotificationCaller = DefaultPlatformNotificationCaller,
) {
    open fun build(text: LiveNotificationText): Notification {
        val style = resolveLiveNotificationStyle(sdkInt, sdkIntFull, text.progress != null)
        if (style == LiveNotificationStyle.COMPAT || text.progress == null) {
            return buildStandard(text)
        }
        return buildLiveUpdateNotification(text, text.progress, style)
    }

    fun buildStandard(text: LiveNotificationText): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            DeepLinks.intent(context, DeepLinks.HOME),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, NotificationChannels.LIVE)
            .setSmallIcon(R.drawable.ic_stat_ember)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setTicker(text.contentDescription)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }

    private fun buildLiveUpdateNotification(
        text: LiveNotificationText,
        progress: LiveProgress,
        style: LiveNotificationStyle,
    ): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            DeepLinks.intent(context, DeepLinks.HOME),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(context, NotificationChannels.LIVE)
            .setSmallIcon(R.drawable.ic_stat_ember)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setTicker(text.contentDescription)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(open)

        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        platformCaller.setProgressStyle(builder, progress.fractionUsed)
        platformCaller.setShortCriticalText(builder, progress.chip)
        if (style == LiveNotificationStyle.PROMOTED) {
            platformCaller.setRequestPromotedOngoing(builder, true)
        }
        return builder.build()
    }
}
