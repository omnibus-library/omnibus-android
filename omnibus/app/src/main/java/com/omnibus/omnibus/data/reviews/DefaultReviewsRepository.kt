package com.omnibus.omnibus.data.reviews

import android.util.Log
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.reviews.remote.BookReviewsDto
import com.omnibus.omnibus.data.reviews.remote.MyReviewDto
import com.omnibus.omnibus.data.reviews.remote.ReviewsJson
import com.omnibus.omnibus.data.reviews.remote.toJsonRequest
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Default [ReviewsRepository]. Keeps one hot state per book uuid, and every collector shares it. A fetch runs in the
 * application scope, so leaving the page cannot strand it, and concurrent requests for one uuid join one GET. Writes to
 * the reader's own review apply optimistically, run in the application scope, and roll back to the last server answer
 * on failure. A failure becomes a state or a result, so no exception leaves this class except cancellation.
 */
@Singleton
class DefaultReviewsRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val connectivityMonitor: ConnectivityMonitor,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ReviewsRepository {

    /** One book's state. Everything but [writeMutex] is guarded by [lock]. */
    private class Entry {
        val lock = Any()
        val state = MutableStateFlow<ReviewsState>(ReviewsState.Loading)

        /** Serialises the writes for this book, so they reach the server in call order. */
        val writeMutex = Mutex()

        /** The last server answer, which is what a failed write rolls back to. */
        var confirmed: ReviewsState = ReviewsState.Loading

        /** The write sequence that [confirmed] belongs to, so an older answer never replaces a newer one. */
        var confirmedSeq = 0L

        /** Bumped by every write that makes a request. */
        var writeSeq = 0L

        /** Writes whose optimistic value is not yet resolved. When this is zero, [state] equals [confirmed]. */
        var pendingWrites = 0

        /** The fetch in flight, which a new request joins instead of starting another GET. */
        var inFlight: Deferred<ReviewsState>? = null

        /** The [writeSeq] when [inFlight] started, so a save can tell a GET that began before it from one after. */
        var inFlightSeq = 0L
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    private fun entry(uuid: String): Entry = entries.computeIfAbsent(uuid) { Entry() }

    override fun getReviewsFlow(bookUuid: String): Flow<ReviewsState> {
        val entry = entry(bookUuid)
        return channelFlow {
            // isOnline is a StateFlow, so this fires once per offline-to-online change. Joining keeps it to one GET.
            launch {
                connectivityMonitor.isOnline.collect { online ->
                    if (online && entry.state.value == ReviewsState.Offline) startFetch(bookUuid, entry)
                }
            }
            entry.state.collect { send(it) }
        }.onStart {
            if (entry.state.value !is ReviewsState.Loaded) startFetch(bookUuid, entry)
        }
    }

    override suspend fun refresh(bookUuid: String) {
        startFetch(bookUuid, entry(bookUuid)).await()
    }

    override suspend fun updateMyReview(bookUuid: String, patch: ReviewPatch): ReviewWriteResult =
        write(bookUuid, patch) { mine -> mine.applying(patch) }

    override suspend fun deleteMyReview(bookUuid: String): ReviewWriteResult =
        write(bookUuid, patch = null) { null }

    /**
     * One optimistic write. [patch] null is a DELETE. The rules are checked in order: a blank uuid, then a state that is
     * not Loaded, then a patch that changes nothing.
     */
    private suspend fun write(uuid: String, patch: ReviewPatch?, optimistic: (Review?) -> Review?): ReviewWriteResult {
        if (uuid.isBlank()) return ReviewWriteResult.Failed(ReviewError.BookNotFound)
        val entry = entry(uuid)
        val seq = synchronized(entry.lock) {
            val current = entry.state.value as? ReviewsState.Loaded
                ?: return ReviewWriteResult.Failed(ReviewError.NotLoaded)
            if (patch != null && patch.isNoOpFor(current.reviews.mine)) return ReviewWriteResult.Unchanged
            entry.pendingWrites++
            entry.state.value = ReviewsState.Loaded(current.reviews.copy(mine = optimistic(current.reviews.mine)))
            ++entry.writeSeq
        }
        // UNDISPATCHED reaches the FIFO mutex before returning, so writes from one caller reach the server in call order.
        return applicationScope.async(start = CoroutineStart.UNDISPATCHED) {
            val result = entry.writeMutex.withLock { send(uuid, patch) }
            synchronized(entry.lock) {
                entry.pendingWrites--
                val confirmed = entry.confirmed
                if (result is ReviewWriteResult.Saved && seq >= entry.confirmedSeq && confirmed is ReviewsState.Loaded) {
                    entry.confirmed = ReviewsState.Loaded(confirmed.reviews.copy(mine = result.mine))
                    entry.confirmedSeq = seq
                }
                // Only the latest write sets the state: the server's answer on success, the confirmed one on failure.
                if (seq == entry.writeSeq) entry.state.value = entry.confirmed
            }
            if (result is ReviewWriteResult.Saved) refetchAfterWrite(uuid, entry, seq)
            result
        }.await()
    }

