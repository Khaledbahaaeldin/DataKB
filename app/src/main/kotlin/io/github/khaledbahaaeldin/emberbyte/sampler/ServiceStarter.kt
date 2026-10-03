package io.github.khaledbahaaeldin.emberbyte.sampler

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

object ServiceStarter {
    private const val TAG = "ServiceStarter"

    /** Starts the sampler service. Returns false when Android refused (a foreground start from the background); never throws. */
    fun start(context: Context): Boolean = try {
        ContextCompat.startForegroundService(context, Intent(context, SamplerService::class.java))
        true
    } catch (error: IllegalStateException) { // includes ForegroundServiceStartNotAllowedException
        Log.w(TAG, "Foreground start not allowed right now", error)
        false
    } catch (error: SecurityException) {
        Log.w(TAG, "Foreground start refused", error)
        false
    }
}
