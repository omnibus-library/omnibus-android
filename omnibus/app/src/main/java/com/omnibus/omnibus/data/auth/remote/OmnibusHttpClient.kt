package com.omnibus.omnibus.data.auth.remote

import com.omnibus.omnibus.data.auth.di.BaseUrl
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmnibusHttpClient @Inject constructor(
    @BaseUrl baseUrl: String,
    omnibusNetworkInterceptor: OmnibusNetworkInterceptor,
    authClientMetadataInterceptor: AuthClientMetadataInterceptor,
) {
    private val baseUrl = baseUrl.toHttpUrl()
    private val client = OkHttpClient.Builder()
        .addInterceptor(omnibusNetworkInterceptor)
        .addInterceptor(authClientMetadataInterceptor)
        .build()

    fun request(path: String): Request.Builder =
        Request.Builder().url(resolve(path))

    fun newCall(request: Request): Call = client.newCall(request)

    private fun resolve(path: String): HttpUrl =
        checkNotNull(baseUrl.resolve(path)) {
            "Unable to resolve Omnibus API path: $path"
        }
}