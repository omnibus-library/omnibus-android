package com.omnibus.omnibus.data.libraries.local

import coil3.disk.DiskCache
import com.omnibus.omnibus.data.cover.local.CoverDiskStore
import com.omnibus.omnibus.data.cover.local.CachedCover
import com.omnibus.omnibus.data.cover.local.DefaultCoverDiskStore
import com.omnibus.omnibus.data.libraries.ActiveLibrary
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toOkioPath

/**
 * The cover cache of the active library (C9). Each library's covers live in their own directory, and the cache of the
 * library that is active is opened on first use, never at injection.
 *
 * With no active library, reads miss and writes are dropped. When the active library changes, the next call shuts the
 * old cache down and opens the new library's directory. A write already in flight on the old cache can recreate the
 * old directory. That directory belongs to no library, and no library reuses its id, so it is accepted.
 */
@Singleton
class ActiveLibraryCoverDiskStore internal constructor(
    private val activeLibrary: ActiveLibrary,
    private val covers: LibraryCovers,
    private val openCache: (File) -> DiskCache,
) : CoverDiskStore {

    @Inject
    constructor(activeLibrary: ActiveLibrary, covers: LibraryCovers) : this(
        activeLibrary,
        covers,
        { directory ->
            DiskCache.Builder()
                .directory(directory.toOkioPath())
                .maxSizeBytes(LibraryCovers.MAX_BYTES)
                .build()
        },
    )

    private val mutex = Mutex()
    private var current: OpenCache? = null

    private class OpenCache(val libraryId: String, val cache: DiskCache, val store: DefaultCoverDiskStore)

    override suspend fun read(key: String): CachedCover? = store()?.read(key)

    override suspend fun write(key: String, cover: CachedCover) {
        store()?.write(key, cover)
    }

    override suspend fun remove(key: String) {
        store()?.remove(key)
    }

    override suspend fun clear() {
        store()?.clear()
    }

    /** The store of the active library, or null when there is none. */
    private suspend fun store(): DefaultCoverDiskStore? {
        val libraryId = activeLibrary.id() ?: return null
        return mutex.withLock {
            current?.takeIf { it.libraryId == libraryId }?.store ?: run {
                current?.cache?.shutdown()
                val cache = openCache(covers.directory(libraryId))
                val store = DefaultCoverDiskStore(cache)
                current = OpenCache(libraryId, cache, store)
                store
            }
        }
    }
}
