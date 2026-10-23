package com.omnibus.omnibus.data.network

/** Supplies and invalidates the bearer token attached to API requests. */
interface AuthTokenProvider {
    /** The stored token, or null when signed out. */
    suspend fun token(): String?

    /** Called when the server rejects the token, so the session ends. */
    suspend fun invalidate()
}
