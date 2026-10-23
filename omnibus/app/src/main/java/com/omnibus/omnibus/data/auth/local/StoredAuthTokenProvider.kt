package com.omnibus.omnibus.data.auth.local

import com.omnibus.omnibus.data.network.AuthTokenProvider
import javax.inject.Inject

/** Supplies the token saved in [AuthTokenStore], and clears it when the server rejects it. */
class StoredAuthTokenProvider @Inject constructor(
    private val authTokenStore: AuthTokenStore,
) : AuthTokenProvider {

    // STUB(Stage 2): replace with the real implementation.
    override suspend fun token(): String? = TODO("Stage 2")

    // STUB(Stage 2): replace with the real implementation.
    override suspend fun invalidate(): Unit = TODO("Stage 2")
}
