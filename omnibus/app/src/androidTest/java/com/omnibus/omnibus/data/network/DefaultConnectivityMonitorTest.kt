package com.omnibus.omnibus.data.network

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultConnectivityMonitorTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun issue21_tc2_seedsFromCurrentNetworkWithoutPermissionError() {
        // Constructing it must not throw: the manifest declares ACCESS_NETWORK_STATE.
        val monitor = DefaultConnectivityMonitor(context)

        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }
        val expected = capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        assertEquals(expected, monitor.isOnline.value)
    }
}
