package com.omnibus.omnibus.data.libraries.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [LibraryTokenStore] for tests.
 *
 * @param initialTokens tokens present before any [save] call, by library id.
 */
class TestLibraryTokenStore(initialTokens: Map<String, String> = emptyMap()) : LibraryTokenStore {

    private val tokens = MutableStateFlow(initialTokens)

    /** When non-null, [clear] throws this instead of removing the token. */
    var clearFailure: Throwable? = null

    override fun getTokenFlow(libraryId: String): Flow<String?> = tokens.map { it[libraryId] }

    override suspend fun save(libraryId: String, token: String) {
        tokens.value = tokens.value + (libraryId to token)
    }

    override suspend fun clear(libraryId: String) {
        clearFailure?.let { throw it }
        tokens.value = tokens.value - libraryId
    }
}
