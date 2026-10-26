package com.omnibus.omnibus.data.cover

import android.util.Log
import coil3.memory.MemoryCache
import com.omnibus.omnibus.data.cover.local.CachedCover
import com.omnibus.omnibus.data.cover.local.CoverDiskStore
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import com.omnibus.omnibus.di.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/** Loads book cover thumbnails through a memory + disk cache and keeps that cache honest. */
interface CoverRepository {

    /**
     * Loads the [uuid] book's thumbnail at [size], falling back to the other sizes (largest first)
     * when the server doesn't have it. Cached copies are served when the server can't be reached.
     */
    suspend fun thumbnail(uuid: String, size: CoverSize): CoverImageResult

    /**
     * Drops the [uuid] book's thumbnails at every size from both cache tiers. Call this after a
     * cover is uploaded or reverted, since its URLs stay the same while the bytes behind them don't.
     */
    suspend fun invalidate(uuid: String)

    /** Drops every cached thumbnail. */
    suspend fun clear()
}

/** The outcome of [CoverRepository.thumbnail]. */
sealed interface CoverImageResult {

    /**
     * An encoded image.
     *
     * @property size The size actually served, which differs from the one requested after a fall-back.
     * @property fromCache True when the bytes came from the disk cache rather than the server.
     */
    data class Found(
        val image: ByteString,
        val size: CoverSize,
        val fromCache: Boolean,
    ) : CoverImageResult

    /** The server has no thumbnail for this book at any size. */
    data object NotFound : CoverImageResult

    /** The server couldn't be reached or answered unexpectedly, and nothing was cached. */
    data object Error : CoverImageResult
}

/**
 * Default [CoverRepository]: revalidates cached covers against the server and serves cached copies
 * when the server can't answer.
 */
class DefaultCoverRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val diskStore: CoverDiskStore,
    private val memoryCache: MemoryCache,
    private val timeSource: TimeSource,
) : CoverRepository {

    /** How many times each disk key has been invalidated; a fetch refuses to store across a change. */
    private val generations = ConcurrentHashMap<String, AtomicInteger>()
    private val globalGeneration = AtomicInteger()

    /**
     * Held while a fetch checks its generation and writes, and while an invalidation bumps the
     * generation and removes entries. Without it, an invalidation could land between a fetch's
     * check and its write, and the superseded bytes would be stored after the removal.
     */
    private val cacheLock = Mutex()

    override suspend fun thumbnail(uuid: String, size: CoverSize): CoverImageResult =
        withContext(ioDispatcher) {
            val candidates = listOf(size) + size.fallbacks
            for ((index, candidate) in candidates.withIndex()) {
                when (val attempt = load(uuid, candidate)) {
                    is Attempt.Found -> return@withContext attempt.result
                    Attempt.Missing -> continue
                    Attempt.Failed -> {
                        // The server isn't answering, so asking it about the other sizes would only
                        // wait out more failures. A smaller copy of the right cover still beats none.
                        return@withContext candidates.drop(index + 1).firstNotNullOfOrNull { other ->
                            cached(uuid, other)?.let { found(it, other) }
                        } ?: CoverImageResult.Error
                    }
                }
            }
            CoverImageResult.NotFound
        }

    override suspend fun invalidate(uuid: String) {
        val diskKeys = CoverSize.entries.map { CoverCacheKeys.diskKey(uuid, it) }
        withContext(ioDispatcher) {
            cacheLock.withLock {
                // Bumped before the removals, so a fetch that finishes after them is refused.
                diskKeys.forEach { generations.computeIfAbsent(it) { AtomicInteger() }.incrementAndGet() }
                diskKeys.forEach { diskStore.remove(it) }
            }
        }
        memoryCache.keys
            .filter { CoverCacheKeys.diskKeyOfMemoryKey(it.key) in diskKeys }
            .forEach { memoryCache.remove(it) }
    }

    override suspend fun clear() {
        withContext(ioDispatcher) {
            cacheLock.withLock {
                globalGeneration.incrementAndGet()
                diskStore.clear()
            }
        }
        memoryCache.clear()
    }

    private fun found(cover: CachedCover, size: CoverSize) =
        CoverImageResult.Found(cover.bytes, size, fromCache = true)

    private fun currentGeneration(key: String): Int =
        globalGeneration.get() + (generations[key]?.get() ?: 0)

    /**
     * Runs a disk-cache operation. A failure is logged and reported as null, so the cache never
     * fails a load; cancellation still propagates.
     */
    private inline fun <T> diskOrNull(description: String, block: () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Couldn't $description", t)
            null
        }

    private suspend fun cached(uuid: String, size: CoverSize): CachedCover? =
        diskOrNull("read cached cover") { diskStore.read(CoverCacheKeys.diskKey(uuid, size)) }

    private suspend fun load(uuid: String, size: CoverSize): Attempt {
        val path = size.path(uuid)
        val key = CoverCacheKeys.diskKey(path)
        val cached = cached(uuid, size)
        val now = timeSource.nowMillis()
        val plan = if (cached == null) {
            RevalidationPlan.Unconditional
        } else {
            RevalidationPlan.of(now - cached.storedAtMillis, cached.etag)
        }
        if (cached != null && plan == RevalidationPlan.Skip) {
            return Attempt.Found(found(cached, size))
        }
        // Whatever the cache holds is better than nothing when the server can't answer.
        val staleOrFailed = if (cached != null) Attempt.Found(found(cached, size)) else Attempt.Failed

        val request = omnibusHttpClient.request(path)?.apply {
            header("Accept", ACCEPT)
            if (plan is RevalidationPlan.Conditional) header("If-None-Match", plan.etag)
        }?.build() ?: return staleOrFailed

        // Captured before the request: an invalidation during it makes these bytes the cover that
        // was just replaced, and storing them would undo it.
        val generation = currentGeneration(key)
        return try {
            omnibusHttpClient.execute(request).use { response ->
                when {
                    response.code == 304 && cached != null -> {
                        store(key, generation, cached.copy(etag = response.header("ETag") ?: cached.etag, storedAtMillis = now))
                        Attempt.Found(found(cached, size))
                    }
                    response.isSuccessful -> {
                        val body = response.body.bytes().toByteString()
                        val etag = response.header("ETag")
                        store(key, generation, CachedCover(body, etag, now))
                        Attempt.Found(CoverImageResult.Found(body, size, fromCache = false))
                    }
                    response.code == 404 -> {
                        if (cached != null) diskOrNull("remove cached cover") { diskStore.remove(key) }
                        Attempt.Missing
                    }
                    else -> staleOrFailed
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't reach the server for $path", e)
            staleOrFailed
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected failure loading $path", t)
            staleOrFailed
        }
    }

    /**
     * Writes [cover] unless the key was invalidated since [generation] was taken. A refused write is
     * not an error: the bytes are still returned to the caller, they just aren't kept.
     */
    private suspend fun store(key: String, generation: Int, cover: CachedCover) {
        cacheLock.withLock {
            if (generation != currentGeneration(key)) return
            // The cover is still good for this draw; it just won't be there next time.
            diskOrNull("cache cover") { diskStore.write(key, cover) }
        }
    }

    private sealed interface Attempt {
        data class Found(val result: CoverImageResult.Found) : Attempt
        data object Missing : Attempt
        data object Failed : Attempt
    }

    private companion object {
        const val TAG = "CoverRepository"
        const val ACCEPT = "image/webp,image/*;q=0.8"
    }
}
