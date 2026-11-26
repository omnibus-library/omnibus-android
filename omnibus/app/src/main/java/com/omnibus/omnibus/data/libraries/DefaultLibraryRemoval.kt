package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.data.configuration.ServerAddress
import com.omnibus.omnibus.data.libraries.local.LibraryTokenStore
import com.omnibus.omnibus.data.libraries.remote.InactiveLibraryApi
import com.omnibus.omnibus.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** [LibraryRemoval] over the library store, its tokens and [InactiveLibraryApi]. */
class DefaultLibraryRemoval @Inject constructor(
    private val libraries: LibrariesRepository,
    private val tokens: LibraryTokenStore,
    private val activeLibrary: ActiveLibrary,
    private val sync: LibrarySync,
    private val api: InactiveLibraryApi,
    @param:ApplicationScope private val scope: CoroutineScope,
) : LibraryRemoval {

    override suspend fun sync(libraryId: String): LibrarySyncResult = sync.syncInactive(libraryId)

    override suspend fun remove(libraryId: String) {
        check(activeLibrary.id() != libraryId) { "The active library can't be removed in this process" }
        // Read before the library is deleted, since deleting it also clears its token.
        val library = libraries.getLibrariesFlow().first().firstOrNull { it.id == libraryId }
        val baseUrl = library?.let { ServerAddress.normalize(it.address)?.toHttpUrlOrNull() }
        val token = tokens.getTokenFlow(libraryId).first()?.takeIf { it.isNotBlank() }
        if (baseUrl != null && token != null) {
            // Started, not awaited: an unreachable server must not hold the removal (spec: ending the session MUST NOT block removal).
            scope.launch { api.logout(baseUrl, token) }
        }
        libraries.remove(libraryId)
    }
}
