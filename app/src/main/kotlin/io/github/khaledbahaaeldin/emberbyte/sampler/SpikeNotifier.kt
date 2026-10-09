package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import io.github.khaledbahaaeldin.emberbyte.R
import io.github.khaledbahaaeldin.emberbyte.engine.model.SpikeEvent
import io.github.khaledbahaaeldin.emberbyte.nav.DeepLinks
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits

object SpikeNotifier {
    const val TAG = "spike"

    fun post(context: Context, event: SpikeEvent, label: String, units: ByteUnits) {
        NotificationChannels.ensure(context)
        val text = spikeText(event, label, units)
        val id = event.packageName.hashCode() and Int.MAX_VALUE
        val open = PendingIntent.getActivity(
            context,
            id,
            DeepLinks.intent(context, DeepLinks.app(event.packageName)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val settings = PendingIntent.getActivity(
            context,
            id xor 0x5E77,
            DataSettingsIntent.forPackage(context, event.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.SPIKES)
            .setSmallIcon(R.drawable.ic_stat_ember)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.notification_action_data_settings), settings)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(TAG, id, notification)
    }

    fun post(context: Context, event: SpikeEvent, units: ByteUnits) {
        val label = try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(event.packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            event.packageName
        }
        post(context, event, label, units)
    }
}
