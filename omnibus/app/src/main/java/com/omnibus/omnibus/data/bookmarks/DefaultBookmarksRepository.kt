package com.omnibus.omnibus.data.bookmarks

import android.util.Log
import com.omnibus.omnibus.data.bookmarks.remote.BookmarkCreateRequestDto
import com.omnibus.omnibus.data.bookmarks.remote.BookmarkDto
import com.omnibus.omnibus.data.bookmarks.remote.BookmarksJson
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
 * Default [BookmarksRepository]. Keeps one hot state per book uuid. Each collection starts a GET, joining one in flight,
 * so a reader open always re-reads the server. Writes apply optimistically and run in the application scope, so leaving
 * the reader cannot cancel them. Writes for one book go to the server one at a time in call order. A failed fetch never
 * replaces a loaded list, and no exception leaves this class except cancellation.
 */
@Singleton
class DefaultBookmarksRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val timeSource: TimeSource,
    private val clientIdSource: ClientIdSource,
) : BookmarksRepository {

    /** A write that resolved while a fetch was in flight, so the fetch's answer can be corrected for it. */
    private sealed interface Resolved {
        val seq: Long

        data class Added(override val seq: Long, val bookmark: Bookmark) : Resolved
        data class Removed(override val seq: Long, val key: String) : Resolved
    }

    /** One book's state. Everything but [writeMutex] is guarded by [lock]. */
    private class Entry {
        val lock = Any()
        val state = MutableStateFlow<BookmarksState>(BookmarksState.Loading)

        /** Serialises the writes for this book, so they reach the server in call order. */
        val writeMutex = Mutex()

        /** The last server list, with resolved writes applied. Null until a fetch or a write has answered. */
        var server: List<Bookmark>? = null

        /** The last fetch failed and no list has ever loaded. */
        var failed = false

        /** Optimistic adds not yet answered, by client id. */
        val pendingAdds = LinkedHashMap<String, Bookmark>()

        /** Keys of bookmarks being removed, hidden until their DELETE answers. */
        val pendingRemovals = mutableSetOf<String>()

        /** The fetch in flight, which a new request joins instead of starting another GET. */
        var inFlightFetch: Deferred<Unit>? = null

        /** Bumped by every write that changes [server]. */
        var resolvedSeq = 0L

        /** The [resolvedSeq] when [inFlightFetch] started. */
        var fetchStartSeq = 0L

        /** Writes resolved while a fetch is in flight. Only appended then, so it stays short. */
        val journal = mutableListOf<Resolved>()
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    private fun entry(uuid: String): Entry = entries.computeIfAbsent(uuid) { Entry() }

    override fun getBookmarksFlow(bookUuid: String): Flow<BookmarksState> {
        val entry = entry(bookUuid)
        return entry.state.onStart { startFetch(bookUuid, entry) }
    }

    override suspend fun refresh(bookUuid: String) {
        startFetch(bookUuid, entry(bookUuid)).await()
    }

    /**
     * Starts a GET, or returns the one already in flight. The fetch's answer is merged with the writes that resolved while
     * it ran, so a stale list cannot undo them.
     */
    private fun startFetch(uuid: String, entry: Entry): Deferred<Unit> {
        val deferred = synchronized(entry.lock) {
            entry.inFlightFetch?.takeIf { !it.isCompleted }?.let { return it }
            // A retry after a failure shows progress. A loaded list stays on screen while it refreshes.
            if (entry.failed) {
                entry.failed = false
                publish(entry)
            }
            entry.fetchStartSeq = entry.resolvedSeq
            entry.journal.clear()
            applicationScope.async(start = CoroutineStart.LAZY) {
                val rows = get(uuid)
                synchronized(entry.lock) {
                    if (rows != null) {
                        var merged: List<Bookmark> = rows
                        for (resolved in entry.journal) {
                            if (resolved.seq <= entry.fetchStartSeq) continue
                            merged = when (resolved) {
                                is Resolved.Added ->
                                    if (merged.none { it.clientId == resolved.bookmark.clientId }) {
                                        merged + resolved.bookmark
                                    } else {
                                        merged
                                    }
                                is Resolved.Removed -> merged.filterNot { it.key == resolved.key }
                            }
                        }
                        entry.server = merged
                        entry.failed = false
                    } else if (entry.server == null) {
                        entry.failed = true
                    }
                    entry.journal.clear()
                    entry.inFlightFetch = null
                    publish(entry)
                }
            }.also { entry.inFlightFetch = it }
        }
        deferred.start()
        return deferred
    }

    override suspend fun addBookmark(draft: BookmarkDraft): BookmarkWriteResult {
        val entry = entry(draft.bookUuid)
        val clientId = clientIdSource.mint()
        val now = timeSource.nowMillis() / 1000
        val optimistic = Bookmark(
            id = null,
            clientId = clientId,
            bookUuid = draft.bookUuid,
            position = draft.position,
            title = draft.title,
            chapterTitle = null,
            fraction = null,
            createdAtSeconds = now,
        )
        synchronized(entry.lock) {
            entry.pendingAdds[clientId] = optimistic
            publish(entry)
        }
        // UNDISPATCHED reaches the FIFO mutex before returning, so writes from one caller reach the server in call order.
        return applicationScope.async(start = CoroutineStart.UNDISPATCHED) {
            val result = entry.writeMutex.withLock { sendCreate(draft, clientId, now) }
            synchronized(entry.lock) {
                entry.pendingAdds.remove(clientId)
                if (result is BookmarkWriteResult.Saved) {
                    val list = entry.server.orEmpty()
                    val index = list.indexOfFirst { it.clientId == clientId }
                    entry.server = if (index >= 0) {
                        list.toMutableList().also { it[index] = result.bookmark }
                    } else {
                        list + result.bookmark
                    }
                    entry.failed = false
                    entry.resolvedSeq++
                    if (entry.inFlightFetch != null) entry.journal += Resolved.Added(entry.resolvedSeq, result.bookmark)
                }
                publish(entry)
            }
            result
        }.await()
    }

    override suspend fun removeBookmark(bookmark: Bookmark): BookmarkWriteResult {
        if (bookmark.bookUuid.isBlank()) return BookmarkWriteResult.Failed(BookmarkError.BookNotFound)
        val key = bookmark.key
        val pathId = bookmark.pathId
        if (key == null || pathId == null) return BookmarkWriteResult.Failed(BookmarkError.UnexpectedResponse)
        val entry = entry(bookmark.bookUuid)
        synchronized(entry.lock) {
            // A second tap on a row whose removal is already going is not a new write.
            if (key in entry.pendingRemovals) return BookmarkWriteResult.Removed
            entry.pendingRemovals += key
            publish(entry)
        }
        return applicationScope.async(start = CoroutineStart.UNDISPATCHED) {
            val result = entry.writeMutex.withLock { sendDelete(pathId) }
            synchronized(entry.lock) {
                entry.pendingRemovals -= key
                if (result is BookmarkWriteResult.Removed) {
                    entry.server = entry.server?.filterNot { it.key == key }
                    entry.resolvedSeq++
                    if (entry.inFlightFetch != null) entry.journal += Resolved.Removed(entry.resolvedSeq, key)
                }
                // A failed removal only drops its pending key, which puts the row back where it was.
                publish(entry)
            }
            result
        }.await()
    }

    /**
     * Sets the state from the server list, the pending adds and the pending removals. Call under the entry's lock.
     * Loading or Failed show only while nothing has loaded and no add is waiting.
     */
    private fun publish(entry: Entry) {
        val server = entry.server
        entry.state.value = when {
            server != null || entry.pendingAdds.isNotEmpty() -> {
                val base = server.orEmpty()
                val adds = entry.pendingAdds.values.filter { add -> base.none { it.clientId == add.clientId } }
                BookmarksState.Loaded((base + adds).filter { it.key !in entry.pendingRemovals })
            }
            entry.failed -> BookmarksState.Failed
            else -> BookmarksState.Loading
        }
    }

    /** One GET of the book's bookmarks. Null for any failure, including a body that does not decode. */
    private suspend fun get(uuid: String): List<Bookmark>? = withContext(ioDispatcher) {
        if (uuid.isBlank()) return@withContext null
        val builder = omnibusHttpClient.request(BOOK_BOOKMARKS_PATH) ?: return@withContext null
        val url = builder.build().url.newBuilder()
            .addPathSegment(uuid)
            .build()
        val request = builder.url(url).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (!response.isSuccessful) {
                    null
                } else {
                    BookmarksJson.decodeFromString<List<BookmarkDto>>(response.body.string()).map { it.toDomain() }
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to read bookmarks", ex)
            null
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to a bookmarks read", ex)
            null
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading bookmarks", ex)
            null
        }
    }

    /** One POST of a create. A 2xx answer is the saved row; a 400, 401, 404 or other code is a typed failure. */
    private suspend fun sendCreate(draft: BookmarkDraft, clientId: String, createdAtSeconds: Long): BookmarkWriteResult =
        withContext(ioDispatcher) {
            val builder = omnibusHttpClient.request(BOOKMARKS_PATH)
                ?: return@withContext BookmarkWriteResult.Failed(BookmarkError.NoServer)
            val body = BookmarkCreateRequestDto(
                bookUuid = draft.bookUuid,
                position = draft.position,
                title = draft.title,
                clientId = clientId,
                clientCreatedAt = createdAtSeconds,
            ).toJsonRequest()
            val request = builder.post(body).build()

            try {
                omnibusHttpClient.execute(request).use { response ->
                    when {
                        response.isSuccessful -> BookmarkWriteResult.Saved(
                            BookmarksJson.decodeFromString<BookmarkDto>(response.body.string()).toDomain(),
                        )
                        response.code == HTTP_BAD_REQUEST -> BookmarkWriteResult.Failed(BookmarkError.Rejected)
                        response.code == HTTP_UNAUTHORIZED -> BookmarkWriteResult.Failed(BookmarkError.SessionExpired)
                        response.code == HTTP_NOT_FOUND -> BookmarkWriteResult.Failed(BookmarkError.BookNotFound)
                        else -> BookmarkWriteResult.Failed(BookmarkError.Http(response.code))
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: IOException) {
                Log.w(TAG, "Unable to reach the server to create a bookmark", ex)
                BookmarkWriteResult.Failed(BookmarkError.Unreachable)
            } catch (ex: IllegalArgumentException) {
                // SerializationException is a subtype, so a body that doesn't decode lands here too.
                Log.w(TAG, "Unexpected response to a bookmark create", ex)
                BookmarkWriteResult.Failed(BookmarkError.UnexpectedResponse)
            } catch (ex: Exception) {
                Log.e(TAG, "Unexpected failure creating a bookmark", ex)
                BookmarkWriteResult.Failed(BookmarkError.UnexpectedResponse)
            }
        }

    /** One DELETE by [pathId]. A 204 or a 404 both mean the bookmark is gone. */
    private suspend fun sendDelete(pathId: String): BookmarkWriteResult = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(BOOKMARKS_PATH)
            ?: return@withContext BookmarkWriteResult.Failed(BookmarkError.NoServer)
        val url = builder.build().url.newBuilder()
            .addPathSegment(pathId)
            .build()
        val request = builder.url(url).delete().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful || response.code == HTTP_NOT_FOUND -> BookmarkWriteResult.Removed
                    response.code == HTTP_BAD_REQUEST -> BookmarkWriteResult.Failed(BookmarkError.Rejected)
                    response.code == HTTP_UNAUTHORIZED -> BookmarkWriteResult.Failed(BookmarkError.SessionExpired)
                    else -> BookmarkWriteResult.Failed(BookmarkError.Http(response.code))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to delete a bookmark", ex)
            BookmarkWriteResult.Failed(BookmarkError.Unreachable)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure deleting a bookmark", ex)
            BookmarkWriteResult.Failed(BookmarkError.UnexpectedResponse)
        }
    }

    internal companion object {
        const val TAG = "DefaultBookmarksRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val BOOKMARKS_PATH = "api/bookmarks"

        const val BOOK_BOOKMARKS_PATH = "api/bookmarks/book"

        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}
