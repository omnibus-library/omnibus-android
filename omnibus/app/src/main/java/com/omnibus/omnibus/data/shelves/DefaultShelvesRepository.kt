package com.omnibus.omnibus.data.shelves

import android.util.Log
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.shelves.remote.AddBooksRequestDto
import com.omnibus.omnibus.data.shelves.remote.ShelfDetailDto
import com.omnibus.omnibus.data.shelves.remote.ShelfSummaryDto
import com.omnibus.omnibus.data.shelves.remote.ShelvesJson
import com.omnibus.omnibus.di.IoDispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/**
 * Default [ShelvesRepository]. Every failure becomes a result, so no exception leaves this class,
 * except cancellation, which always propagates.
 */
class DefaultShelvesRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
) : ShelvesRepository {

    override suspend fun getBookShelves(bookUuid: String): BookShelvesResult {
        // A blank segment would address the list endpoint, so it is answered without a request.
        if (bookUuid.isBlank()) return BookShelvesResult.Failure(ShelvesError.NotFound)

        return coroutineScope {
            val all = async {
                get(SHELVES_PATH) { response ->
                    ShelvesJson.decodeFromString<List<ShelfSummaryDto>>(response.body.string())
                        .mapNotNull { it.toDomain() }
                }
            }
            val member = async {
                get(SHELVES_PATH, listOf(CONTAINING, bookUuid)) { response ->
                    ShelvesJson.decodeFromString<List<Long>>(response.body.string())
                }
            }
            val smart = async {
                get(SHELVES_PATH, listOf(CONTAINING, bookUuid, SMART)) { response ->
                    ShelvesJson.decodeFromString<List<Long>>(response.body.string())
                }
            }

            val shelves = when (val read = all.await()) {
                is Read.Ok -> read.value
                is Read.Failed -> return@coroutineScope BookShelvesResult.Failure(read.error)
                Read.NoServer -> return@coroutineScope BookShelvesResult.NoServer
            }
            val memberIds = when (val read = member.await()) {
                is Read.Ok -> read.value
                is Read.Failed -> return@coroutineScope BookShelvesResult.Failure(read.error)
                Read.NoServer -> return@coroutineScope BookShelvesResult.NoServer
            }
            // The smart section is informational, so its failure degrades to no smart shelves.
            val smartIds = when (val read = smart.await()) {
                is Read.Ok -> read.value.toSet()
                is Read.Failed -> {
                    Log.w(TAG, "Matching smart shelves unavailable: ${read.error}")
                    emptySet()
                }
                Read.NoServer -> emptySet()
            }

            BookShelvesResult.Success(
                BookShelves(
                    shelves = shelves,
                    memberShelfIds = memberIds.toSet(),
                    matchingSmartShelfIds = smartIds,
                ),
            )
        }
    }

    override suspend fun getShelfRules(shelfId: Long): ShelfRulesResult =
        when (val read = get(SHELVES_PATH, listOf(shelfId.toString())) { response ->
            ShelvesJson.decodeFromString<ShelfDetailDto>(response.body.string()).toDomain()
        }) {
            is Read.Ok -> ShelfRulesResult.Success(read.value)
            is Read.Failed -> ShelfRulesResult.Failure(read.error)
            Read.NoServer -> ShelfRulesResult.NoServer
        }

    override suspend fun addBook(shelfId: Long, bookUuid: String): ShelfWriteResult {
        if (bookUuid.isBlank()) return ShelfWriteResult.Failure(ShelvesError.NotFound)
        return send(
            method = "POST",
            pathSegments = listOf(shelfId.toString(), BOOKS),
            body = AddBooksRequestDto(listOf(bookUuid)).toJsonRequest(),
        )
    }

    override suspend fun removeBook(shelfId: Long, bookUuid: String): ShelfWriteResult {
        if (bookUuid.isBlank()) return ShelfWriteResult.Failure(ShelvesError.NotFound)
        return send(method = "DELETE", pathSegments = listOf(shelfId.toString(), BOOKS, bookUuid), body = null)
    }

    /**
     * One GET, reduced to a value or a [ShelvesError]. Every shelves read maps errors here, so a
     * given failure is reported the same way for each read.
     *
     * @param pathSegments appended to [SHELVES_PATH] one encoded segment each, so a value such as a
     *   uuid can't add or split segments.
     */
    private suspend fun <T> get(
        path: String,
        pathSegments: List<String> = emptyList(),
        decode: (Response) -> T,
    ): Read<T> = withContext(ioDispatcher) {
        val builder = builderFor(path, pathSegments) ?: return@withContext Read.NoServer
        val request = builder.get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (response.isSuccessful) Read.Ok(decode(response)) else Read.Failed(errorOf(response))
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for $path", ex)
            Read.Failed(ShelvesError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response for $path", ex)
            Read.Failed(ShelvesError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading $path", ex)
            Read.Failed(ShelvesError.UnexpectedResponse)
        }
    }

    /** One POST or DELETE. Any 2xx counts as success, and the response body is not read. */
    private suspend fun send(method: String, pathSegments: List<String>, body: RequestBody?): ShelfWriteResult =
        withContext(ioDispatcher) {
            val builder = builderFor(SHELVES_PATH, pathSegments) ?: return@withContext ShelfWriteResult.NoServer
            val request: Request = builder.method(method, body).build()

            try {
                omnibusHttpClient.execute(request).use { response ->
                    if (response.isSuccessful) ShelfWriteResult.Success else ShelfWriteResult.Failure(errorOf(response))
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: IOException) {
                Log.w(TAG, "Unable to reach the server to $method $SHELVES_PATH", ex)
                ShelfWriteResult.Failure(ShelvesError.Unreachable)
            } catch (ex: IllegalArgumentException) {
                Log.w(TAG, "Unexpected response to $method $SHELVES_PATH", ex)
                ShelfWriteResult.Failure(ShelvesError.UnexpectedResponse)
            } catch (ex: Exception) {
                Log.e(TAG, "Unexpected failure to $method $SHELVES_PATH", ex)
                ShelfWriteResult.Failure(ShelvesError.UnexpectedResponse)
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
    private fun errorOf(response: Response): ShelvesError = when (response.code) {
        HTTP_UNAUTHORIZED -> ShelvesError.SessionExpired
        HTTP_FORBIDDEN -> ShelvesError.Forbidden
        HTTP_NOT_FOUND -> ShelvesError.NotFound
        else -> ShelvesError.Http(response.code, serverMessage(response.body.string()))
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

    /** The outcome of one GET before it is turned into a read's own result type. */
    private sealed interface Read<out T> {
        data class Ok<out T>(val value: T) : Read<T>
        data class Failed(val error: ShelvesError) : Read<Nothing>
        data object NoServer : Read<Nothing>
    }

    internal companion object {
        const val TAG = "DefaultShelvesRepository"

        const val SHELVES_PATH = "api/shelves"
        const val CONTAINING = "containing"
        const val SMART = "smart"
        const val BOOKS = "books"

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404

        const val MAX_SERVER_MESSAGE_LENGTH = 300
        val SERVER_MESSAGE_KEYS = listOf("error", "message", "detail")
    }
}
