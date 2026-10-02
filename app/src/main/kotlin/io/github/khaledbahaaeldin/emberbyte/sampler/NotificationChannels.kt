package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.khaledbahaaeldin.emberbyte.R

object NotificationChannels {
    const val LIVE = "live"
    const val STATUS = "status"

    /** Creates the channels (idempotent). Only `live` and `status` exist in M2; `alerts`, `lens`, `spikes` come with their features. */
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
    }
}
