package com.omnibus.omnibus.data.cover.local

/** In-memory [CoverDiskStore] for tests. */
class FakeCoverDiskStore : CoverDiskStore {

    /** The stored entries by key. Tests seed and inspect this directly. */
    val entries = mutableMapOf<String, CachedCover>()

    /** When non-null, [write] throws this instead of storing. */
    var writeFailure: Throwable? = null

    /** When non-null, [read] throws this instead of returning an entry. */
    var readFailure: Throwable? = null

    override suspend fun read(key: String): CachedCover? {
        readFailure?.let { throw it }
        return entries[key]
    }

    override suspend fun write(key: String, cover: CachedCover) {
        writeFailure?.let { throw it }
        entries[key] = cover
    }

    override suspend fun remove(key: String) {
        entries.remove(key)
    }

    override suspend fun clear() {
        entries.clear()
    }
}
