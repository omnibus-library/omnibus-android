package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.network.OmnibusHttpClient
import okhttp3.Call
import okhttp3.Request

/**
 * The call factory audio streams use (AND-STK-3): every audio fetch goes through the app's one OkHttp client and its
 * interceptors, so it shares the connection pool and the auth behaviour.
 */
internal fun audioCallFactory(client: OmnibusHttpClient): Call.Factory = object : Call.Factory {
    override fun newCall(request: Request): Call = client.newCall(request)
}
