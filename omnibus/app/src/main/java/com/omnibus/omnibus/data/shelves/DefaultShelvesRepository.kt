package com.omnibus.omnibus.data.shelves

import android.util.Log
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.remote.LibraryJson
import com.omnibus.omnibus.data.network.AuthTokenProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.ServerBaseUrlProvider
import com.omnibus.omnibus.data.shelves.remote.AddBooksRequestDto
import com.omnibus.omnibus.data.shelves.remote.RulePreviewDto
import com.omnibus.omnibus.data.shelves.remote.RulePreviewRequestDto
import com.omnibus.omnibus.data.shelves.remote.ShelfDetailDto
import com.omnibus.omnibus.data.shelves.remote.ShelfPageDto
import com.omnibus.omnibus.data.shelves.remote.ShelfSummaryDto
import com.omnibus.omnibus.data.shelves.remote.ShelvesJson
import com.omnibus.omnibus.data.shelves.remote.UpdateShelfRequestDto
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.RequestBody
import okhttp3.Response

/**
 * Default [ShelvesRepository]. Every failure becomes a result, so no exception leaves this class,
 * except cancellation, which always propagates.
 */
class DefaultShelvesRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val baseUrlProvider: ServerBaseUrlProvider,
    private val authTokenProvider: AuthTokenProvider,
) : ShelvesRepository {

    /** The last successful read of each shelf's detail, per server, token and shelf. */
    private val detailCache = ConcurrentHashMap<CacheKey, ShelfDetail>()

    /** The last successful unfiltered read of each shelf's books, per server, token and shelf. */
    private val booksCache = ConcurrentHashMap<CacheKey, List<Book>>()

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

    override fun getShelfFlow(shelfId: Long): Flow<ShelfDetailResult> = flow {
        // Read once, before the request, so the result is filed under the session it was asked for.
        val key = cacheKey(shelfId)
        key?.let { detailCache[it] }?.let { emit(ShelfDetailResult.Success(it)) }
        emit(readDetail(shelfId, key))
    }

    override suspend fun getShelf(shelfId: Long): ShelfDetailResult = readDetail(shelfId, cacheKey(shelfId))

    override fun getShelfBooksFlow(shelfId: Long, clauses: List<FilterClause>): Flow<ShelfBooksResult> = flow {
        val filter = filterQueryValue(clauses)
        val key = cacheKey(shelfId)
        // Only the unfiltered list is cached, so a filtered read never shows as the shelf's books.
        if (filter == null) key?.let { booksCache[it] }?.let { emit(ShelfBooksResult.Success(it)) }

        val params = listOfNotNull(filter?.let { FILTER_PARAM to it })
        val read = get(SHELVES_PATH, listOf(shelfId.toString(), PAGE), params) { response ->
            LibraryJson.decodeFromString<ShelfPageDto>(response.body.string()).books.map { it.toDomain() }
        }
        emit(
            when (read) {
                is Read.Ok -> {
                    if (filter == null && key != null) booksCache[key] = read.value
                    ShelfBooksResult.Success(read.value)
                }
                is Read.Failed -> {
                    if (read.error == ShelvesError.NotFound) ShelfBooksResult.Gone else ShelfBooksResult.Failure(read.error)
                }
                Read.NoServer -> ShelfBooksResult.NoServer
            },
        )
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

    override suspend fun updateShelf(shelfId: Long, update: ShelfUpdate): ShelfWriteResult {
        val result = send(
            method = "PATCH",
            pathSegments = listOf(shelfId.toString()),
            body = UpdateShelfRequestDto.of(update).toJsonRequest(),
        )
        if (result == ShelfWriteResult.Success) dropCaches(shelfId)
        return result
    }

    override suspend fun deleteShelf(shelfId: Long): ShelfWriteResult {
        val result = send(method = "DELETE", pathSegments = listOf(shelfId.toString()), body = null)
        if (result == ShelfWriteResult.Success) dropCaches(shelfId)
        return result
    }

    override suspend fun previewRules(matchMode: ShelfMatchMode, rules: List<ShelfRule>): RulePreviewResult {
        val body = RulePreviewRequestDto.of(matchMode, rules).toJsonRequest()
        return when (val read = exchange("POST", SHELVES_PATH, listOf(PREVIEW), body = body) { response ->
            ShelvesJson.decodeFromString<RulePreviewDto>(response.body.string())
        }) {
            is Read.Ok -> RulePreviewResult.Success(RuleMatchCount(read.value.matched, read.value.total))
            is Read.Failed -> RulePreviewResult.Failure(read.error)
            Read.NoServer -> RulePreviewResult.NoServer
        }
    }

    /** One detail read. A success is cached; a 404 is [ShelfDetailResult.Gone], not a failure. */
    private suspend fun readDetail(shelfId: Long, key: CacheKey?): ShelfDetailResult =
        when (val read = get(SHELVES_PATH, listOf(shelfId.toString())) { response ->
            ShelvesJson.decodeFromString<ShelfDetailDto>(response.body.string()).toDetail()
        }) {
            is Read.Ok -> {
                val detail = read.value
                if (detail == null) {
                    ShelfDetailResult.Failure(ShelvesError.UnexpectedResponse)
                } else {
                    if (key != null) detailCache[key] = detail
                    ShelfDetailResult.Success(detail)
                }
            }
            is Read.Failed -> {
                if (read.error == ShelvesError.NotFound) ShelfDetailResult.Gone else ShelfDetailResult.Failure(read.error)
            }
            Read.NoServer -> ShelfDetailResult.NoServer
        }

    /**
     * The cache key for [shelfId] under the saved server and signed-in token, or null when either is missing,
     * in which case nothing is cached.
     */
    private suspend fun cacheKey(shelfId: Long): CacheKey? {
        val server = baseUrlProvider.baseUrl() ?: return null
        val token = authTokenProvider.token()?.takeIf { it.isNotBlank() } ?: return null
        return CacheKey(server, token, shelfId)
    }

    /** Forgets the cached detail and books of [shelfId] under every server and token. */
    private fun dropCaches(shelfId: Long) {
        detailCache.keys.removeAll { it.shelfId == shelfId }
        booksCache.keys.removeAll { it.shelfId == shelfId }
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
        params: List<Pair<String, String>> = emptyList(),
        decode: (Response) -> T,
    ): Read<T> = exchange("GET", path, pathSegments, params, body = null, decode = decode)

    /** One POST, PUT, PATCH or DELETE. Any 2xx counts as success, and the response body is not read. */
    private suspend fun send(method: String, pathSegments: List<String>, body: RequestBody?): ShelfWriteResult =
        when (val read = exchange(method, SHELVES_PATH, pathSegments, body = body) { }) {
            is Read.Ok -> ShelfWriteResult.Success
            is Read.Failed -> ShelfWriteResult.Failure(read.error)
            Read.NoServer -> ShelfWriteResult.NoServer
        }

    /**
     * One request under [path], reduced to a value or a [ShelvesError]. The only place a shelves request is sent,
     * so every call maps its failures the same way.
     *
     * @param pathSegments appended to [path] one encoded segment each.
     * @param params added as query parameters, each encoded.
     */
    private suspend fun <T> exchange(
        method: String,
        path: String,
        pathSegments: List<String> = emptyList(),
        params: List<Pair<String, String>> = emptyList(),
        body: RequestBody? = null,
        decode: (Response) -> T,
    ): Read<T> = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(path) ?: return@withContext Read.NoServer
        val url = builder.build().url.newBuilder().apply {
            pathSegments.forEach { addPathSegment(it) }
            params.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val request = builder.url(url).method(method, body).build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (response.isSuccessful) Read.Ok(decode(response)) else Read.Failed(errorOf(response))
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for $method $path", ex)
            Read.Failed(ShelvesError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response for $method $path", ex)
            Read.Failed(ShelvesError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure for $method $path", ex)
            Read.Failed(ShelvesError.UnexpectedResponse)
        }
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

    /**
     * Identifies one cached shelf read. A plain class, not a data class, so its `toString` never prints the token.
     */
    private class CacheKey(val server: HttpUrl, val token: String, val shelfId: Long) {

        override fun equals(other: Any?): Boolean =
            other is CacheKey && server == other.server && token == other.token && shelfId == other.shelfId

        override fun hashCode(): Int = Objects.hash(server, token, shelfId)
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
        const val PAGE = "page"
        const val PREVIEW = "preview"
        const val FILTER_PARAM = "filter"

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404

        const val MAX_SERVER_MESSAGE_LENGTH = 300
        val SERVER_MESSAGE_KEYS = listOf("error", "message", "detail")
    }
}
