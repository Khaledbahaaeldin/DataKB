package io.github.khaledbahaaeldin.emberbyte

import android.app.Application

class EmberbyteApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
