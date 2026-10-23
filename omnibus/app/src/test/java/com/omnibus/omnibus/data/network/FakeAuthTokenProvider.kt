package com.omnibus.omnibus.data.network

/**
 * In-memory [AuthTokenProvider] for tests.
 *
 * @param token the token returned by [token] until [invalidate] is called.
 */
class FakeAuthTokenProvider(var token: String? = null) : AuthTokenProvider {

    /** How many times [invalidate] has been called. */
    var invalidateCalls = 0
        private set

    override suspend fun token(): String? = token

    override suspend fun invalidate() {
        invalidateCalls++
        token = null
    }
}
