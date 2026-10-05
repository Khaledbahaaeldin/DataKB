package io.github.khaledbahaaeldin.emberbyte.widget

import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetManifestTest {
    @Test fun receivers_are_declared_and_exported() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pm = context.packageManager
        val intent = Intent("android.appwidget.action.APPWIDGET_UPDATE").setPackage(context.packageName)
        val receivers = pm.queryBroadcastReceivers(intent, 0)
        val names = receivers.map { it.activityInfo.name }
        assertTrue(names.contains("${context.packageName}.widget.HeroWidgetReceiver"))
        assertTrue(names.contains("${context.packageName}.widget.BentoWidgetReceiver"))
        
        val heroReceiver = receivers.find { it.activityInfo.name == "${context.packageName}.widget.HeroWidgetReceiver" }
        assertTrue(heroReceiver?.activityInfo?.exported == true)

        val bentoReceiver = receivers.find { it.activityInfo.name == "${context.packageName}.widget.BentoWidgetReceiver" }
        assertTrue(bentoReceiver?.activityInfo?.exported == true)
    }
}
