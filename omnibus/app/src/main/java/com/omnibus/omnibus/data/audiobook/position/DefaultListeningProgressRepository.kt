package com.omnibus.omnibus.data.audiobook.position

import android.util.Log
import com.omnibus.omnibus.data.audiobook.position.local.AudioPositionStore
import com.omnibus.omnibus.data.audiobook.position.remote.AudioProgressUpdateRequestDto
import com.omnibus.omnibus.data.audiobook.position.remote.ListeningSessionReportDto
import com.omnibus.omnibus.data.audiobook.position.remote.toJsonRequest
import com.omnibus.omnibus.data.bookprogress.remote.BookProgressRecordsDto
import com.omnibus.omnibus.data.library.remote.LibraryJson
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Default [ListeningProgressRepository]: the local store, the HTTP calls, and the outbox that sends pending
 * writes and queued sessions (I-SAVE-4, I-SESS-3).
 *
 * Network work runs on [applicationScope], so a caller's cancellation never abandons a request midway. What
 * is not sent stays in the store and goes out on the next flush. A flush runs when the device comes online,
 * when the repository is built online, and after every pushed save or queued session.
 */
@Singleton
class DefaultListeningProgressRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val store: AudioPositionStore,
    private val connectivity: ConnectivityMonitor,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ListeningProgressRepository {

    /** Keeps local writes in call order, so an older save never overwrites a newer one. */
    private val saveMutex = Mutex()

    /** One flush at a time, so a pending write is never sent twice in parallel. */
    private val flushMutex = Mutex()

    // Declared above init: property initializers and init blocks run in source order.
    init {
        applicationScope.launch {
            connectivity.isOnline.collect { online -> if (online) flush() }
        }
    }

    override suspend fun localCandidate(bookUuid: String): AudioPosition? {
        val cached = store.cached(bookUuid)
        val pending = store.pending(bookUuid)
        return if (pending != null && (cached == null || pending.clientUpdatedAt > cached.clientUpdatedAt)) {
            pending
        } else {
            cached
        }
    }

    override suspend fun serverPosition(bookUuid: String): ServerAudioPosition? = withContext(ioDispatcher) {
        if (bookUuid.isBlank()) return@withContext null
        val builder = omnibusHttpClient.request(PROGRESS_PATH) ?: return@withContext null
        val url = builder.build().url.newBuilder()
            .addPathSegment(bookUuid)
            .addQueryParameter("format", FORMAT_AUDIO)
            .build()
        val request = builder.url(url).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (!response.isSuccessful) return@use null
                val body = LibraryJson.decodeFromString<BookProgressRecordsDto?>(response.body.string())
                    ?: return@use null
                body.records
                    .filter { it.format == FORMAT_AUDIO && (it.audioPositionSeconds ?: 0.0) > 0.0 }
                    .maxByOrNull { ResumeRules.orderingClock(it.clientUpdatedAt, it.updatedAt) }
                    ?.let { record ->
                        ServerAudioPosition(
                            positionSeconds = record.audioPositionSeconds ?: 0.0,
                            bookFileId = record.bookFileId,
                            orderingClock = ResumeRules.orderingClock(record.clientUpdatedAt, record.updatedAt),
                        )
                    }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to read an audio position", ex)
            null
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to an audio position read", ex)
            null
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading an audio position", ex)
            null
        }
    }

    override suspend fun save(position: AudioPosition, push: Boolean) {
        saveMutex.withLock { store.save(position) }
        if (push) requestFlush()
    }

    override suspend fun queueSession(report: ListeningSessionReport) {
        saveMutex.withLock { store.queueSessions(listOf(report)) }
        requestFlush()
    }

    override fun requestFlush() {
        applicationScope.launch { flush() }
    }

    /**
     * Sends every pending write, then the queued sessions in batches of at most [SESSION_BATCH_CAP]. Does nothing
     * while offline. Each outcome is applied as soon as it is known, so a later failure keeps earlier successes.
     */
    internal suspend fun flush(): Unit = flushMutex.withLock {
        if (!connectivity.isOnline.value) return@withLock

        for (position in store.allPending()) {
            when (postPosition(position)) {
                Outcome.Sent -> store.clearPendingIf(position)
                Outcome.Drop -> {
                    Log.w(TAG, "Dropping a rejected audio position for ${position.bookUuid}")
                    store.clearPendingIf(position)
                }
                Outcome.Keep -> Unit
            }
        }

        for (batch in store.sessions().chunked(SESSION_BATCH_CAP)) {
            when (postSessions(batch)) {
                Outcome.Sent -> store.removeSessions(batch.mapTo(mutableSetOf()) { it.clientId })
                Outcome.Drop -> {
                    Log.w(TAG, "Dropping ${batch.size} rejected listening sessions")
                    store.removeSessions(batch.mapTo(mutableSetOf()) { it.clientId })
                }
                Outcome.Keep -> break
            }
        }
    }

    private suspend fun postPosition(position: AudioPosition): Outcome = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(PROGRESS_PATH) ?: return@withContext Outcome.Keep
        val request = builder.post(AudioProgressUpdateRequestDto.from(position).toJsonRequest()).build()

        try {
            omnibusHttpClient.execute(request).use { response -> classifyPosition(response.code) }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to push an audio position", ex)
            Outcome.Keep
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure pushing an audio position", ex)
            Outcome.Keep
        }
    }

    private suspend fun postSessions(batch: List<ListeningSessionReport>): Outcome = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(SESSIONS_PATH) ?: return@withContext Outcome.Keep
        val body = batch.map { ListeningSessionReportDto.from(it) }.toJsonRequest()
        val request = builder.post(body).build()

        try {
            omnibusHttpClient.execute(request).use { response -> classifySessions(response.code) }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to send listening sessions", ex)
            Outcome.Keep
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure sending listening sessions", ex)
            Outcome.Keep
        }
    }

    /** A position write: 2xx is sent, 400 to 499 except 401, 408 and 429 is dropped (I-SAVE-4). */
    private fun classifyPosition(code: Int): Outcome = when (code) {
        in 200..299 -> Outcome.Sent
        HTTP_UNAUTHORIZED, HTTP_REQUEST_TIMEOUT, HTTP_TOO_MANY_REQUESTS -> Outcome.Keep
        in 400..499 -> Outcome.Drop
        else -> Outcome.Keep
    }

    /** A session batch: 2xx is sent, 400 and 422 can never succeed so are dropped, anything else is kept (I-SESS-3). */
    private fun classifySessions(code: Int): Outcome = when (code) {
        in 200..299 -> Outcome.Sent
        HTTP_BAD_REQUEST, HTTP_UNPROCESSABLE -> Outcome.Drop
        else -> Outcome.Keep
    }

    /** What a POST meant for the outbox: remove it, remove it with a log, or leave it for the next flush. */
    private enum class Outcome { Sent, Drop, Keep }

    internal companion object {
        const val TAG = "ListeningProgressRepo"

        /** Relative, so they resolve beneath the server's base URL. */
        const val PROGRESS_PATH = "api/progress"
        const val SESSIONS_PATH = "api/progress/sessions"

        const val FORMAT_AUDIO = "audio"

        /** The server rejects a sessions POST larger than this with 422 (I-SESS-3). */
        const val SESSION_BATCH_CAP = 500

        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_UNPROCESSABLE = 422
    }
}
