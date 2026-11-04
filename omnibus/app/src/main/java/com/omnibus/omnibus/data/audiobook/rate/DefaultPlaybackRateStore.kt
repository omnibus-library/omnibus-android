package com.omnibus.omnibus.data.audiobook.rate

import android.util.Log
import com.omnibus.omnibus.data.audiobook.playback.PlaybackRateStore
import com.omnibus.omnibus.data.audiobook.playback.clampRate
import com.omnibus.omnibus.data.audiobook.rate.local.PlaybackRateCache
import com.omnibus.omnibus.data.audiobook.rate.remote.PlaybackRateApi
import com.omnibus.omnibus.data.audiobook.rate.remote.PutOutcome
import com.omnibus.omnibus.data.audiobook.rate.remote.RateFetch
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** How long a rate read waits for the server before it answers from the cache (I-SPD-4, the resume deadline). */
internal const val RATE_READ_DEADLINE_MS = 1_200L

/**
 * The playback speed per book (C6, I-SPD-1, I-SPD-3, I-SPD-4). The local cache and the unsent local change are the
 * device's memory; the server is asked at each load and its answer refreshes the cache.
 *
 * The read order at load is: an unsent local change, then the server's answer within [RATE_READ_DEADLINE_MS], then the
 * cached rate, then null (the controller plays at 1.0). A server answer that arrives later only refreshes the cache.
 * A save writes the device first and then sends it on the application scope; a failed send keeps the rate pending.
 * Pending rates are sent again when the device comes back online, when their book next loads, and on the next change
 * (I-SPD-3). Nothing here throws except cancellation.
 */
@Singleton
class DefaultPlaybackRateStore @Inject constructor(
    private val api: PlaybackRateApi,
    private val cache: PlaybackRateCache,
    private val connectivity: ConnectivityMonitor,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : PlaybackRateStore {

    /** Keeps local writes and guarded cache refreshes in call order. */
    private val saveMutex = Mutex()

    /** One flush at a time, so a pending rate is never sent twice in parallel. */
    private val flushMutex = Mutex()

    /** Saves started per book, read under [saveMutex]; a read that saw an older count must not refresh the cache. */
    private val saveCounts = HashMap<String, Int>()

    // Declared after the mutexes: property initializers and init blocks run in source order.
    init {
        applicationScope.launch {
            connectivity.isOnline.collect { online -> if (online) flush() }
        }
    }

    override suspend fun rate(bookUuid: String): Double? {
        if (bookUuid.isBlank()) return null
        return try {
            readRate(bookUuid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unable to read a playback rate", e)
            null
        }
    }

    override suspend fun save(bookUuid: String, rate: Double) {
        if (bookUuid.isBlank()) return
        val clamped = clampRate(rate) ?: return
        try {
            saveMutex.withLock {
                saveCounts[bookUuid] = (saveCounts[bookUuid] ?: 0) + 1
                cache.saveLocal(bookUuid, clamped)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unable to save a playback rate", e)
        }
        requestFlush()
    }

    /** The read order of I-SPD-4 (see the class KDoc). Starts the server read on the application scope. */
    private suspend fun readRate(bookUuid: String): Double? {
        cache.pending(bookUuid)?.let { pending ->
            requestFlush()
            return clampRate(pending)
        }
        if (!connectivity.isOnline.value) return cache.cached(bookUuid)?.let(::clampRate)

        val savesAtStart = saveMutex.withLock { saveCounts[bookUuid] ?: 0 }
        // On the application scope: it outlives the deadline and the caller, and refreshes the cache when it lands.
        val fetch = applicationScope.async {
            val fetched = try {
                api.fetch(bookUuid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Unable to read a playback rate", e)
                RateFetch.Failed
            }
            refreshCache(bookUuid, fetched, savesAtStart)
            fetched
        }
        return when (val answer = withTimeoutOrNull(RATE_READ_DEADLINE_MS) { fetch.await() }) {
            is RateFetch.Found -> clampRate(answer.rate) ?: cache.cached(bookUuid)?.let(::clampRate)
            RateFetch.Unset -> null
            RateFetch.Failed, null -> cache.cached(bookUuid)?.let(::clampRate)
        }
    }

    /**
     * Stores a server answer as the cached rate, unless a save for this book started after the read did or a pending
     * change exists (I-SPD-4): a stale answer never overwrites a newer local change. Never throws.
     */
    private suspend fun refreshCache(bookUuid: String, fetched: RateFetch, savesAtStart: Int) {
        val value: Double? = when (fetched) {
            is RateFetch.Found -> clampRate(fetched.rate) ?: return
            RateFetch.Unset -> null
            RateFetch.Failed -> return
        }
        try {
            saveMutex.withLock {
                val noNewerSave = (saveCounts[bookUuid] ?: 0) == savesAtStart
                if (noNewerSave && cache.pending(bookUuid) == null) cache.storeServer(bookUuid, value)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Unable to cache a playback rate", e)
        }
    }

    private fun requestFlush() {
        applicationScope.launch { flush() }
    }

    /**
     * Sends every pending rate, one PUT per book with its current value, so a burst of changes goes out as its newest
     * value (I-SPD-3). Does nothing while offline. A sent or rejected rate is cleared only if it is still the pending one.
     */
    internal suspend fun flush() {
        try {
            flushMutex.withLock {
                if (!connectivity.isOnline.value) return@withLock
                for (bookUuid in cache.allPending().keys) {
                    val current = cache.pending(bookUuid) ?: continue
                    when (api.put(bookUuid, current)) {
                        PutOutcome.Sent -> cache.clearPendingIf(bookUuid, current)
                        PutOutcome.Drop -> {
                            Log.w(TAG, "Dropping a rejected playback rate for $bookUuid")
                            cache.clearPendingIf(bookUuid, current)
                        }
                        PutOutcome.Keep -> Unit
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected failure sending playback rates", e)
        }
    }

    private companion object {
        const val TAG = "PlaybackRateStore"
    }
}
