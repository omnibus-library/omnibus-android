package com.omnibus.omnibus.data.auth.remote

import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

class OmnibusNetworkInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
            .newBuilder()
            .header("Accept", "application/json")
            .build()

        return chain.proceed(request)
    }
}
