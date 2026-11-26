package com.omnibus.omnibus.data.libraries

/** Removes a library from this device (C16). Used by the detail screen (#373) and the switching screen (#374). */
interface LibraryRemoval {

    /** Sends [libraryId]'s pending changes to its server first; see [LibrarySync.syncInactive]. */
    suspend fun sync(libraryId: String): LibrarySyncResult

    /**
     * Ends [libraryId]'s session on its server and deletes the library and its data. Ending the session is started,
     * not awaited, and its failure never stops the removal. Must not be called for the library active in this process.
     */
    suspend fun remove(libraryId: String)
}
