package com.omnibus.omnibus.data.audiobook

import android.util.Log
import com.omnibus.omnibus.data.audiobook.playback.Chapter
import com.omnibus.omnibus.data.audiobook.playback.resolveChapters
import com.omnibus.omnibus.data.bookprogress.remote.AudiobookManifestDto
import com.omnibus.omnibus.data.bookprogress.remote.BookProgressJson
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request

/**
 * Default [AudiobookRepository]. Requests the manifest from the saved server, retries a 404 once without the file id,
 * and maps the answer to a typed result. Part and playlist URLs are resolved beneath the server's base URL, keeping any
 * path prefix. The headers the audio fetches need travel with the result.
 */
@Singleton
class DefaultAudiobookRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
) : AudiobookRepository {

    override suspend fun getManifest(bookUuid: String, bookFileId: Long?): AudiobookManifestResult =
        withContext(ioDispatcher) {
            try {
                val answer = when (val first = fetch(bookUuid, bookFileId)) {
                    is Fetch.Err ->
                        if (bookFileId != null && (first.error as? AudiobookManifestError.Http)?.code == HTTP_NOT_FOUND) {
                            fetch(bookUuid, null)
                        } else {
                            first
                        }
                    is Fetch.Ok -> first
                }
                when (answer) {
                    is Fetch.Err -> AudiobookManifestResult.Failure(answer.error)
                    is Fetch.Ok -> toResult(answer)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Log.e(TAG, "Unexpected failure reading the manifest of $bookUuid", ex)
                AudiobookManifestResult.Failure(AudiobookManifestError.UnexpectedResponse)
            }
        }

    /** The outcome of one GET: the decoded body with the file id and headers it was sent with, or the error it failed with. */
    private sealed interface Fetch {
        data class Ok(val dto: AudiobookManifestDto, val requestedFileId: Long?, val headers: Map<String, String>) : Fetch
        data class Err(val error: AudiobookManifestError) : Fetch
    }

    /** One GET of the manifest of [bookUuid], with `file_id` when [bookFileId] is set. */
    private suspend fun fetch(bookUuid: String, bookFileId: Long?): Fetch {
        val builder = omnibusHttpClient.request(AUDIOBOOKS_PATH) ?: return Fetch.Err(AudiobookManifestError.NoServer)
        val url = builder.build().url.newBuilder().apply {
            addPathSegment(bookUuid)
            addPathSegment(MANIFEST_SEGMENT)
            bookFileId?.let { addQueryParameter(FILE_ID_QUERY, it.toString()) }
        }.build()
        val request = builder.url(url).get().build()
        return try {
            omnibusHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful -> Fetch.Ok(
                        dto = BookProgressJson.decodeFromString<AudiobookManifestDto>(response.body.string()),
                        requestedFileId = bookFileId,
                        headers = audioHeaders(request),
                    )
                    response.code == HTTP_UNAUTHORIZED -> Fetch.Err(AudiobookManifestError.SessionExpired)
                    else -> Fetch.Err(
                        AudiobookManifestError.Http(response.code, serverMessage(response.body.string())),
                    )
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to read the manifest of $bookUuid", ex)
            Fetch.Err(AudiobookManifestError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response for the manifest of $bookUuid", ex)
            Fetch.Err(AudiobookManifestError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading the manifest of $bookUuid", ex)
            Fetch.Err(AudiobookManifestError.UnexpectedResponse)
        }
    }

    /** Maps a decoded manifest to its domain form, resolving every URL it names. */
    private suspend fun toResult(answer: Fetch.Ok): AudiobookManifestResult {
        val dto = answer.dto
        // The server's id wins, then the id that was asked for. After a 404 retry nothing was asked for.
        val fileId = dto.bookFileId.takeIf { it > 0 } ?: answer.requestedFileId ?: 0L
        return when (dto.mode) {
            MODE_DIRECT -> {
                if (dto.parts.isEmpty()) return AudiobookManifestResult.Failure(AudiobookManifestError.NoAudioParts)
                val parts = mutableListOf<AudioPart>()
                for (part in dto.parts.sortedBy { it.ordinal }) {
                    val url = resolve(part.url) ?: return AudiobookManifestResult.Failure(AudiobookManifestError.NoServer)
                    parts += AudioPart(part.ordinal, url, part.durationSeconds, part.mime)
                }
                val total = dto.totalDurationSeconds?.takeIf { it > 0 } ?: parts.sumOf { it.durationSeconds }
                val chapters = resolveChapters(
                    dto.chapters.map { Chapter(it.ordinal, it.title, it.startSeconds, it.durationSeconds) },
                    total,
                )
                AudiobookManifestResult.Success(
                    AudiobookManifest.Direct(fileId, parts, total, chapters, answer.headers),
                )
            }
            MODE_HLS -> {
                val playlist = dto.playlistUrl?.takeIf { it.isNotBlank() }
                    ?: return AudiobookManifestResult.Failure(AudiobookManifestError.InvalidManifest)
                val url = resolve(playlist) ?: return AudiobookManifestResult.Failure(AudiobookManifestError.NoServer)
                AudiobookManifestResult.Success(AudiobookManifest.Hls(fileId, url, answer.headers))
            }
            else -> AudiobookManifestResult.Failure(AudiobookManifestError.InvalidManifest)
        }
    }

    /**
     * The absolute URL for a server path such as `/api/audiobooks/{uuid}/parts/1?file_id=7`, which keeps its query. Null
     * when no server is saved. An absolute URL is returned as it is.
     */
    private suspend fun resolve(url: String): String? {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        // The server sends a leading slash, which OmnibusHttpClient rejects, so it is stripped first.
        return omnibusHttpClient.request(url.removePrefix("/"))?.build()?.url?.toString()
    }

    /** The headers every audio fetch sends: the manifest request's bearer token, and an `Accept` for any media type. */
    private fun audioHeaders(request: Request): Map<String, String> = buildMap {
        request.header("Authorization")?.let { put("Authorization", it) }
        put("Accept", "*/*")
    }

    /** The server's own text: its JSON `error`, `message` or `detail` string, else the trimmed plain body. */
    private fun serverMessage(body: String): String? {
        val json = try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (ex: SerializationException) {
            null
        }
        return json
            ?.let { obj ->
                SERVER_MESSAGE_KEYS.firstNotNullOfOrNull { key ->
                    (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
                }
            }
            ?: body.trim().take(MAX_SERVER_MESSAGE_LENGTH).takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "DefaultAudiobookRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val AUDIOBOOKS_PATH = "api/audiobooks"
        const val MANIFEST_SEGMENT = "manifest"
        const val FILE_ID_QUERY = "file_id"

        const val MODE_DIRECT = "direct"
        const val MODE_HLS = "hls"

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404

        const val MAX_SERVER_MESSAGE_LENGTH = 300
        val SERVER_MESSAGE_KEYS = listOf("error", "message", "detail")
    }
}
