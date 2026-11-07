package com.omnibus.omnibus.data.network

import com.omnibus.omnibus.testing.randomBoolean
import kotlinx.coroutines.flow.MutableStateFlow

/** A [ConnectivityMonitor] whose [isOnline] tests set directly. */
class TestConnectivityMonitor(online: Boolean = randomBoolean()) : ConnectivityMonitor {

    override val isOnline: MutableStateFlow<Boolean> = MutableStateFlow(online)
}
