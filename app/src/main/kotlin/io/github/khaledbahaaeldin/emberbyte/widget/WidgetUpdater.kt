package io.github.khaledbahaaeldin.emberbyte.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        HeroWidget().updateAll(context)
        BentoWidget().updateAll(context)
    }
}
