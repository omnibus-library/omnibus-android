package com.omnibus.omnibus.data.highlights

import android.util.Log
import com.omnibus.omnibus.data.highlights.remote.HighlightCreateRequestDto
import com.omnibus.omnibus.data.highlights.remote.HighlightDto
import com.omnibus.omnibus.data.highlights.remote.HighlightsJson
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.ClientIdSource
import com.omnibus.omnibus.di.IoDispatcher
import com.omnibus.omnibus.di.TimeSource
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Default [HighlightsRepository]. Keeps one hot state per book uuid. Fetches, creates and deletes run in the application
 * scope, so leaving the page cannot cancel them. Writes for one book go to the server one at a time in call order. A failed
 * fetch never replaces a loaded list. Every failure becomes a state or a typed result, so no exception leaves this class
 * except cancellation.
 */
@Singleton
class DefaultHighlightsRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val timeSource: TimeSource,
    private val clientIdSource: ClientIdSource,
) : HighlightsRepository {

    /** One book's state. Everything but [writeMutex] is guarded by [lock]. */
    private class Entry {
        val lock = Any()
        val state = MutableStateFlow<HighlightsState>(HighlightsState.Loading)
        var inFlightFetch: Deferred<HighlightsState>? = null

        /** Serialises the writes (creates and deletes) for this book, so they reach the server in call order. */
        val writeMutex = Mutex()

        /** Rows saved while a fetch is in flight, so the fetch's answer can be corrected for them. */
        val journal = mutableListOf<Highlight>()

        /** Ids deleted while a fetch is in flight, so the fetch's answer can drop them. */
        val deletedIds = mutableSetOf<Long>()
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    private fun entry(uuid: String): Entry = entries.computeIfAbsent(uuid) { Entry() }

    override fun getHighlightsFlow(bookUuid: String): Flow<HighlightsState> {
        val entry = entry(bookUuid)
        return entry.state.onStart {
            if (entry.state.value !is HighlightsState.Loaded) startFetch(bookUuid, entry)
        }
    }

    override suspend fun refresh(bookUuid: String) {
        startFetch(bookUuid, entry(bookUuid)).await()
    }

    override suspend fun addHighlight(draft: HighlightDraft): HighlightWriteResult {
        val entry = entry(draft.bookUuid)
        val clientId = clientIdSource.mint()
        val createdAtSeconds = timeSource.nowMillis() / 1000
        // UNDISPATCHED reaches the FIFO mutex before returning, so creates reach the server in call order.
        return applicationScope.async(start = CoroutineStart.UNDISPATCHED) {
            val result = entry.writeMutex.withLock { sendCreate(draft, clientId, createdAtSeconds) }
            if (result is HighlightWriteResult.Saved) {
                synchronized(entry.lock) {
                    val row = result.highlight
                    val loaded = entry.state.value as? HighlightsState.Loaded
                    if (loaded != null && loaded.highlights.none { it.id == row.id }) {
                        entry.state.value = HighlightsState.Loaded(loaded.highlights + row)
                    }
                    if (entry.inFlightFetch != null) entry.journal += row
                }
            }
            result
        }.await()
    }

    override suspend fun deleteHighlight(bookUuid: String, id: Long): HighlightDeleteResult {
        val entry = entry(bookUuid)
        // UNDISPATCHED reaches the FIFO mutex before returning, so writes reach the server in call order.
        return applicationScope.async(start = CoroutineStart.UNDISPATCHED) {
            val result = entry.writeMutex.withLock { sendDelete(id) }
            if (result is HighlightDeleteResult.Deleted) {
                synchronized(entry.lock) {
                    val loaded = entry.state.value as? HighlightsState.Loaded
                    if (loaded != null && loaded.highlights.any { it.id == id }) {
                        entry.state.value = HighlightsState.Loaded(loaded.highlights.filterNot { it.id == id })
                    }
                    entry.journal.removeAll { it.id == id }
                    if (entry.inFlightFetch != null) entry.deletedIds += id
                }
            }
            result
        }.await()
    }

    /** One POST of a create. A 2xx answer is the saved row; a 400, 401, 404 or other code is a typed failure. */
    private suspend fun sendCreate(draft: HighlightDraft, clientId: String, createdAtSeconds: Long): HighlightWriteResult =
        withContext(ioDispatcher) {
            val builder = omnibusHttpClient.request(HIGHLIGHTS_CREATE_PATH)
                ?: return@withContext HighlightWriteResult.Failed(HighlightError.NoServer)
            val body = HighlightCreateRequestDto(
                bookUuid = draft.bookUuid,
                epubCfiRange = draft.cfiRange,
                color = draft.color.token,
                text = draft.text,
                clientId = clientId,
                clientCreatedAt = createdAtSeconds,
            ).toJsonRequest()
            val request = builder.post(body).build()

            try {
                omnibusHttpClient.execute(request).use { response ->
                    when {
                        response.isSuccessful -> HighlightWriteResult.Saved(
                            HighlightsJson.decodeFromString<HighlightDto>(response.body.string()).toDomain(),
                        )
                        response.code == HTTP_BAD_REQUEST -> HighlightWriteResult.Failed(HighlightError.Rejected)
                        response.code == HTTP_UNAUTHORIZED -> HighlightWriteResult.Failed(HighlightError.SessionExpired)
                        response.code == HTTP_NOT_FOUND -> HighlightWriteResult.Failed(HighlightError.BookNotFound)
                        else -> HighlightWriteResult.Failed(HighlightError.Http(response.code))
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: IOException) {
                Log.w(TAG, "Unable to reach the server to create a highlight", ex)
                HighlightWriteResult.Failed(HighlightError.Unreachable)
            } catch (ex: IllegalArgumentException) {
                // SerializationException is a subtype, so a body that doesn't decode lands here too.
                Log.w(TAG, "Unexpected response to a highlight create", ex)
                HighlightWriteResult.Failed(HighlightError.UnexpectedResponse)
            } catch (ex: Exception) {
                Log.e(TAG, "Unexpected failure creating a highlight", ex)
                HighlightWriteResult.Failed(HighlightError.UnexpectedResponse)
            }
        }

    /** One DELETE of the highlight with server [id]. A 2xx or 404 is deleted; a 401 or other code is a typed failure. */
    private suspend fun sendDelete(id: Long): HighlightDeleteResult = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(HIGHLIGHTS_CREATE_PATH)
            ?: return@withContext HighlightDeleteResult.Failed(HighlightError.NoServer)
        val url = builder.build().url.newBuilder()
            .addPathSegment(id.toString())
            .build()
        val request = builder.url(url).delete().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful || response.code == HTTP_NOT_FOUND -> HighlightDeleteResult.Deleted
                    response.code == HTTP_UNAUTHORIZED -> HighlightDeleteResult.Failed(HighlightError.SessionExpired)
                    else -> HighlightDeleteResult.Failed(HighlightError.Http(response.code))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to delete a highlight", ex)
            HighlightDeleteResult.Failed(HighlightError.Unreachable)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure deleting a highlight", ex)
            HighlightDeleteResult.Failed(HighlightError.UnexpectedResponse)
        }
    }

    /**
     * Starts a GET, or returns the one already in flight. It runs in the application scope, not the caller's. The in-flight
     * slot is cleared in the same lock as the state write, so a caller that sees Failed always starts a new fetch.
     */
    private fun startFetch(uuid: String, entry: Entry): Deferred<HighlightsState> {
        val deferred = synchronized(entry.lock) {
            entry.inFlightFetch?.takeIf { !it.isCompleted }?.let { return it }
            entry.journal.clear()
            entry.deletedIds.clear()
            applicationScope.async(start = CoroutineStart.LAZY) {
                // A retry after a failure shows progress; a loaded list stays on screen while it refreshes.
                synchronized(entry.lock) {
                    if (entry.state.value is HighlightsState.Failed) entry.state.value = HighlightsState.Loading
                }
                val fetched = get(uuid)
                synchronized(entry.lock) {
                    val outcome = if (fetched is HighlightsState.Loaded) {
                        // A row saved while this GET ran may be missing from its answer, and a row deleted while it ran
                        // may still be in it, so both are corrected.
                        val ids = fetched.highlights.mapTo(HashSet()) { it.id }
                        HighlightsState.Loaded(
                            (fetched.highlights + entry.journal.filter { it.id !in ids })
                                .filterNot { it.id in entry.deletedIds },
                        )
                    } else {
                        fetched
                    }
                    if (outcome is HighlightsState.Loaded) {
                        entry.state.value = outcome
                    } else if (entry.state.value !is HighlightsState.Loaded) {
                        entry.state.value = HighlightsState.Failed
                    }
                    entry.journal.clear()
                    entry.deletedIds.clear()
                    entry.inFlightFetch = null
                    outcome
                }
            }.also { entry.inFlightFetch = it }
        }
        deferred.start()
        return deferred
    }

    /** One GET of the book's highlights. Any failure, including a body that does not decode, reads as Failed. */
    private suspend fun get(uuid: String): HighlightsState = withContext(ioDispatcher) {
        if (uuid.isBlank()) return@withContext HighlightsState.Failed
        val builder = omnibusHttpClient.request(HIGHLIGHTS_PATH) ?: return@withContext HighlightsState.Failed
        val url = builder.build().url.newBuilder()
            .addPathSegment(uuid)
            .build()
        val request = builder.url(url).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (!response.isSuccessful) {
                    HighlightsState.Failed
                } else {
                    val rows = HighlightsJson.decodeFromString<List<HighlightDto>>(response.body.string())
                    HighlightsState.Loaded(rows.map { it.toDomain() })
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to read highlights", ex)
            HighlightsState.Failed
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to a highlights read", ex)
            HighlightsState.Failed
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading highlights", ex)
            HighlightsState.Failed
        }
    }

    internal companion object {
        const val TAG = "DefaultHighlightsRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val HIGHLIGHTS_PATH = "api/highlights/book"

        /** Relative, so it resolves beneath the server's base URL. A create POSTs here; a delete appends the id. */
        const val HIGHLIGHTS_CREATE_PATH = "api/highlights"

        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}
