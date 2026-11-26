package com.omnibus.omnibus.data.libraries.remote

import android.util.Log
import com.omnibus.omnibus.data.audiobook.position.AudioPosition
import com.omnibus.omnibus.data.audiobook.position.DefaultListeningProgressRepository
import com.omnibus.omnibus.data.audiobook.position.ListeningSessionReport
import com.omnibus.omnibus.data.audiobook.position.remote.AudioProgressUpdateRequestDto
import com.omnibus.omnibus.data.audiobook.position.remote.ListeningSessionReportDto
import com.omnibus.omnibus.data.audiobook.position.remote.toJsonRequest
import com.omnibus.omnibus.data.audiobook.rate.remote.PlaybackRateUpdateDto
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Calls to the server of a library that isn't the active one, with that library's own token. Nothing throws except
 * cancellation.
 */
interface InactiveLibraryApi {

    /** Ends the session on the server (`POST api/auth/logout`). Every answer and failure is ignored. */
    suspend fun logout(baseUrl: HttpUrl, token: String)

    /** Sends one pending position. True only on a 2xx. */
    suspend fun sendPosition(baseUrl: HttpUrl, token: String, position: AudioPosition): Boolean

    /** Sends one batch of queued sessions. True only on a 2xx. */
    suspend fun sendSessions(baseUrl: HttpUrl, token: String, batch: List<ListeningSessionReport>): Boolean

    /** Writes one pending playback rate. True only on a 2xx. */
    suspend fun putRate(baseUrl: HttpUrl, token: String, bookUuid: String, rate: Double): Boolean
}

/**
 * Default [InactiveLibraryApi] over [OmnibusHttpClient], run on [ioDispatcher]. Requests are run with `newCall`, never
 * `execute`, so a 401 from the removed library's server can't clear the active library's token.
 */
class DefaultInactiveLibraryApi @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
) : InactiveLibraryApi {

    override suspend fun logout(baseUrl: HttpUrl, token: String) {
        val request = omnibusHttpClient.request(baseUrl, LOGOUT_PATH, token)
            .post(ByteArray(0).toRequestBody(null))
            .build()
        withContext(ioDispatcher) {
            try {
                omnibusHttpClient.newCall(request).execute().close()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Log.w(TAG, "Unable to end a removed library's session", ex)
            }
        }
    }

    override suspend fun sendPosition(baseUrl: HttpUrl, token: String, position: AudioPosition): Boolean {
        val request = omnibusHttpClient.request(baseUrl, DefaultListeningProgressRepository.PROGRESS_PATH, token)
            .post(AudioProgressUpdateRequestDto.from(position).toJsonRequest())
            .build()
        return send(request)
    }

    override suspend fun sendSessions(
        baseUrl: HttpUrl,
        token: String,
        batch: List<ListeningSessionReport>,
    ): Boolean {
        val request = omnibusHttpClient.request(baseUrl, DefaultListeningProgressRepository.SESSIONS_PATH, token)
            .post(batch.map { ListeningSessionReportDto.from(it) }.toJsonRequest())
            .build()
        return send(request)
    }

    override suspend fun putRate(baseUrl: HttpUrl, token: String, bookUuid: String, rate: Double): Boolean {
        if (bookUuid.isBlank()) return false
        val builder = omnibusHttpClient.request(baseUrl, AUDIOBOOKS_PATH, token)
        val url = builder.build().url.newBuilder()
            .addPathSegment(bookUuid)
            .addPathSegment(RATE_SEGMENT)
            .build()
        val request = builder
            .url(url)
            .put(PlaybackRateUpdateDto(rate).toJsonRequest())
            .build()
        return send(request)
    }

    private suspend fun send(request: Request): Boolean = withContext(ioDispatcher) {
        try {
            omnibusHttpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach a removed library's server", ex)
            false
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure sending to a removed library's server", ex)
            false
        }
    }

    private companion object {
        const val TAG = "InactiveLibraryApi"

        /** Relative, so they resolve beneath the server's base URL. */
        const val LOGOUT_PATH = "api/auth/logout"

        /** The same path as `DefaultPlaybackRateApi`, whose constants are private. */
        const val AUDIOBOOKS_PATH = "api/audiobooks"
        const val RATE_SEGMENT = "playback-rate"
    }
}
