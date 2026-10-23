package com.omnibus.omnibus.data.auth.local

import android.util.Log
import com.omnibus.omnibus.data.network.AuthTokenProvider
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Supplies the token saved in [AuthTokenStore], and clears it when the server rejects it. */
class StoredAuthTokenProvider @Inject constructor(
    private val authTokenStore: AuthTokenStore,
) : AuthTokenProvider {

    override suspend fun token(): String? {
        val stored = try {
            authTokenStore.token.first()
        } catch (ex: IOException) {
            Log.e(TAG, "Unable to read the stored auth token", ex)
            null
        }
        return stored?.takeIf { it.isNotBlank() }
    }

    override suspend fun invalidate() {
        try {
            authTokenStore.clear()
        } catch (ex: IOException) {
            Log.e(TAG, "Unable to clear the stored auth token", ex)
        }
    }

    private companion object {
        const val TAG = "StoredAuthTokenProvider"
    }
}
