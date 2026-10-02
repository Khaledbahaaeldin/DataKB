package io.github.khaledbahaaeldin.emberbyte.sampler

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

object ServiceStarter {
    private const val TAG = "ServiceStarter"

    /** Starts the sampler service. Never throws: Android may refuse a foreground start from the background. */
    fun start(context: Context) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, SamplerService::class.java))
        } catch (error: IllegalStateException) { // includes ForegroundServiceStartNotAllowedException
            Log.w(TAG, "Foreground start not allowed right now", error)
        } catch (error: SecurityException) {
            Log.w(TAG, "Foreground start refused", error)
        }
    }
}
