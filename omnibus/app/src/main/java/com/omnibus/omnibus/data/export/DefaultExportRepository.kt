package com.omnibus.omnibus.data.export

import android.util.Log
import com.omnibus.omnibus.data.export.local.ExportFileStore
import com.omnibus.omnibus.data.export.remote.ExportJson
import com.omnibus.omnibus.data.export.remote.ExportManifestDto
import com.omnibus.omnibus.data.export.remote.KindleSendQueuedDto
import com.omnibus.omnibus.data.export.remote.KindleSendRequestDto
import com.omnibus.omnibus.data.export.remote.KindleSendStatusDto
import com.omnibus.omnibus.data.export.remote.contentDispositionFilename
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.buffer
import okio.sink

/**
 * Default [ExportRepository]. Every failure becomes a result, so no exception leaves this class except
 * cancellation, which always propagates. Downloads stream to a fresh folder, which is deleted unless the
 * save succeeds.
 *
 * @param execute Runs one request. Production passes [OmnibusHttpClient.execute]; tests pass a client with
 *   shorter timeouts.
 */
class DefaultExportRepository internal constructor(
    private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val fileStore: ExportFileStore,
    private val timings: ExportTimings,
    private val execute: suspend (Request) -> Response,
) : ExportRepository {

    @Inject
    constructor(
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        omnibusHttpClient: OmnibusHttpClient,
        fileStore: ExportFileStore,
        timings: ExportTimings,
    ) : this(ioDispatcher, omnibusHttpClient, fileStore, timings, omnibusHttpClient::execute)

    override suspend fun sendToKindle(bookUuid: String): KindleSendResult = withContext(ioDispatcher) {
        when (val queued = queueSend(bookUuid)) {
            is Queued.Refused -> queued.result
            is Queued.Accepted -> awaitOutcome(queued.taskId)
        }
    }

    override suspend fun saveKepub(bookUuid: String, title: String): ExportSaveResult = withContext(ioDispatcher) {
        save(expectedBytes = 0) { folder ->
            val request = downloadRequest(EBOOKS_PATH, listOf(bookUuid, KEPUB)) ?: return@save ExportSaveResult.NoServer
            write(folder, fetchWithRetries(request), title, EPUB, part = null).toSaveResult()
        }
    }

    override suspend fun saveEbook(
        bookUuid: String,
        title: String,
        fallbackExtension: String,
        expectedBytes: Long,
    ): ExportSaveResult = withContext(ioDispatcher) {
        save(expectedBytes) { folder ->
            download(folder, EBOOKS_PATH, listOf(bookUuid, DOWNLOAD), null, title, fallbackExtension, null)
                .toSaveResult()
        }
    }

    override suspend fun saveAudiobook(
        bookUuid: String,
        title: String,
        fallbackExtension: String,
        expectedBytes: Long,
    ): ExportSaveResult = withContext(ioDispatcher) {
        save(expectedBytes) { folder ->
            val manifest = when (val read = readManifest(bookUuid)) {
                is Outcome.Ok -> read.value
                is Outcome.Refused -> return@save read.result
            }
            val files = mutableListOf<ExportedFile>()
            for (part in audiobookDownloads(manifest)) {
                val query = part.partOrdinal?.let { PART to it.toString() }
                when (val outcome = download(
                    folder,
                    AUDIOBOOKS_PATH,
                    listOf(bookUuid, DOWNLOAD),
                    query,
                    title,
                    fallbackExtension,
                    part.partNumber,
                )) {
                    is Outcome.Ok -> files += outcome.value
                    is Outcome.Refused -> return@save outcome.result
                }
            }
            ExportSaveResult.Saved(files)
        }
    }

    /** The outcome of queuing a send: the task to poll, or the answer that ended it. */
    private sealed interface Queued {
        data class Accepted(val taskId: Long) : Queued
        data class Refused(val result: KindleSendResult) : Queued
    }

    /** A step that either yields a value or ends the operation with a failure result. */
    private sealed interface Outcome<out T> {
        data class Ok<out T>(val value: T) : Outcome<T>
        data class Refused(val result: ExportSaveResult) : Outcome<Nothing>
    }

    /** `POST api/kindle/send`. Never retried, so a send can't be queued twice. */
    private suspend fun queueSend(bookUuid: String): Queued {
        val body: RequestBody = ExportJson.encodeToString(KindleSendRequestDto(bookUuid))
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = builderFor(KINDLE_SEND_PATH, emptyList())?.post(body)?.build()
            ?: return Queued.Refused(KindleSendResult.NoServer)

        return try {
            execute(request).use { response ->
                when (response.code) {
                    HTTP_OK -> Queued.Accepted(
                        ExportJson.decodeFromString<KindleSendQueuedDto>(response.body.string()).taskId,
                    )
                    HTTP_NOT_FOUND -> Queued.Refused(KindleSendResult.NotFound)
                    HTTP_CONFLICT -> Queued.Refused(KindleSendResult.NotConfigured)
                    HTTP_UNPROCESSABLE -> Queued.Refused(KindleSendResult.NoAddress)
                    else -> Queued.Refused(KindleSendResult.Failed)
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to queue a Kindle send", ex)
            Queued.Refused(KindleSendResult.Unreachable)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to a Kindle send", ex)
            Queued.Refused(KindleSendResult.Failed)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure queuing a Kindle send", ex)
            Queued.Refused(KindleSendResult.Failed)
        }
    }

    /** Polls the task every [ExportTimings.pollIntervalMillis] until it ends, or until the poll budget is spent. */
    private suspend fun awaitOutcome(taskId: Long): KindleSendResult {
        val status = builderFor(KINDLE_STATUS_PATH, emptyList(), TASK_ID to taskId.toString())?.build()
            ?: return KindleSendResult.NoServer
        // Counted rather than timed, so the budget is deterministic under test.
        val polls = ceil(timings.pollBudgetMillis / timings.pollIntervalMillis.toDouble()).toInt().coerceAtLeast(1)
        repeat(polls) {
            delay(timings.pollIntervalMillis)
            pollOnce(status)?.let { return it }
        }
        return KindleSendResult.Unconfirmed
    }

    /** One status poll. Null while the send is still pending. */
    private suspend fun pollOnce(status: Request): KindleSendResult? =
        try {
            execute(status).use { response ->
                when (response.code) {
                    HTTP_OK -> {
                        val dto = ExportJson.decodeFromString<KindleSendStatusDto>(response.body.string())
                        when (dto.status) {
                            STATUS_PENDING -> null
                            STATUS_SENT -> KindleSendResult.Sent
                            STATUS_FAILED -> KindleSendResult.Rejected(dto.message?.takeIf { it.isNotBlank() })
                            else -> KindleSendResult.Failed
                        }
                    }
                    // The server forgets a task ten seconds after it ends, so an unknown task is unconfirmed.
                    HTTP_NOT_FOUND -> KindleSendResult.Unconfirmed
                    else -> KindleSendResult.Failed
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for a Kindle send status", ex)
            KindleSendResult.Unconfirmed
        } catch (ex: IllegalArgumentException) {
            Log.w(TAG, "Unexpected Kindle send status response", ex)
            KindleSendResult.Failed
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure polling a Kindle send", ex)
            KindleSendResult.Failed
        }

    /**
     * Runs [body] in a fresh folder. Any result other than [ExportSaveResult.Saved], and any cancellation,
     * deletes the folder. A download that would not fit in the free space is refused before any request.
     */
    private suspend fun save(expectedBytes: Long, body: suspend (File) -> ExportSaveResult): ExportSaveResult {
        val folder = fileStore.newFolder()
        var result: ExportSaveResult = ExportSaveResult.Failed
        try {
            result = if (expectedBytes > 0 && expectedBytes + SPACE_MARGIN_BYTES > fileStore.usableBytes()) {
                ExportSaveResult.NoSpace
            } else {
                body(folder)
            }
            return result
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to download an export", ex)
            result = if (ex.message?.contains("ENOSPC") == true) ExportSaveResult.NoSpace else ExportSaveResult.Unreachable
            return result
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure saving an export", ex)
            result = ExportSaveResult.Failed
            return result
        } finally {
            if (result !is ExportSaveResult.Saved) {
                withContext(NonCancellable) { folder.deleteRecursively() }
            }
        }
    }

    /** One GET of a book file into [folder]. */
    private suspend fun download(
        folder: File,
        path: String,
        segments: List<String>,
        query: Pair<String, String>?,
        title: String,
        fallbackExtension: String,
        part: Int?,
    ): Outcome<ExportedFile> {
        val request = downloadRequest(path, segments, query) ?: return Outcome.Refused(ExportSaveResult.NoServer)
        return write(folder, execute(request), title, fallbackExtension, part)
    }

    /**
     * The KEPUB request. The server converts the book while the first request is open, which can outlast the
     * client's read timeout, so a timeout before the headers is retried up to [ExportTimings.kepubAttempts] times.
     */
    private suspend fun fetchWithRetries(request: Request): Response {
        var attempt = 1
        while (true) {
            try {
                return execute(request)
            } catch (ex: SocketTimeoutException) {
                if (attempt >= timings.kepubAttempts) throw ex
                Log.w(TAG, "KEPUB request timed out on attempt $attempt, retrying", ex)
                attempt++
            }
        }
    }

    /**
     * Streams a response into a file in [folder]. A non-2xx response becomes a failure and nothing is written.
     * The response is always closed.
     */
    private suspend fun write(
        folder: File,
        response: Response,
        title: String,
        fallbackExtension: String,
        part: Int?,
    ): Outcome<ExportedFile> = response.use { current ->
        if (!current.isSuccessful) return@use Outcome.Refused(failureOf(current.code))
        val name = ExportFileNames.fileName(
            title = title,
            suggested = contentDispositionFilename(current.header(CONTENT_DISPOSITION)),
            fallbackExtension = fallbackExtension,
            part = part,
        )
        val target = File(folder, name)
        current.body.source().use { source ->
            target.sink().buffer().use { sink ->
                while (true) {
                    // Blocking reads can't be interrupted, so cancellation is checked between chunks.
                    currentCoroutineContext().ensureActive()
                    if (source.read(sink.buffer, CHUNK_BYTES) == -1L) break
                    sink.emitCompleteSegments()
                }
            }
        }
        Outcome.Ok(ExportedFile(target, exportMimeType(current.header(CONTENT_TYPE), name)))
    }

    /** `GET api/audiobooks/{uuid}/manifest`, decoded. */
    private suspend fun readManifest(bookUuid: String): Outcome<ExportManifestDto> {
        val request = builderFor(AUDIOBOOKS_PATH, listOf(bookUuid, MANIFEST))?.build()
            ?: return Outcome.Refused(ExportSaveResult.NoServer)
        return execute(request).use { response ->
            if (!response.isSuccessful) return@use Outcome.Refused(failureOf(response.code))
            try {
                Outcome.Ok(ExportJson.decodeFromString<ExportManifestDto>(response.body.string()))
            } catch (ex: IllegalArgumentException) {
                Log.w(TAG, "Unreadable audiobook manifest", ex)
                Outcome.Refused(ExportSaveResult.Failed)
            }
        }
    }

    /** A download request for a book file, asking for any media type. Null when no server is saved. */
    private suspend fun downloadRequest(
        path: String,
        segments: List<String>,
        query: Pair<String, String>? = null,
    ): Request? = builderFor(path, segments, query)?.header(ACCEPT, ANY_MEDIA_TYPE)?.build()

    /**
     * A request builder for [path] with each of [pathSegments] appended (encoded, so a value can't add or
     * split segments) and an optional query parameter, or null when no server is saved.
     */
    private suspend fun builderFor(
        path: String,
        pathSegments: List<String>,
        query: Pair<String, String>? = null,
    ): Request.Builder? {
        val builder = omnibusHttpClient.request(path) ?: return null
        val url = builder.build().url.newBuilder().apply {
            pathSegments.forEach { addPathSegment(it) }
            if (query != null) addQueryParameter(query.first, query.second)
        }.build()
        return builder.url(url)
    }

    private fun failureOf(code: Int): ExportSaveResult = when (code) {
        HTTP_FORBIDDEN -> ExportSaveResult.Forbidden
        HTTP_NOT_FOUND -> ExportSaveResult.NotFound
        else -> ExportSaveResult.Failed
    }

    private fun Outcome<ExportedFile>.toSaveResult(): ExportSaveResult = when (this) {
        is Outcome.Ok -> ExportSaveResult.Saved(listOf(value))
        is Outcome.Refused -> result
    }

    internal companion object {
        const val TAG = "DefaultExportRepository"

        const val KINDLE_SEND_PATH = "api/kindle/send"
        const val KINDLE_STATUS_PATH = "api/kindle/send/status"
        const val EBOOKS_PATH = "api/ebooks"
        const val AUDIOBOOKS_PATH = "api/audiobooks"
        const val KEPUB = "kepub"
        const val DOWNLOAD = "download"
        const val MANIFEST = "manifest"
        const val TASK_ID = "task_id"
        const val PART = "part"

        const val STATUS_PENDING = "pending"
        const val STATUS_SENT = "sent"
        const val STATUS_FAILED = "failed"

        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_UNPROCESSABLE = 422

        const val ACCEPT = "Accept"
        const val ANY_MEDIA_TYPE = "*/*"
        const val CONTENT_TYPE = "Content-Type"
        const val CONTENT_DISPOSITION = "Content-Disposition"
        const val EPUB = "epub"

        /** Room kept free beyond the expected size, so a full cache doesn't fail at the last chunk. */
        const val SPACE_MARGIN_BYTES = 8L * 1024 * 1024
        const val CHUNK_BYTES = 64L * 1024

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
