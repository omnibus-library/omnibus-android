package com.omnibus.omnibus.data.libraries

import kotlinx.coroutines.CompletableDeferred

/**
 * A [LibraryRemoval] whose answers tests set by hand. When [repository] is given, [remove] also removes the library
 * from it, so a view model watching that repository sees it leave.
 */
class TestLibraryRemoval(private val repository: TestLibrariesRepository? = null) : LibraryRemoval {

    /** What [sync] returns when [syncGate] is null. */
    var syncResult: LibrarySyncResult = LibrarySyncResult.Synced

    /** When set, [sync] waits for it and returns its value instead of [syncResult]. */
    var syncGate: CompletableDeferred<LibrarySyncResult>? = null

    /** The ids passed to [sync], in call order. */
    val syncCalls = mutableListOf<String>()

    /** The ids passed to [remove], in call order. */
    val removeCalls = mutableListOf<String>()

    /** When set, [remove] waits for it before removing from [repository]. */
    var removeGate: CompletableDeferred<Unit>? = null

    override suspend fun sync(libraryId: String): LibrarySyncResult {
        syncCalls += libraryId
        return syncGate?.await() ?: syncResult
    }

    override suspend fun remove(libraryId: String) {
        removeCalls += libraryId
        removeGate?.await()
        repository?.remove(libraryId)
    }
}
