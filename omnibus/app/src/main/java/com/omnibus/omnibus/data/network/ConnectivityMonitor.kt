package com.omnibus.omnibus.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Reports whether the device can reach the internet. */
interface ConnectivityMonitor {

    /** True while the default network has validated internet access. */
    val isOnline: StateFlow<Boolean>
}

/**
 * Tracks the default network with [ConnectivityManager.registerDefaultNetworkCallback]. The state is
 * seeded from the current network, so the first value is never assumed.
 *
 * The callback is registered once and never unregistered. This singleton lives as long as the process.
 */
@Singleton
class DefaultConnectivityMonitor @Inject constructor(
    @ApplicationContext context: Context,
) : ConnectivityMonitor {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(currentlyOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // Callbacks arrive on a binder thread. MutableStateFlow is thread-safe, so no dispatcher is needed.
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = update(NetworkEvent.Available)

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            update(capabilities.toEvent())

        override fun onLost(network: Network) = update(NetworkEvent.Lost)

        override fun onUnavailable() = update(NetworkEvent.Unavailable)
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    private fun update(event: NetworkEvent) {
        _isOnline.value = NetworkStatus.isOnlineAfter(event)
    }

    private fun currentlyOnline(): Boolean {
        val capabilities = connectivityManager.activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it) }
        return NetworkStatus.isOnlineAfter(capabilities?.toEvent() ?: NetworkEvent.Unavailable)
    }

    private fun NetworkCapabilities.toEvent() = NetworkEvent.CapabilitiesChanged(
        hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        validated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
    )
}
