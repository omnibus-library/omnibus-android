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

    /** What [syncInactive] returns when [inactiveGate] is null. */
    var inactiveResult: LibrarySyncResult = LibrarySyncResult.Synced

    /** When set, [syncInactive] waits for it and returns its value instead of [inactiveResult]. */
    var inactiveGate: CompletableDeferred<LibrarySyncResult>? = null

    /** The ids passed to [syncInactive], in call order. */
    val inactiveSyncs = mutableListOf<String>()

    override suspend fun syncInactive(libraryId: String): LibrarySyncResult {
        inactiveSyncs += libraryId
        return inactiveGate?.await() ?: inactiveResult
    }
}
