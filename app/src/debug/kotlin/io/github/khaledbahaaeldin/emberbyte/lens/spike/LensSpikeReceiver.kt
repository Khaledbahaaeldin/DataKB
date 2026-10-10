package io.github.khaledbahaaeldin.emberbyte.lens.spike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.github.khaledbahaaeldin.emberbyte.lens.android.LensVpnService

class LensSpikeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "LensSpikeReceiver"
        const val ACTION_START = "io.github.khaledbahaaeldin.emberbyte.debug.LENS_START"
        const val ACTION_STOP = "io.github.khaledbahaaeldin.emberbyte.debug.LENS_STOP"
        const val ACTION_DUMP = "io.github.khaledbahaaeldin.emberbyte.debug.LENS_DUMP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START -> {
                Log.i(TAG, "Received LENS_START broadcast")
                val startIntent = LensVpnService.startIntent(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(startIntent)
                } else {
                    context.startService(startIntent)
                }
                setResultCode(0)
                setResultData("STARTED")
            }
            ACTION_STOP -> {
                Log.i(TAG, "Received LENS_STOP broadcast")
                val service = LensVpnService.activeInstance
                if (service != null) {
                    val stopIntent = LensVpnService.stopIntent(context)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            context.startForegroundService(stopIntent)
                        } catch (_: Exception) {
                            context.startService(stopIntent)
                        }
                    } else {
                        context.startService(stopIntent)
                    }
                }
                setResultCode(0)
                setResultData("STOPPED")
            }
            ACTION_DUMP -> {
                val service = LensVpnService.activeInstance
                if (service != null) {
                    val counters = service.getCounters()
                    val dumpStr = "open=${counters.openFlows} evicted=${counters.evictedFlows} errors=${counters.errors} " +
                            "tunRx=${counters.tunRxBytes} tunTx=${counters.tunTxBytes} " +
                            "opened=${counters.flowsOpened} knownUid=${counters.knownUidFlows} unknownUid=${counters.unknownUidFlows}"
                    Log.i(TAG, "LENS_DUMP: $dumpStr")
                    setResultCode(0)
                    setResultData(dumpStr)
                } else {
                    val dumpStr = "open=0 evicted=0 errors=0 tunRx=0 tunTx=0 opened=0 knownUid=0 unknownUid=0 (inactive)"
                    Log.i(TAG, "LENS_DUMP: $dumpStr")
                    setResultCode(0)
                    setResultData(dumpStr)
                }
            }
        }
    }
}
