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

data class LiveNotificationText(val title: String, val text: String?, val contentDescription: String = "")

fun liveNotificationText(
    todayBytes: Long,
    rxBps: Long,
    units: ByteUnits,
    showSpeed: Boolean,
    offline: Boolean = false,
    remainingBytes: Long? = null,
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
    )
}

class LiveNotificationBuilder(private val context: Context) {
    fun build(text: LiveNotificationText): Notification {
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
}
