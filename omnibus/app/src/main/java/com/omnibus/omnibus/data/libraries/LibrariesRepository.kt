package com.omnibus.omnibus.data.libraries

import kotlinx.coroutines.flow.Flow

/** The libraries this device keeps, and which one is active (C2). */
interface LibrariesRepository {

    /** Every library, in the order they were added. Emits again when one is added or changed. */
    fun getLibrariesFlow(): Flow<List<Library>>

    /** The active library, or null when none is. */
    fun getActiveLibraryFlow(): Flow<Library?>

    /**
     * Adds a library for [address], normalized, named by its host, with no username yet. When [activate] it is
     * the only active library. Returns null when it can't be stored.
     */
    suspend fun add(address: String, activate: Boolean): Library?

    /**
     * Removes library [libraryId] and all its data: the row goes, so its books and per-book rows cascade, then its
     * token is cleared and its covers are deleted. Local only. When the removed library was active, no library is
     * active afterwards. Covers of the removed library that are in flight may be written back once; that directory
     * belongs to no library and is never reused.
     */
    suspend fun remove(libraryId: String)

    // #366
    /**
     * Sets library [libraryId]'s username to [username], and nothing else. An unknown id changes nothing. Waits for
     * the storage upgrade; a storage failure is logged, not thrown.
     */
    suspend fun setUsername(libraryId: String, username: String)

    // #368
    /**
     * Stores [name] as library [libraryId]'s name, trimmed and cut to 40 characters (code points; a surrogate pair is
     * never split). A blank name is stored as "" and shows as the host. An unknown id changes nothing. Touches no
     * other column. Waits for the storage upgrade; a storage failure is logged, not thrown. Local only.
     */
    suspend fun rename(libraryId: String, name: String)

    // #369
    /**
     * Makes library [libraryId] the only active library and stamps its `lastUsedAt` with the clock. An unknown id
     * changes nothing. Waits for the storage upgrade; a storage failure is logged, not thrown. Does not change
     * [ActiveLibrary]: a switch restarts the process instead.
     */
    suspend fun setActive(libraryId: String)

    // #371
    /**
     * The saved library whose address is [address] once both are normalized, with the scheme and host compared without
     * regard to case and a default port ignored. Null when none matches or [address] doesn't normalize. Waits for the
     * storage upgrade, and gives null if it fails. A storage failure is logged, not thrown.
     */
    suspend fun findByAddress(address: String): Library?
}
