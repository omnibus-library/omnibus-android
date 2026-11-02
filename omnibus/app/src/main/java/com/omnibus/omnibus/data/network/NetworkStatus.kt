package com.omnibus.omnibus.data.network

/** What the default-network callback reported, without Android types, so the mapping runs on the JVM. */
sealed interface NetworkEvent {

    /** A default network became available. Android follows it with [CapabilitiesChanged], which decides. */
    data object Available : NetworkEvent

    /** The default network's capabilities changed. [validated] is whether Android has confirmed internet access. */
    data class CapabilitiesChanged(val hasInternet: Boolean, val validated: Boolean) : NetworkEvent

    /** The default network was lost. */
    data object Lost : NetworkEvent

    /** No default network is available. */
    data object Unavailable : NetworkEvent
}

/** Maps default-network events to whether the device is online. */
object NetworkStatus {

    /** Online only for a network that has internet and has been validated. */
    fun isOnlineAfter(event: NetworkEvent): Boolean =
        event is NetworkEvent.CapabilitiesChanged && event.hasInternet && event.validated
}
