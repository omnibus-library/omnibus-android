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
     * Changes the address of library [libraryId] in place, renaming it after the new host. Slice 1 only; changing
     * server replaces the library once `remove` exists.
     */
    suspend fun changeAddress(libraryId: String, address: String)
}
