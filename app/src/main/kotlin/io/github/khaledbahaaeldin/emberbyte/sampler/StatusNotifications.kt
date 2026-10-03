package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.khaledbahaaeldin.emberbyte.MainActivity
import io.github.khaledbahaaeldin.emberbyte.R

object StatusNotifications {
    const val PAUSED_ID = 1002

    /** "Measurement paused - tap to resume". Opening the app starts the service from the foreground, which Android allows. */
    fun showPaused(context: Context) {
        NotificationChannels.ensure(context)
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_stat_ember)
            .setContentTitle(context.getString(R.string.notification_paused_title))
            .setContentText(context.getString(R.string.notification_paused_text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(PAUSED_ID, notification)
    }

    fun cancelPaused(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(PAUSED_ID)
    }
}
