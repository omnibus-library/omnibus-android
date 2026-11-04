package com.omnibus.omnibus.data.audiobook.crossformat

import android.util.Log
import com.omnibus.omnibus.data.audiobook.crossformat.local.CrossFormatDismissStore
import com.omnibus.omnibus.data.audiobook.crossformat.remote.CROSS_FORMAT_STATE_CANDIDATE
import com.omnibus.omnibus.data.audiobook.crossformat.remote.CrossFormatResumeDto
import com.omnibus.omnibus.data.library.remote.LibraryJson
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Default [CrossFormatRepository]: one GET for the ebook-to-audio candidate, and the dismissal memory in
 * [CrossFormatDismissStore]. A failure is [CrossFormatAudioResult.Failed] and is never thrown (cancellation excepted).
 */
@Singleton
class DefaultCrossFormatRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val dismissStore: CrossFormatDismissStore,
) : CrossFormatRepository {

    override suspend fun audioResume(bookUuid: String): CrossFormatAudioResult = withContext(ioDispatcher) {
        if (bookUuid.isBlank()) return@withContext CrossFormatAudioResult.Failed
        val builder = omnibusHttpClient.request(BOOKS_PATH) ?: return@withContext CrossFormatAudioResult.Failed
        val url = builder.build().url.newBuilder()
            .addPathSegment(bookUuid)
            .addPathSegment(CROSS_FORMAT_SEGMENT)
            .addQueryParameter("target", TARGET_AUDIO)
            .build()
        val request = builder.url(url).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (!response.isSuccessful) return@use CrossFormatAudioResult.Failed
                LibraryJson.decodeFromString<CrossFormatResumeDto>(response.body.string()).toResult()
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to read the ebook position", ex)
            CrossFormatAudioResult.Failed
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to the ebook position read", ex)
            CrossFormatAudioResult.Failed
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading the ebook position", ex)
            CrossFormatAudioResult.Failed
        }
    }

    override suspend fun dismissedClock(bookUuid: String): Long? = dismissStore.dismissedClock(bookUuid)

    override suspend fun storeDismissedClock(bookUuid: String, sourceClock: Long) {
        dismissStore.store(bookUuid, sourceClock)
    }

    /**
     * Only a `candidate` with a position is an offer. The file is not checked here: a candidate for another file, or
     * with no file, is the holder's to reject (TC-11, TC-39).
     */
    private fun CrossFormatResumeDto.toResult(): CrossFormatAudioResult {
        if (state != CROSS_FORMAT_STATE_CANDIDATE) return CrossFormatAudioResult.NoCandidate
        val candidate = candidate ?: return CrossFormatAudioResult.NoCandidate
        val position = candidate.audioPositionSeconds ?: return CrossFormatAudioResult.NoCandidate
        return CrossFormatAudioResult.Candidate(
            positionSeconds = position,
            bookFileId = candidate.bookFileId,
            sourceAhead = candidate.sourceAhead ?: true,
            sourceClock = candidate.sourceClientUpdatedAt,
            follow = follow,
        )
    }

    private companion object {
        const val TAG = "CrossFormatRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val BOOKS_PATH = "api/books"
        const val CROSS_FORMAT_SEGMENT = "cross-format-resume"
        const val TARGET_AUDIO = "audio"
    }
}
