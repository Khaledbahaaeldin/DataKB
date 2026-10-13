package io.github.khaledbahaaeldin.emberbyte.lens.android

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

class UnderlyingNetworks(
    private val connectivityManager: ConnectivityManager,
    private val onUnderlyingNetworkChanged: (Network?) -> Unit
) {
    private var callback: ConnectivityManager.NetworkCallback? = null
    var currentUnderlyingNetwork: Network? = null
        private set

    fun start() {
        if (callback != null) return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                currentUnderlyingNetwork = network
                onUnderlyingNetworkChanged(network)
            }

            override fun onLost(network: Network) {
                if (currentUnderlyingNetwork == network) {
                    currentUnderlyingNetwork = null
                    onUnderlyingNetworkChanged(null)
                }
            }
        }
        callback = cb
        connectivityManager.registerNetworkCallback(request, cb)
    }

    fun stop() {
        callback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        callback = null
        currentUnderlyingNetwork = null
    }
}
