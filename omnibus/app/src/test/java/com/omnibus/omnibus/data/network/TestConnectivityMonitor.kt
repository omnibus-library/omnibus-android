package com.omnibus.omnibus.data.network

import kotlinx.coroutines.flow.MutableStateFlow

/** A [ConnectivityMonitor] whose [isOnline] tests set directly. */
class TestConnectivityMonitor(online: Boolean = true) : ConnectivityMonitor {

    override val isOnline: MutableStateFlow<Boolean> = MutableStateFlow(online)
}
