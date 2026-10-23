package com.omnibus.omnibus.data.network

import okhttp3.HttpUrl

/** Supplies the Omnibus server that API requests are sent to. */
interface ServerBaseUrlProvider {
    /** The configured server, or null if none is saved or the saved value isn't a valid URL. */
    suspend fun baseUrl(): HttpUrl?
}
