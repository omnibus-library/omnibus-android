package com.omnibus.omnibus.data.metadata

import android.util.Log
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.remote.BookDto
import com.omnibus.omnibus.data.library.remote.LibraryJson
import com.omnibus.omnibus.data.metadata.remote.VocabularyEntryDto
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Default [MetadataRepository]. Every failure becomes a result, so no exception leaves this class, except
 * cancellation, which always propagates. Saves run in the application scope, so leaving the page cannot strand one.
 */
@Singleton
class DefaultMetadataRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : MetadataRepository {

    /** The write queue for one (book, kind): a lock that keeps saves in order, and a counter that marks the newest. */
    private class Chain {
        val mutex = Mutex()
        val generation = AtomicLong(0)
    }

    private val chains = ConcurrentHashMap<Pair<String, MetadataListKind>, Chain>()

    override suspend fun replaceList(
        bookUuid: String,
        kind: MetadataListKind,
        values: List<String>,
    ): MetadataWriteResult {
        // A blank segment would address the list endpoint, so it is answered without a request.
        if (bookUuid.isBlank()) return MetadataWriteResult.Failure(MetadataError.NotFound)

        val chain = chains.computeIfAbsent(bookUuid to kind) { Chain() }
        val mine = chain.generation.incrementAndGet()
        // UNDISPATCHED: the lock is queued on the caller's thread, so saves queue in call (tap) order.
        // applicationScope, not the caller's scope: cancelling the caller never cancels a save.
        return applicationScope.async(start = CoroutineStart.UNDISPATCHED) {
            chain.mutex.withLock {
                if (chain.generation.get() != mine) MetadataWriteResult.Superseded
                else post(bookUuid, kind, values)
            }
        }.await()
    }

    override suspend fun getVocabulary(kind: MetadataListKind): VocabularyResult = withContext(ioDispatcher) {
        val path = when (kind) {
            MetadataListKind.Genres -> GENRES_PATH
            MetadataListKind.Tags -> TAGS_PATH
        }
        val builder = omnibusHttpClient.request(path) ?: return@withContext VocabularyResult.NoServer
        val request = builder.get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (response.isSuccessful) {
                    VocabularyResult.Success(
                        LibraryJson.decodeFromString<List<VocabularyEntryDto>>(response.body.string())
                            .map { it.toDomain() },
                    )
                } else {
                    VocabularyResult.Failure(errorOf(response))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for $path", ex)
            VocabularyResult.Failure(MetadataError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response for $path", ex)
            VocabularyResult.Failure(MetadataError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading $path", ex)
            VocabularyResult.Failure(MetadataError.UnexpectedResponse)
        }
    }

    /**
     * One POST of [kind]'s full list. The body carries that one key only, and an empty list is sent as `[]`.
     * The 200 answer is the merged book record.
     */
    private suspend fun post(bookUuid: String, kind: MetadataListKind, values: List<String>): MetadataWriteResult =
        withContext(ioDispatcher) {
            val builder = builderFor(EBOOKS_PATH, listOf(bookUuid, OVERRIDES))
                ?: return@withContext MetadataWriteResult.NoServer
            val json = buildJsonObject {
                put(kind.wireField, JsonArray(values.map { JsonPrimitive(it) }))
            }.toString()
            val body: RequestBody = json.toRequestBody(JSON_TYPE)
            val request = builder.post(body).build()

            try {
                omnibusHttpClient.execute(request).use { response ->
                    if (response.isSuccessful) {
                        val book: Book = LibraryJson.decodeFromString<BookDto>(response.body.string()).toDomain()
                        MetadataWriteResult.Saved(book)
                    } else {
                        MetadataWriteResult.Failure(errorOf(response))
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: IOException) {
                Log.w(TAG, "Unable to reach the server to save a list", ex)
                MetadataWriteResult.Failure(MetadataError.Unreachable)
            } catch (ex: IllegalArgumentException) {
                Log.w(TAG, "Unexpected response to a list save", ex)
                MetadataWriteResult.Failure(MetadataError.UnexpectedResponse)
            } catch (ex: Exception) {
                Log.e(TAG, "Unexpected failure saving a list", ex)
                MetadataWriteResult.Failure(MetadataError.UnexpectedResponse)
            }
        }

    /** A request builder for [path] with each of [pathSegments] appended, or null when no server is saved. */
    private suspend fun builderFor(path: String, pathSegments: List<String>): Request.Builder? {
        val builder = omnibusHttpClient.request(path) ?: return null
        val url = builder.build().url.newBuilder().apply {
            pathSegments.forEach { addPathSegment(it) }
        }.build()
        return builder.url(url)
    }

    /** Maps a non-2xx response to its error. The body is read only for the server's message. */
    private fun errorOf(response: Response): MetadataError = when (response.code) {
        HTTP_UNAUTHORIZED -> MetadataError.SessionExpired
        HTTP_FORBIDDEN -> MetadataError.Forbidden
        HTTP_NOT_FOUND -> MetadataError.NotFound
        else -> MetadataError.Http(response.code, serverMessage(response.body.string()))
    }

    /** The server's own text: the JSON `error`, `message` or `detail` string, else the plain-text body. */
    private fun serverMessage(body: String): String? {
        val json = try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (ex: SerializationException) {
            null
        }
        return json
            ?.let { obj ->
                SERVER_MESSAGE_KEYS.firstNotNullOfOrNull { key ->
                    (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
                        ?.takeIf { it.isNotBlank() }
                }
            }
            ?: body.trim().take(MAX_SERVER_MESSAGE_LENGTH).takeIf { it.isNotBlank() }
    }

    internal companion object {
        const val TAG = "DefaultMetadataRepository"

        const val EBOOKS_PATH = "api/ebooks"
        const val OVERRIDES = "overrides"
        const val GENRES_PATH = "api/genres"
        const val TAGS_PATH = "api/tags"

        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404

        const val MAX_SERVER_MESSAGE_LENGTH = 300
        val SERVER_MESSAGE_KEYS = listOf("error", "message", "detail")
    }
}
