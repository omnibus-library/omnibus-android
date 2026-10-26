package com.omnibus.omnibus.data.cover.local

/** In-memory [CoverDiskStore] for tests. */
class FakeCoverDiskStore : CoverDiskStore {

    /** The stored entries by key. Tests seed and inspect this directly. */
    val entries = mutableMapOf<String, CachedCover>()

    /** When non-null, [write] throws this instead of storing. */
    var writeFailure: Throwable? = null

    /** When non-null, [read] throws this instead of returning an entry. */
    var readFailure: Throwable? = null

    /** When non-null, [remove] throws this instead of removing. */
    var removeFailure: Throwable? = null

    /** When non-null, [clear] throws this instead of clearing. */
    var clearFailure: Throwable? = null

    /** Every key [remove] was asked for, including the ones whose removal failed. */
    val removedKeys = mutableListOf<String>()

    override suspend fun read(key: String): CachedCover? {
        readFailure?.let { throw it }
        return entries[key]
    }

    override suspend fun write(key: String, cover: CachedCover) {
        writeFailure?.let { throw it }
        entries[key] = cover
    }

    override suspend fun remove(key: String) {
        removedKeys += key
        removeFailure?.let { throw it }
        entries.remove(key)
    }

    override suspend fun clear() {
        clearFailure?.let { throw it }
        entries.clear()
    }
}
