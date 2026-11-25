package com.omnibus.omnibus.data.libraries

/**
 * A server this device keeps a library for (C1).
 *
 * @property id a UUID string, stable for the life of the library. Its per-book data, its token and its covers are keyed by it.
 * @property address the normalized server address.
 * @property name the name shown to the reader. The host (with its port when the address states one), see [LibraryHost].
 * @property username the signed-in account's name on this server, or null when it isn't known yet.
 * @property addedAt milliseconds since the Unix epoch when the library was added. It orders the list.
 * @property lastUsedAt milliseconds since the Unix epoch when the library was last used.
 * @property isActive whether this is the library the app reads from. At most one library is active.
 */
data class Library(
    val id: String,
    val address: String,
    val name: String,
    val username: String?,
    val addedAt: Long,
    val lastUsedAt: Long,
    val isActive: Boolean,
)
