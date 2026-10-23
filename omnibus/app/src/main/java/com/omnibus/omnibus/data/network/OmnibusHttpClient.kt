package com.omnibus.omnibus.data.network

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * The app's single HTTP client for talking to an Omnibus server.
 *
 * Paths are relative (`api/auth/login`, no leading slash) so they resolve beneath the server's
 * base URL, keeping any path prefix such as `https://example.com/omnibus`.
 */
@Singleton
class OmnibusHttpClient @Inject constructor(
    private val baseUrlProvider: ServerBaseUrlProvider,
    private val authTokenProvider: AuthTokenProvider,
    omnibusNetworkInterceptor: OmnibusNetworkInterceptor,
    authClientMetadataInterceptor: AuthClientMetadataInterceptor,
) {
    private val client = OkHttpClient.Builder()
        .addInterceptor(omnibusNetworkInterceptor)
        .addInterceptor(authClientMetadataInterceptor)
        .build()

    /**
     * Builds a request for [path] on the saved server, or returns null if no server is saved.
     * Sets `Authorization: Bearer <token>` when a non-blank token is stored.
     */
    suspend fun request(path: String): Request.Builder? {
        val baseUrl = baseUrlProvider.baseUrl() ?: return null
        val builder = request(baseUrl, path)
        authTokenProvider.token()?.takeIf { it.isNotBlank() }?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }
        return builder
    }

    /**
     * Builds a request for [path] on [baseUrl], for servers that haven't been saved yet,
     * so it never carries the bearer token.
     */
    fun request(baseUrl: HttpUrl, path: String): Request.Builder =
        Request.Builder().url(resolve(baseUrl, path))

    fun newCall(request: Request): Call = client.newCall(request)

    /**
     * Executes [request]. A 401 on a request that carried a bearer token (other than sign-in)
     * invalidates the stored token. Throws [IOException] when the server can't be reached.
     */
    suspend fun execute(request: Request): Response {
        val response = client.newCall(request).execute()
        if (
            response.code == 401 &&
            request.header("Authorization") != null &&
            !isSignInPath(request.url.encodedPath)
        ) {
            try {
                authTokenProvider.invalidate()
            } catch (t: Throwable) {
                response.close()
                throw t
            }
        }
        return response
    }

    private fun resolve(baseUrl: HttpUrl, path: String): HttpUrl {
        require(!path.startsWith("/")) { "API paths must be relative: $path" }
        val directory = if (baseUrl.encodedPath.endsWith("/")) {
            baseUrl
        } else {
            baseUrl.newBuilder().encodedPath("${baseUrl.encodedPath}/").build()
        }
        return checkNotNull(directory.resolve(path)) { "Unable to resolve Omnibus API path: $path" }
    }

    private companion object {
        fun isSignInPath(encodedPath: String): Boolean =
            encodedPath.endsWith("/api/auth/login") ||
                encodedPath.endsWith("/api/auth/register")
    }
}
