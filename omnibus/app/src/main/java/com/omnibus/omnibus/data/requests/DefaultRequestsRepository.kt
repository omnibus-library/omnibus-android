package com.omnibus.omnibus.data.requests

import android.util.Log
import com.omnibus.omnibus.data.cover.CoverRepository
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.requests.remote.BookRequestDto
import com.omnibus.omnibus.data.requests.remote.ChooseVersionDto
import com.omnibus.omnibus.data.requests.remote.PaletteResultsDto
import com.omnibus.omnibus.data.requests.remote.RequestListingDto
import com.omnibus.omnibus.data.requests.remote.RequestsJson
import com.omnibus.omnibus.data.requests.remote.ResolveMetaRequestDto
import com.omnibus.omnibus.data.requests.remote.ScanSearchRequestDto
import com.omnibus.omnibus.data.requests.remote.ScanSearchResponseDto
import com.omnibus.omnibus.data.requests.remote.StartRequestDto
import com.omnibus.omnibus.data.requests.remote.StartRequestFromMetaDto
import com.omnibus.omnibus.data.requests.remote.WishlistBookDto
import com.omnibus.omnibus.data.requests.remote.jsonBody
import com.omnibus.omnibus.data.requests.remote.parseScanMatch
import com.omnibus.omnibus.data.requests.remote.toProviderBook
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.RequestBody
import okhttp3.Response

/**
 * Default [RequestsRepository]. Every failure becomes a result, so no exception leaves this class, except
 * cancellation, which always propagates.
 */
class DefaultRequestsRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val coverRepository: CoverRepository,
) : RequestsRepository {

    override suspend fun list(): RequestsResult<List<RequestListing>> =
        call("GET", REQUESTS_PATH, decode = ::decodeListings)

    override suspend fun bookList(uuid: String): RequestsResult<List<RequestListing>> =
        call("GET", BOOKS_PATH, listOf(uuid, REQUESTS), decode = ::decodeListings)

    override suspend fun request(id: Long): RequestsResult<BookRequest> =
        call("GET", REQUESTS_PATH, listOf(id.toString()), decode = ::decodeRequest)

    override suspend fun wanters(uuid: String): RequestsResult<List<Wanter>> =
        call("GET", WISHLISTS_PATH, query = mapOf("user" to "all")) { body ->
            RequestsJson.decodeFromString<List<WishlistBookDto>>(body)
                .firstOrNull { it.book.toDomain().uuid == uuid }
                ?.wantedBy.orEmpty().map { it.toDomain() }
        }

    override suspend fun start(uuid: String, kind: RequestKind): RequestsResult<BookRequest> =
        call(
            "POST", BOOKS_PATH, listOf(uuid, REQUESTS),
            body = jsonBody(RequestsJson.encodeToString(StartRequestDto(kind))),
            decode = ::decodeRequest,
        )

    override suspend fun startFromRecord(book: ProviderBook): RequestsResult<BookRequest> =
        call(
            "POST", REQUESTS_PATH,
            body = jsonBody(RequestsJson.encodeToString(StartRequestFromMetaDto(RequestKind.Ebook, book.meta))),
            decode = ::decodeRequest,
        )

    override suspend fun choose(id: Long, resultId: Long): RequestsResult<BookRequest> =
        call(
            "POST", REQUESTS_PATH, listOf(id.toString(), CHOOSE),
            body = jsonBody(RequestsJson.encodeToString(ChooseVersionDto(resultId))),
            decode = ::decodeRequest,
        )

    override suspend fun swap(id: Long): RequestsResult<BookRequest> = action(id, SWAP)

    override suspend fun retry(id: Long): RequestsResult<BookRequest> = action(id, RETRY)

    override suspend fun cancel(id: Long): RequestsResult<BookRequest> = action(id, CANCEL)

    override suspend fun undoCancel(id: Long): RequestsResult<BookRequest> =
        call("DELETE", REQUESTS_PATH, listOf(id.toString(), CANCEL), decode = ::decodeRequest)

    override suspend fun takeCover(id: Long): RequestsResult<BookRequest> =
        action(id, COVER).also { if (it is RequestsResult.Success) coverRepository.invalidate(it.value.bookUuid) }

    override suspend fun keepCover(id: Long): RequestsResult<BookRequest> =
        call("DELETE", REQUESTS_PATH, listOf(id.toString(), COVER), decode = ::decodeRequest)

    override suspend fun offeredCover(id: Long): RequestsResult<ByteArray> =
        send("GET", REQUESTS_PATH, listOf(id.toString(), COVER), read = { it.body.bytes() })

    override suspend fun searchLibrary(query: String): RequestsResult<List<LibraryHit>> =
        call("GET", PALETTE_PATH, query = mapOf("q" to query)) { body ->
            RequestsJson.decodeFromString<PaletteResultsDto>(body).books.map { it.toDomain() }
        }

    override suspend fun searchProviders(query: String): RequestsResult<List<ProviderBook>> =
        call(
            "POST", SCAN_SEARCH_PATH,
            body = jsonBody(RequestsJson.encodeToString(ScanSearchRequestDto(query))),
        ) { body ->
            RequestsJson.decodeFromString<ScanSearchResponseDto>(body).results.map { it.toProviderBook() }
        }

    override suspend fun resolve(book: ProviderBook): RequestsResult<ScanMatch> =
        call(
            "POST", RESOLVE_META_PATH,
            body = jsonBody(RequestsJson.encodeToString(ResolveMetaRequestDto(book.meta))),
            decode = ::parseScanMatch,
        )

    /** A command on one request that takes no body of its own. */
    private suspend fun action(id: Long, name: String): RequestsResult<BookRequest> =
        call("POST", REQUESTS_PATH, listOf(id.toString(), name), body = jsonBody("{}"), decode = ::decodeRequest)

    /**
     * One request, reduced to a value or a [RequestsError]. Every route maps errors here, so a failure is reported the
     * same way for each.
     *
     * @param segments appended to [path] one encoded segment each, so a uuid can't add or split segments.
     */
    private suspend fun <T> call(
        method: String,
        path: String,
        segments: List<String> = emptyList(),
        query: Map<String, String> = emptyMap(),
        body: RequestBody? = null,
        decode: (String) -> T,
    ): RequestsResult<T> = send(method, path, segments, query, body, read = { decode(it.body.string()) })

    /** [call] for a route whose answer [read] takes from the response itself, such as image bytes. */
    private suspend fun <T> send(
        method: String,
        path: String,
        segments: List<String> = emptyList(),
        query: Map<String, String> = emptyMap(),
        body: RequestBody? = null,
        read: (Response) -> T,
    ): RequestsResult<T> = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(path) ?: return@withContext RequestsResult.NoServer
        val url = builder.build().url.newBuilder().apply {
            segments.forEach { addPathSegment(it) }
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = builder.url(url).method(method, body).build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful -> RequestsResult.Success(read(response))
                    response.code == HTTP_UNAUTHORIZED -> RequestsResult.Failure(RequestsError.SessionExpired)
                    else -> RequestsResult.Failure(
                        RequestsError.Http(response.code, serverMessage(response.body.string())),
                    )
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for $method $path", ex)
            RequestsResult.Failure(RequestsError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to $method $path", ex)
            RequestsResult.Failure(RequestsError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure for $method $path", ex)
            RequestsResult.Failure(RequestsError.UnexpectedResponse)
        }
    }

    private fun decodeRequest(body: String): BookRequest = RequestsJson.decodeFromString<BookRequestDto>(body).toDomain()

    private fun decodeListings(body: String): List<RequestListing> =
        RequestsJson.decodeFromString<List<RequestListingDto>>(body).map { it.toDomain() }

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
                    (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
                }
            }
            ?: body.trim().take(MAX_SERVER_MESSAGE_LENGTH).takeIf { it.isNotBlank() }
    }

    internal companion object {
        const val TAG = "DefaultRequestsRepository"

        /** Relative, so each resolves beneath the server's base URL. */
        const val REQUESTS_PATH = "api/requests"
        const val BOOKS_PATH = "api/books"
        const val WISHLISTS_PATH = "api/wishlists"
        const val PALETTE_PATH = "api/search/palette"
        const val SCAN_SEARCH_PATH = "api/scan/search"
        const val RESOLVE_META_PATH = "api/scan/resolve-meta"

        const val REQUESTS = "requests"
        const val CHOOSE = "choose"
        const val SWAP = "swap"
        const val RETRY = "retry"
        const val CANCEL = "cancel"
        const val COVER = "cover"

        const val HTTP_UNAUTHORIZED = 401
        const val MAX_SERVER_MESSAGE_LENGTH = 300
        val SERVER_MESSAGE_KEYS = listOf("error", "message", "detail")
    }
}
