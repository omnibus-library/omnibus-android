package com.omnibus.omnibus.data.network

import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/** Sends `Accept: application/json` on every request that doesn't set its own `Accept`. */
class OmnibusNetworkInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // Requests that name their own media type (cover thumbnails ask for images) keep it.
        val request = if (original.header("Accept") == null) {
            original.newBuilder().header("Accept", "application/json").build()
        } else {
            original
        }

        return chain.proceed(request)
    }
}