    /**
     * Refreshes summary and others after a save. A GET that began before the write is waited for, then one more runs.
     * A GET that began after the write is joined.
     */
    private fun refetchAfterWrite(uuid: String, entry: Entry, seq: Long) {
        val older = synchronized(entry.lock) {
            entry.inFlight?.takeIf { !it.isCompleted && entry.inFlightSeq < seq }
        }
        if (older == null) {
            startFetch(uuid, entry)
        } else {
            applicationScope.launch {
                older.join()
                startFetch(uuid, entry)
            }
        }
    }

    /** Starts a GET, or returns the one already in flight. It runs in the application scope, not the caller's. */
    private fun startFetch(uuid: String, entry: Entry): Deferred<ReviewsState> {
        val deferred = synchronized(entry.lock) {
            entry.inFlight?.takeIf { !it.isCompleted }?.let { return it }
            // A Loaded state stays on screen while it refetches. Any other state shows Loading until the answer.
            if (entry.state.value !is ReviewsState.Loaded) entry.state.value = ReviewsState.Loading
            val seqAtStart = entry.writeSeq
            entry.inFlightSeq = seqAtStart
            // The GET's own review is only trusted when no write was pending when it started and no write was
            // confirmed while it ran. Otherwise the server may have answered it before a write was applied, and it
            // would replace a newer answer with a stale one.
            val quietAtStart = entry.pendingWrites == 0
            val confirmedSeqAtStart = entry.confirmedSeq
            applicationScope.async(start = CoroutineStart.LAZY) {
                val outcome = get(uuid)
                synchronized(entry.lock) {
                    val confirmed = entry.confirmed
                    val trusted = quietAtStart && entry.confirmedSeq == confirmedSeqAtStart
                    when {
                        outcome is ReviewsState.Loaded && trusted -> {
                            entry.confirmed = outcome
                            entry.confirmedSeq = seqAtStart
                        }
                        // Began before a newer write was confirmed: keep that write's mine, take the household's part.
                        outcome is ReviewsState.Loaded && confirmed is ReviewsState.Loaded ->
                            entry.confirmed = ReviewsState.Loaded(outcome.reviews.copy(mine = confirmed.reviews.mine))
                        // A failed refetch never replaces Loaded; before anything loaded, the failure is the state.
                        confirmed !is ReviewsState.Loaded -> entry.confirmed = outcome
                    }
                    if (entry.pendingWrites == 0) entry.state.value = entry.confirmed
                }
                outcome
            }.also { entry.inFlight = it }
        }
        deferred.invokeOnCompletion {
            synchronized(entry.lock) {
                if (entry.inFlight === deferred) entry.inFlight = null
            }
        }
        deferred.start()
        return deferred
    }

    /** One GET of the book's reviews. A blank uuid or no saved server makes no request and fails. */
    private suspend fun get(uuid: String): ReviewsState = withContext(ioDispatcher) {
        if (uuid.isBlank()) return@withContext ReviewsState.Failed
        val builder = omnibusHttpClient.request(REVIEWS_PATH) ?: return@withContext ReviewsState.Failed
        val url = builder.build().url.newBuilder()
            .addPathSegment(uuid)
            .build()
        val request = builder.url(url).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (!response.isSuccessful) {
                    ReviewsState.Failed
                } else {
                    ReviewsState.Loaded(ReviewsJson.decodeFromString<BookReviewsDto>(response.body.string()).toDomain())
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to read reviews", ex)
            // A network error while the device reports no connection is "needs a connection"; otherwise it is a failure.
            if (connectivityMonitor.isOnline.value) ReviewsState.Failed else ReviewsState.Offline
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to a reviews read", ex)
            ReviewsState.Failed
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading reviews", ex)
            ReviewsState.Failed
        }
    }

    /**
     * One PATCH of [patch], or one DELETE when [patch] is null. A 2xx PATCH answers the review, or the JSON literal null
     * when the patch left nothing; a DELETE answers null.
     */
    private suspend fun send(uuid: String, patch: ReviewPatch?): ReviewWriteResult = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(REVIEWS_PATH)
            ?: return@withContext ReviewWriteResult.Failed(ReviewError.NoServer)
        val url = builder.build().url.newBuilder()
            .addPathSegment(uuid)
            .build()
        val request = if (patch == null) {
            builder.url(url).delete().build()
        } else {
            builder.url(url).patch(patch.toJsonRequest()).build()
        }

        try {
            omnibusHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful -> if (patch == null) {
                        ReviewWriteResult.Saved(null)
                    } else {
                        ReviewWriteResult.Saved(
                            ReviewsJson.decodeFromString<MyReviewDto?>(response.body.string())?.toDomain(),
                        )
                    }
                    response.code == HTTP_UNAUTHORIZED -> ReviewWriteResult.Failed(ReviewError.SessionExpired)
                    response.code == HTTP_NOT_FOUND -> ReviewWriteResult.Failed(ReviewError.BookNotFound)
                    else -> ReviewWriteResult.Failed(ReviewError.Http(response.code))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to write a review", ex)
            ReviewWriteResult.Failed(ReviewError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to a review write", ex)
            ReviewWriteResult.Failed(ReviewError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure writing a review", ex)
            ReviewWriteResult.Failed(ReviewError.UnexpectedResponse)
        }
    }

    internal companion object {
        const val TAG = "DefaultReviewsRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val REVIEWS_PATH = "api/reviews"

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}
