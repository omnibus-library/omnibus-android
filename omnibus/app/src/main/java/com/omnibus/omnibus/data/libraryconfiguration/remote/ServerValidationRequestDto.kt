package com.omnibus.omnibus.data.libraryconfiguration.remote

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Probe of a candidate server's unauthenticated `GET /api/_health` endpoint, used to confirm an
 * address points at a live Omnibus server before it's saved.
 *
 * @property serverUrl absolute base URL including scheme (e.g. `https://read.example.com`). Any
 * path prefix is kept, so servers behind a reverse-proxy subpath resolve correctly.
 */
data class ServerValidationRequestDto(
    val serverUrl: String,
) {
    /**
     * @throws IllegalArgumentException if [serverUrl] isn't a valid `http`/`https` URL.
     */
    fun toRequest(): Request =
        Request.Builder()
            .url((serverUrl.trimEnd('/') + HEALTH_PATH).toHttpUrl())
            .get()
            .build()

    companion object {
        const val HEALTH_PATH = "/api/_health"
    }
}
