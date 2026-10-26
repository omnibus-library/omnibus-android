package com.omnibus.omnibus.data.cover.local

import coil3.disk.DiskCache
import kotlinx.coroutines.CancellationException
import okio.ByteString
import okio.FileSystem
import okio.buffer
import javax.inject.Inject

/**
 * A cover thumbnail kept on disk.
 *
 * @property bytes The encoded image.
 * @property etag The validator the server sent with [bytes], or null if it sent none.
 * @property storedAtMillis When the bytes were last confirmed current: fetched, or answered 304.
 */
data class CachedCover(
    val bytes: ByteString,
    val etag: String?,
    val storedAtMillis: Long,
)

/**
 * The persistent tier of the cover cache. It survives process restarts and doubles as the offline
 * cover store.
 */
interface CoverDiskStore {
    /** The entry stored under [key], or null if there is none. */
    suspend fun read(key: String): CachedCover?

    /** Stores [cover] under [key], replacing any previous entry. */
    suspend fun write(key: String, cover: CachedCover)

    /** Removes the entry under [key], including its validator. */
    suspend fun remove(key: String)

    /** Removes every entry. */
    suspend fun clear()
}

/** A [CoverDiskStore] over a size-bounded, least-recently-used [DiskCache]. */
class DefaultCoverDiskStore @Inject constructor(
    private val diskCache: DiskCache,
) : CoverDiskStore {

    private val fileSystem: FileSystem get() = diskCache.fileSystem

    override suspend fun read(key: String): CachedCover? =
        try {
            diskCache.openSnapshot(key)?.use { snapshot ->
                val bytes = fileSystem.source(snapshot.data).buffer().use { it.readByteString() }
                val metadata = CoverMetadata.decode(
                    fileSystem.source(snapshot.metadata).buffer().use { it.readUtf8() },
                )
                CachedCover(bytes, metadata.etag, metadata.storedAtMillis)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }

    override suspend fun write(key: String, cover: CachedCover) {
        // Null when another write to this key is in flight; that one is as current as this one.
        val editor = diskCache.openEditor(key) ?: return
        try {
            fileSystem.write(editor.data) { write(cover.bytes) }
            fileSystem.write(editor.metadata) {
                writeUtf8(CoverMetadata.encode(CoverMetadata(cover.etag, cover.storedAtMillis)))
            }
            editor.commit()
        } catch (t: Throwable) {
            editor.abort()
            throw t
        }
    }

    override suspend fun remove(key: String) {
        diskCache.remove(key)
    }

    override suspend fun clear() {
        diskCache.clear()
    }
}

/** The sidecar stored next to a cached image: its validator and when it was confirmed current. */
internal data class CoverMetadata(val etag: String?, val storedAtMillis: Long) {
    companion object {

        /** `storedAtMillis` on the first line, the etag (if any) on the second. */
        fun encode(metadata: CoverMetadata): String =
            "${metadata.storedAtMillis}\n${metadata.etag.orEmpty()}"

        /**
         * Decodes [raw]. An unreadable sidecar yields a validator-less entry stored at the epoch,
         * so the image is re-fetched once instead of being trusted.
         */
        fun decode(raw: String): CoverMetadata {
            val lines = raw.split('\n', limit = 2)
            val storedAt = lines.firstOrNull()?.toLongOrNull() ?: 0L
            val etag = lines.getOrNull(1)?.takeIf { it.isNotEmpty() }
            return CoverMetadata(etag, storedAt)
        }
    }
}
