package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import io.github.khaledbahaaeldin.emberbyte.R
import io.github.khaledbahaaeldin.emberbyte.data.budget.BudgetNotice
import io.github.khaledbahaaeldin.emberbyte.nav.DeepLinks
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits

object BudgetNotifier {
    const val TAG = "budget-alert"

    fun post(context: Context, notice: BudgetNotice, units: ByteUnits) {
        NotificationChannels.ensure(context)
        val text = budgetText(notice, units)
        val id = notice.budget.packageName.hashCode() and Int.MAX_VALUE
        val open = PendingIntent.getActivity(
            context,
            id,
            DeepLinks.intent(context, DeepLinks.app(notice.budget.packageName)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val settings = PendingIntent.getActivity(
            context,
            id xor 0x5E77,
            DataSettingsIntent.forPackage(context, notice.budget.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.ALERTS)
            .setSmallIcon(R.drawable.ic_stat_ember)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.notification_action_data_settings), settings)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(TAG, id, notification)
    }
}
