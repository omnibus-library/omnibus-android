package com.omnibus.omnibus.data.progress

import android.util.Log
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.progress.local.ReadingPositionStore
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
