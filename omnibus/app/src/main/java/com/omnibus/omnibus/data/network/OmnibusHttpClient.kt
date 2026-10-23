package com.omnibus.omnibus.data.network

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The app's single HTTP client for talking to an Omnibus server.
 *
 * Paths are relative (`api/auth/login`, no leading slash) so they resolve beneath the server's
 * base URL, keeping any path prefix such as `https://example.com/omnibus`.
 */
@Singleton
class OmnibusHttpClient @Inject constructor(
    private val baseUrlProvider: ServerBaseUrlProvider,
    omnibusNetworkInterceptor: OmnibusNetworkInterceptor,
    authClientMetadataInterceptor: AuthClientMetadataInterceptor,
) {
    private val client = OkHttpClient.Builder()
        .addInterceptor(omnibusNetworkInterceptor)
        .addInterceptor(authClientMetadataInterceptor)
        .build()

    /** Builds a request for [path] on the saved server, or returns null if no server is saved. */
    suspend fun request(path: String): Request.Builder? =
        baseUrlProvider.baseUrl()?.let { request(it, path) }

    /** Builds a request for [path] on [baseUrl], for servers that haven't been saved yet. */
    fun request(baseUrl: HttpUrl, path: String): Request.Builder =
        Request.Builder().url(resolve(baseUrl, path))

    fun newCall(request: Request): Call = client.newCall(request)

    private fun resolve(baseUrl: HttpUrl, path: String): HttpUrl {
        require(!path.startsWith("/")) { "API paths must be relative: $path" }
        val directory = if (baseUrl.encodedPath.endsWith("/")) {
            baseUrl
        } else {
            baseUrl.newBuilder().encodedPath("${baseUrl.encodedPath}/").build()
        }
        return checkNotNull(directory.resolve(path)) { "Unable to resolve Omnibus API path: $path" }
    }
}
