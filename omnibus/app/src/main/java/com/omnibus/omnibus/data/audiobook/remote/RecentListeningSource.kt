package com.omnibus.omnibus.data.audiobook.remote

import android.util.Log
import com.omnibus.omnibus.data.library.ResumePoint
import com.omnibus.omnibus.data.library.remote.LibraryJson
import com.omnibus.omnibus.data.library.remote.ResumePointDto
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The audiobooks the user listened to most recently, for "play with nothing loaded" (I-SYS-5). */
interface RecentListeningSource {

    /** `GET api/progress/recent?limit=12&format=audio`, newest first. Empty on any failure; never throws. */
    suspend fun recentAudio(): List<ResumePoint>
}

/** Reads the recent audio points from the saved server, using the library's resume-point decoding. */
class DefaultRecentListeningSource @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
) : RecentListeningSource {

    override suspend fun recentAudio(): List<ResumePoint> = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(RECENT_PATH) ?: return@withContext emptyList()
        val url = builder.build().url.newBuilder()
            .addQueryParameter(LIMIT_QUERY, RECENT_LIMIT.toString())
            .addQueryParameter(FORMAT_QUERY, AUDIO_FORMAT)
            .build()
        try {
            omnibusHttpClient.execute(builder.url(url).get().build()).use { response ->
                if (response.isSuccessful) {
                    LibraryJson.decodeFromString<List<ResumePointDto>>(response.body.string())
                        .mapNotNull { it.toDomain() }
                } else {
                    Log.w(TAG, "The recent listening read failed with HTTP ${response.code}")
                    emptyList()
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for the recent listening read", ex)
            emptyList()
        } catch (ex: Exception) {
            // A body that doesn't decode is a failed read, the same as an unreachable server.
            Log.w(TAG, "Unexpected response for the recent listening read", ex)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "DefaultRecentListeningSource"

        /** Relative, so it resolves beneath the server's base URL. */
        const val RECENT_PATH = "api/progress/recent"
        const val LIMIT_QUERY = "limit"
        const val FORMAT_QUERY = "format"
        const val RECENT_LIMIT = 12
        const val AUDIO_FORMAT = "audio"
    }
}
