package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat

object AlertGate {
    fun isEnabled(context: Context, channelId: String = NotificationChannels.ALERTS): Boolean {
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        val channel = manager.getNotificationChannel(channelId)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }
}
