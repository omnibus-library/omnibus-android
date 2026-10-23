package com.omnibus.omnibus.data.auth.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [AuthTokenStore] for tests.
 *
 * @param initialToken token present before any [save] call.
 */
class FakeAuthTokenStore(initialToken: String? = null) : AuthTokenStore {

    private val _token = MutableStateFlow(initialToken)
    override val token: Flow<String?> = _token.asStateFlow()

    /** When non-null, [save] throws this instead of storing the token. */
    var saveFailure: Throwable? = null

    override suspend fun save(token: String) {
        saveFailure?.let { throw it }
        _token.value = token
    }

    /** When non-null, [clear] throws this instead of removing the token. */
    var clearFailure: Throwable? = null

    override suspend fun clear() {
        clearFailure?.let { throw it }
        _token.value = null
    }
}
