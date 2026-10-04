package io.github.khaledbahaaeldin.emberbyte.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.khaledbahaaeldin.emberbyte.EmberbyteApplication
import io.github.khaledbahaaeldin.emberbyte.nav.DeepLinks

class BentoWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(DpSize(250.dp, 110.dp), DpSize(250.dp, 250.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = (context.applicationContext as EmberbyteApplication).graph
        val model = bentoWidgetModel(WidgetData.load(graph.usage, graph.plans, graph.settings, graph.clock))
        val open = actionStartActivity(DeepLinks.intent(context, DeepLinks.HOME))
        provideContent { GlanceTheme { BentoContent(model, open) } }
    }
}

class BentoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BentoWidget()
}

@Composable
private fun BentoContent(model: BentoWidgetModel, open: Action) {
    val tall = LocalSize.current.height >= 200.dp
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(12.dp).clickable(open),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(model.hero.number, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            Text(" ${model.hero.unit}", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
        }
        Text(model.hero.caption, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp))
        Row {
            Text(model.forecast, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
            Text(" · ", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
            Text(model.speed, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
        }
        if (tall) {
            for (app in model.topApps) {
                Text(app, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
            }
        }
    }
}
