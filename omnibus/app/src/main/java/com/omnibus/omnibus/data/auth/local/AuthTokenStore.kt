package com.omnibus.omnibus.data.auth.local

import android.util.Log
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.libraries.local.LibraryTokenStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Persists the auth token issued by the server, for the active library. */
interface AuthTokenStore {
    /** The active library's token, or null when signed out there. */
    val token: Flow<String?>

    /** Saves [token] for the active library. Nothing is saved when there is no active library. */
    suspend fun save(token: String)

    /** Removes the active library's token. Other libraries keep theirs. */
    suspend fun clear()
}

/**
 * [AuthTokenStore] over the active library's token in [LibraryTokenStore]. It follows the active library, so a library
 * added in this process is seen at once.
 */
@Singleton
class DefaultAuthTokenStore @Inject constructor(
    private val tokens: LibraryTokenStore,
    private val libraries: LibrariesRepository,
) : AuthTokenStore {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val token: Flow<String?> = libraries.getActiveLibraryFlow()
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else tokens.getTokenFlow(id) }

    override suspend fun save(token: String) {
        val libraryId = activeLibraryId()
        if (libraryId == null) {
            Log.w(TAG, "No library to save the token for")
            return
        }
        tokens.save(libraryId, token)
    }

    override suspend fun clear() {
        val libraryId = activeLibraryId() ?: return
        tokens.clear(libraryId)
    }

    private suspend fun activeLibraryId(): String? = libraries.getActiveLibraryFlow().first()?.id

    private companion object {
        const val TAG = "DefaultAuthTokenStore"
    }
}
