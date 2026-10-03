package io.github.khaledbahaaeldin.emberbyte

import android.app.Application
import io.github.khaledbahaaeldin.emberbyte.sampler.CatchUpScheduler
import io.github.khaledbahaaeldin.emberbyte.sampler.NotificationChannels

class EmberbyteApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        CatchUpScheduler.schedule(this)
    }
}
