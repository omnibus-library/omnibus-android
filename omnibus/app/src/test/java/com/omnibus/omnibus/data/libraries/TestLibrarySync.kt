package com.omnibus.omnibus.data.libraries

import kotlinx.coroutines.CompletableDeferred

/** A [LibrarySync] whose answers tests set by hand. */
class TestLibrarySync : LibrarySync {

    /** What [hasUnsyncedChanges] returns. */
    var hasUnsynced = false

    /** What [syncActive] returns when [gate] is null. */
    var result: LibrarySyncResult = LibrarySyncResult.Synced

    /** When set, [syncActive] waits for it and returns its value instead of [result]. */
    var gate: CompletableDeferred<LibrarySyncResult>? = null

    /** How many times [syncActive] was called. */
    var syncCalls = 0

    override suspend fun hasUnsyncedChanges(): Boolean = hasUnsynced

    override suspend fun syncActive(): LibrarySyncResult {
        syncCalls++
        return gate?.await() ?: result
    }
}
