package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import io.github.khaledbahaaeldin.emberbyte.R
import io.github.khaledbahaaeldin.emberbyte.data.alert.AlertNotice
import io.github.khaledbahaaeldin.emberbyte.nav.DeepLinks
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.time.ZoneId
import java.util.Locale

object AlertNotifier {
    const val TAG = "plan-alert"

    fun post(context: Context, notice: AlertNotice, units: ByteUnits) {
        NotificationChannels.ensure(context)
        val text = alertText(notice, units, ZoneId.systemDefault(), Locale.getDefault())
        val open = PendingIntent.getActivity(
            context, notice.planId.toInt() + 100, DeepLinks.intent(context, DeepLinks.plan(notice.planId)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.ALERTS)
            .setSmallIcon(R.drawable.ic_stat_ember)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setTicker(text.contentDescription)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(TAG, notice.planId.toInt(), notification)
    }
}
