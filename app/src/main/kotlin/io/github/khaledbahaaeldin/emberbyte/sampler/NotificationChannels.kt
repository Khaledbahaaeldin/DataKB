package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.khaledbahaaeldin.emberbyte.R

object NotificationChannels {
    const val LIVE = "live"
    const val STATUS = "status"
    const val ALERTS = "alerts"
    const val SPIKES = "spikes"

    /** Creates the channels (idempotent). `live`, `status`, `alerts` and `spikes` exist. */
    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            LIVE,
            context.getString(R.string.channel_live_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_live_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
        manager.createNotificationChannel(
            NotificationChannel(STATUS, context.getString(R.string.channel_status_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_status_description)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERTS, context.getString(R.string.channel_alerts_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_alerts_description)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(SPIKES, context.getString(R.string.channel_spikes_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_spikes_description)
            },
        )
    }
}
