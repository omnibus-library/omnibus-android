package com.omnibus.omnibus.data.libraries

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Sends the active library's pending changes before a switch leaves it (C10). */
interface LibrarySync {

    /** True while any participant has a change waiting to be sent. */
    suspend fun hasUnsyncedChanges(): Boolean

    /**
     * Sends every participant's pending changes and waits at most [SYNC_LIMIT]. [LibrarySyncResult.Synced] only when
     * every participant sent everything; any other outcome, or the limit passing, is [LibrarySyncResult.Failed].
     */
    suspend fun syncActive(): LibrarySyncResult
    // #373 appends: suspend fun syncInactive(libraryId: String): LibrarySyncResult
}

/** The outcome of [LibrarySync.syncActive]. */
sealed interface LibrarySyncResult {

    data object Synced : LibrarySyncResult

    data object Failed : LibrarySyncResult
}

/** How long a sync may take before a switch gives up on it. */
val SYNC_LIMIT: Duration = 10.seconds
