package io.github.khaledbahaaeldin.emberbyte.lens.spike

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import io.github.khaledbahaaeldin.emberbyte.lens.android.LensVpnService

class LensSpikeActivity : Activity() {

    companion object {
        private const val TAG = "LensSpikeActivity"
        private const val REQUEST_VPN = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = VpnService.prepare(this)
        if (intent != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_VPN)
        } else {
            Log.i(TAG, "VPN permission already prepared/granted")
            startLens()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) {
                Log.i(TAG, "VPN permission granted by user")
                startLens()
            } else {
                Log.w(TAG, "VPN permission denied by user (resultCode=$resultCode)")
            }
            finish()
        }
    }

    private fun startLens() {
        val startIntent = LensVpnService.startIntent(this)
        startForegroundService(startIntent)
    }
}
