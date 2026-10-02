package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.khaledbahaaeldin.emberbyte.MainActivity
import io.github.khaledbahaaeldin.emberbyte.R
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.formatBytes

data class LiveNotificationText(val title: String, val text: String?)

fun liveNotificationText(todayBytes: Long, rxBps: Long, units: ByteUnits, showSpeed: Boolean): LiveNotificationText {
    val today = formatBytes(todayBytes, units)
    val speed = formatBytes(rxBps, units)
    return LiveNotificationText(
        title = "${today.value} ${today.unit} today",
        text = if (showSpeed) "↓ ${speed.value} ${speed.unit}/s" else null,
    )
}

class LiveNotificationBuilder(private val context: Context) {
    fun build(text: LiveNotificationText): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, NotificationChannels.LIVE)
            .setSmallIcon(R.drawable.ic_stat_ember)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }
}
