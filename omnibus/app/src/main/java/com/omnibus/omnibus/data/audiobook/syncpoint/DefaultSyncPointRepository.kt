package com.omnibus.omnibus.data.audiobook.syncpoint

import android.util.Log
import com.omnibus.omnibus.data.audiobook.syncpoint.remote.AudioSyncPointRequestDto
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Default [SyncPointRepository]: one POST per declaration, with no retry and no store (I-BMK-3).
 *
 * The request runs on [applicationScope], so closing the player cannot abandon a POST the server may already be
 * applying. The caller's cancellation still cancels its wait.
 */
@Singleton
class DefaultSyncPointRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : SyncPointRepository {

    override suspend fun declareAudioSyncPoint(
        bookUuid: String,
        bookFileId: Long,
        audioSeconds: Double,
    ): SyncPointResult {
        if (bookUuid.isBlank()) return SyncPointResult.Failed(SyncPointError.BookNotFound)
        return applicationScope.async { post(bookUuid, bookFileId, audioSeconds) }.await()
    }

    /** One POST. The body is never read: a 2xx answer is only a status. */
    private suspend fun post(bookUuid: String, bookFileId: Long, audioSeconds: Double): SyncPointResult =
        withContext(ioDispatcher) {
            val builder = omnibusHttpClient.request(BOOKS_PATH)
                ?: return@withContext SyncPointResult.Failed(SyncPointError.NoServer)
            val url = builder.build().url.newBuilder()
                .addPathSegment(bookUuid)
                .addPathSegment(SYNC_POINT_SEGMENT)
                .build()
            val body = AudioSyncPointRequestDto(
                bookUuid = bookUuid,
                audioBookFileId = bookFileId,
                audioSeconds = audioSeconds,
            ).toJsonRequest()
            val request = builder.url(url).post(body).build()

            try {
                omnibusHttpClient.execute(request).use { response -> classify(response.code) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: IOException) {
                Log.w(TAG, "Unable to reach the server to declare a sync point", ex)
                SyncPointResult.Failed(SyncPointError.Unreachable)
            } catch (ex: Exception) {
                Log.e(TAG, "Unexpected failure declaring a sync point", ex)
                SyncPointResult.Failed(SyncPointError.UnexpectedResponse)
            }
        }

    /** Maps a status code to the result. Only 2xx is declared; no code is retried. */
    private fun classify(code: Int): SyncPointResult = when (code) {
        in 200..299 -> SyncPointResult.Declared
        HTTP_BAD_REQUEST, HTTP_UNPROCESSABLE -> SyncPointResult.Failed(SyncPointError.Rejected)
        HTTP_UNAUTHORIZED -> SyncPointResult.Failed(SyncPointError.SessionExpired)
        HTTP_NOT_FOUND -> SyncPointResult.Failed(SyncPointError.BookNotFound)
        HTTP_CONFLICT -> SyncPointResult.Failed(SyncPointError.Conflict)
        else -> SyncPointResult.Failed(SyncPointError.Http(code))
    }

    internal companion object {
        const val TAG = "DefaultSyncPointRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val BOOKS_PATH = "api/books"

        const val SYNC_POINT_SEGMENT = "sync-point"

        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_CONFLICT = 409
        private const val HTTP_UNPROCESSABLE = 422
    }
}
