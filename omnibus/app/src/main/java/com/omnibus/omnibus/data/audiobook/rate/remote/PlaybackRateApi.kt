package com.omnibus.omnibus.data.audiobook.rate.remote

import android.util.Log
import com.omnibus.omnibus.data.library.remote.LibraryJson
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Request

/** The answer to a read of a book's saved playback rate (I-SPD-1). */
sealed interface RateFetch {

    /** The server holds [rate] for the book. */
    data class Found(val rate: Double) : RateFetch

    /** The listener never set a rate for the book. */
    data object Unset : RateFetch

    /** The server could not answer: no server saved, a non-2xx, a network failure or an unreadable body. */
    data object Failed : RateFetch
}

/** What a rate write meant for the local outbox (I-SPD-3). */
enum class PutOutcome {

    /** The server accepted the rate (2xx). */
    Sent,

    /** The server can never accept this write (400, 404, 422), so it must not be retried. */
    Drop,

    /** A transient or unknown failure: keep the rate pending and try again later. */
    Keep,
}

/** The two calls the rate store makes to the server. Neither throws except for cancellation. */
interface PlaybackRateApi {

    /** Reads the saved rate of [bookUuid]. */
    suspend fun fetch(bookUuid: String): RateFetch

    /** Writes [rate] for [bookUuid] and classifies the answer. */
    suspend fun put(bookUuid: String, rate: Double): PutOutcome
}

/** Default [PlaybackRateApi] over [OmnibusHttpClient], run on [ioDispatcher]. */
class DefaultPlaybackRateApi @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
) : PlaybackRateApi {

    override suspend fun fetch(bookUuid: String): RateFetch = withContext(ioDispatcher) {
        if (bookUuid.isBlank()) return@withContext RateFetch.Failed
        val builder = omnibusHttpClient.request(AUDIOBOOKS_PATH) ?: return@withContext RateFetch.Failed
        val request = builder.url(rateUrl(builder, bookUuid)).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (!response.isSuccessful) return@use RateFetch.Failed
                val record = LibraryJson.decodeFromString<PlaybackRateRecordDto?>(response.body.string())
                if (record == null) RateFetch.Unset else RateFetch.Found(record.playbackRate)
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to read a playback rate", ex)
            RateFetch.Failed
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to a playback rate read", ex)
            RateFetch.Failed
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading a playback rate", ex)
            RateFetch.Failed
        }
    }

    override suspend fun put(bookUuid: String, rate: Double): PutOutcome = withContext(ioDispatcher) {
        if (bookUuid.isBlank()) return@withContext PutOutcome.Drop
        val builder = omnibusHttpClient.request(AUDIOBOOKS_PATH) ?: return@withContext PutOutcome.Keep
        val request: Request = builder
            .url(rateUrl(builder, bookUuid))
            .put(PlaybackRateUpdateDto(rate).toJsonRequest())
            .build()

        try {
            omnibusHttpClient.execute(request).use { response -> classify(response.code) }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to save a playback rate", ex)
            PutOutcome.Keep
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure saving a playback rate", ex)
            PutOutcome.Keep
        }
    }

    /** A rate write: 2xx is sent, 400, 404 and 422 can never succeed so are dropped, anything else is kept (I-SPD-3). */
    private fun classify(code: Int): PutOutcome = when (code) {
        in 200..299 -> PutOutcome.Sent
        HTTP_BAD_REQUEST, HTTP_NOT_FOUND, HTTP_UNPROCESSABLE -> PutOutcome.Drop
        else -> PutOutcome.Keep
    }

    /** `api/audiobooks/{uuid}/playback-rate` on the saved server; the uuid is one encoded path segment. */
    private fun rateUrl(builder: Request.Builder, bookUuid: String) = builder.build().url.newBuilder()
        .addPathSegment(bookUuid)
        .addPathSegment(RATE_SEGMENT)
        .build()

    private companion object {
        const val TAG = "PlaybackRateApi"

        /** Relative, so it resolves beneath the server's base URL. */
        const val AUDIOBOOKS_PATH = "api/audiobooks"
        const val RATE_SEGMENT = "playback-rate"

        const val HTTP_BAD_REQUEST = 400
        const val HTTP_NOT_FOUND = 404
        const val HTTP_UNPROCESSABLE = 422
    }
}
