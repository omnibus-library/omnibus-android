package com.omnibus.omnibus.data.progress

import android.util.Log
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.progress.local.ReadingPositionStore
import com.omnibus.omnibus.data.progress.remote.BookProgressResponseDto
import com.omnibus.omnibus.data.progress.remote.ProgressJson
import com.omnibus.omnibus.data.progress.remote.ProgressUpdateRequestDto
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Default [ProgressRepository]. Every failure becomes a result, so no exception leaves this class,
 * except cancellation, which always propagates.
 */
class DefaultProgressRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val store: ReadingPositionStore,
) : ProgressRepository {

    /** Serializes write-then-push, so writes land in call order and an older one never overwrites a newer one. */
    private val mutex = Mutex()

    override suspend fun getLocalPosition(bookUuid: String): ReadingPosition? =
        store.getPositionFlow(bookUuid).first()

    override suspend fun savePosition(position: ReadingPosition, push: Boolean): PositionSaveResult =
        mutex.withLock {
            store.save(position)
            if (push) push(position) else PositionSaveResult.NotPushed
        }

    /**
     * One GET of the book's epub record. Not taken under [mutex]: a read never waits for a save, and
     * a save never waits for a read.
     */
    override suspend fun getServerPosition(bookUuid: String): ServerPositionResult = withContext(ioDispatcher) {
        if (bookUuid.isBlank()) return@withContext ServerPositionResult.None
        val builder = omnibusHttpClient.request(PROGRESS_PATH) ?: return@withContext ServerPositionResult.Error
        val url = builder.build().url.newBuilder()
            .addPathSegment(bookUuid)
            .addQueryParameter("format", FORMAT_EPUB)
            .build()
        val request = builder.url(url).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (!response.isSuccessful) {
                    ServerPositionResult.Error
                } else {
                    val body = ProgressJson.decodeFromString<BookProgressResponseDto?>(response.body.string())
                    val record = body?.records?.firstOrNull { it.format == FORMAT_EPUB }
                    when {
                        body == null -> ServerPositionResult.None
                        record == null -> ServerPositionResult.None
                        else -> ServerPositionResult.Found(record.toDomain())
                    }
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to read a position", ex)
            ServerPositionResult.Error
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to a position read", ex)
            ServerPositionResult.Error
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading a position", ex)
            ServerPositionResult.Error
        }
    }

    /** One POST of [position]. The response body is not read. */
    private suspend fun push(position: ReadingPosition): PositionSaveResult = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(PROGRESS_PATH) ?: return@withContext PositionSaveResult.NoServer
        val body = ProgressUpdateRequestDto(
            bookUuid = position.bookUuid,
            format = FORMAT_EPUB,
            epubCfi = position.cfi,
            progressPercent = position.percent,
            clientUpdatedAt = position.clientUpdatedAt,
        ).toJsonRequest()
        val request = builder.post(body).build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful -> PositionSaveResult.Pushed
                    response.code == HTTP_UNAUTHORIZED -> PositionSaveResult.Failed(ProgressError.SessionExpired)
                    else -> PositionSaveResult.Failed(ProgressError.Http(response.code))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to push a position", ex)
            PositionSaveResult.Failed(ProgressError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            Log.w(TAG, "Unexpected response to a position push", ex)
            PositionSaveResult.Failed(ProgressError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure pushing a position", ex)
            PositionSaveResult.Failed(ProgressError.UnexpectedResponse)
        }
    }

    internal companion object {
        const val TAG = "DefaultProgressRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val PROGRESS_PATH = "api/progress"

        const val FORMAT_EPUB = "epub"

        const val HTTP_UNAUTHORIZED = 401
    }
}
