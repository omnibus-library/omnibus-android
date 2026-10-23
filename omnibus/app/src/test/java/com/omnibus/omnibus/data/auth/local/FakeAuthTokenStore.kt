package com.omnibus.omnibus.data.auth.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * In-memory [AuthTokenStore] for tests.
 *
 * @param initialToken token present before any [save] call.
 */
class FakeAuthTokenStore(initialToken: String? = null) : AuthTokenStore {

    private val _token = MutableStateFlow(initialToken)

    /** When non-null, collecting [token] fails with this instead of emitting. */
    var readFailure: Throwable? = null

    override val token: Flow<String?> = flow {
        readFailure?.let { throw it }
        emitAll(_token)
    }

    /** When non-null, [save] throws this instead of storing the token. */
    var saveFailure: Throwable? = null

    override suspend fun save(token: String) {
        saveFailure?.let { throw it }
        _token.value = token
    }

    /** When non-null, [clear] throws this instead of removing the token. */
    var clearFailure: Throwable? = null

    /** How many times [clear] has been called, including calls that failed. */
    var clearCalls = 0
        private set

    override suspend fun clear() {
        clearCalls++
        clearFailure?.let { throw it }
        _token.value = null
    }
}
