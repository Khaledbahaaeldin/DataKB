package io.github.khaledbahaaeldin.emberbyte.data.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.github.khaledbahaaeldin.emberbyte.data.source.NetworkKindSource
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ConnectivityNetworkKindSource(context: Context) : NetworkKindSource {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val state = MutableStateFlow(kindOf(manager.getNetworkCapabilities(manager.activeNetwork)))
    override val current: StateFlow<NetworkKind?> = state

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                state.value = kindOf(capabilities)
            }

            override fun onLost(network: Network) {
                state.value = null
            }
        })
    }

    private fun kindOf(capabilities: NetworkCapabilities?): NetworkKind? = when {
        capabilities == null -> null
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.MOBILE
        else -> null
    }
}
