package com.omnibus.omnibus.data.reviews

import android.util.Log
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.reviews.remote.BookReviewsDto
import com.omnibus.omnibus.data.reviews.remote.ReviewsJson
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
import kotlinx.coroutines.withContext

/**
 * Default [ReviewsRepository]. Keeps one hot state per book uuid, and every collector shares it. A fetch runs in the
 * application scope, so leaving the page cannot strand it, and concurrent requests for one uuid join one GET. A
 * failure becomes a state, so no exception leaves this class except cancellation.
 */
@Singleton
class DefaultReviewsRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val connectivityMonitor: ConnectivityMonitor,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ReviewsRepository {

    /** One book's state. Everything is guarded by [lock]. */
    private class Entry {
        val lock = Any()
        val state = MutableStateFlow<ReviewsState>(ReviewsState.Loading)

        /** The fetch in flight, which a new request joins instead of starting another GET. */
        var inFlight: Deferred<ReviewsState>? = null
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

    /** Starts a GET, or returns the one already in flight. It runs in the application scope, not the caller's. */
    private fun startFetch(uuid: String, entry: Entry): Deferred<ReviewsState> {
        val deferred = synchronized(entry.lock) {
            entry.inFlight?.takeIf { !it.isCompleted }?.let { return it }
            // A Loaded state stays on screen while it refetches. Any other state shows Loading until the answer.
            if (entry.state.value !is ReviewsState.Loaded) entry.state.value = ReviewsState.Loading
            applicationScope.async(start = CoroutineStart.LAZY) {
                val outcome = get(uuid)
                synchronized(entry.lock) {
                    // A failed refetch never replaces Loaded.
                    if (outcome is ReviewsState.Loaded || entry.state.value !is ReviewsState.Loaded) {
                        entry.state.value = outcome
                    }
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

    internal companion object {
        const val TAG = "DefaultReviewsRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val REVIEWS_PATH = "api/reviews"
    }
}
