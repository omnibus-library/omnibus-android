package com.omnibus.omnibus.data.library

import android.util.Log
import com.omnibus.omnibus.data.library.remote.EbooksPageResponseDto
import com.omnibus.omnibus.data.library.remote.LibraryJson
import com.omnibus.omnibus.data.library.remote.ResumePointDto
import com.omnibus.omnibus.data.library.remote.ShelfDto
import com.omnibus.omnibus.data.library.remote.ShelfPageResponseDto
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/**
 * Default [LibraryRepository]. Every failure becomes a result, so no exception leaves this class,
 * except cancellation, which always propagates.
 */
class DefaultLibraryRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
) : LibraryRepository {

    override fun getBooksPageFlow(query: BooksPageQuery, cursor: String?): Flow<LibraryPageResult> = flow {
        // The server rejects a cursor sent without sort and dir, so every page request carries all of them.
        val params = buildList {
            add("limit" to PAGE_SIZE.toString())
            add("sort" to query.sort.apiName)
            add("dir" to query.dir.apiName)
            cursor?.let { add("cursor" to it) }
            if (query.formats.isNotEmpty()) {
                add("formats" to query.formats.map { it.lowercase() }.distinct().joinToString(","))
            }
            if (query.excludeFormats.isNotEmpty()) {
                add(
                    "exclude_formats" to query.excludeFormats.map { it.lowercase() }.distinct().sorted()
                        .joinToString(","),
                )
            }
        }

        val read = get(EBOOKS_PATH, params) { response ->
            val body = LibraryJson.decodeFromString<EbooksPageResponseDto>(response.body.string())
            BooksPage(
                books = body.books.map { it.toDomain() },
                nextCursor = response.header(NEXT_CURSOR_HEADER)?.takeIf { it.isNotBlank() },
                total = response.header(TOTAL_COUNT_HEADER)?.toLongOrNull(),
                hiddenCount = response.header(HIDDEN_COUNT_HEADER)?.toIntOrNull(),
            )
        }
        emit(
            when (read) {
                is Read.Ok -> LibraryPageResult.Success(read.value)
                is Read.Failed -> LibraryPageResult.Failure(read.message)
                Read.NoServer -> LibraryPageResult.NoServer
            },
        )
    }

    override fun getRecentProgressFlow(): Flow<RecentProgressResult> = flow {
        val params = listOf("limit" to RECENT_LIMIT.toString())

        val read = get(RECENT_PROGRESS_PATH, params) { response ->
            LibraryJson.decodeFromString<List<ResumePointDto>>(response.body.string())
                .mapNotNull { it.toDomain() }
        }
        emit(
            when (read) {
                is Read.Ok -> RecentProgressResult.Success(read.value)
                is Read.Failed -> RecentProgressResult.Failure(read.message)
                Read.NoServer -> RecentProgressResult.NoServer
            },
        )
    }

    override fun getShelfPreviewsFlow(): Flow<ShelfPreviewsResult> = flow {
        val shelves = when (
            val read = get(SHELVES_PATH) { response ->
                LibraryJson.decodeFromString<List<ShelfDto>>(response.body.string()).map { it.toDomain() }
            }
        ) {
            is Read.Ok -> read.value
            is Read.Failed -> {
                emit(ShelfPreviewsResult.Failure(read.message))
                return@flow
            }
            Read.NoServer -> {
                emit(ShelfPreviewsResult.NoServer)
                return@flow
            }
        }

        // awaitAll keeps input order, so the previews stay in the server's shelf order.
        val previews = coroutineScope {
            shelves.map { shelf ->
                async {
                    // A shelf whose covers can't be loaded still shows, just without covers.
                    val covers = if (shelf.bookCount > 0) {
                        val read = get("$SHELVES_PATH/${shelf.id}/page") { response ->
                            LibraryJson.decodeFromString<ShelfPageResponseDto>(response.body.string())
                                .books
                                .take(SHELF_COVER_COUNT)
                                .map { it.toDomain() }
                        }
                        when (read) {
                            is Read.Ok -> read.value
                            is Read.Failed -> {
                                Log.w(TAG, "Shelf ${shelf.id} covers unavailable: ${read.message}")
                                emptyList()
                            }
                            Read.NoServer -> emptyList()
                        }
                    } else {
                        emptyList()
                    }
                    ShelfPreview(shelf = shelf, covers = covers)
                }
            }.awaitAll()
        }
        emit(ShelfPreviewsResult.Success(previews))
    }

    /**
     * One GET, reduced to a value or a failure. Every library read maps errors here, so the
     * failure text is the same for each read.
     */
    private suspend fun <T> get(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
        decode: (Response) -> T,
    ): Read<T> = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(path) ?: return@withContext Read.NoServer
        val url = builder.build().url.newBuilder().apply {
            params.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val request = builder.url(url).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (response.isSuccessful) {
                    Read.Ok(decode(response))
                } else {
                    Read.Failed(LibraryErrorMessages.forHttp(response.code, response.body.string()))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for $path", ex)
            Read.Failed(LibraryErrorMessages.UNREACHABLE)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response for $path", ex)
            Read.Failed(LibraryErrorMessages.UNEXPECTED)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading $path", ex)
            Read.Failed(LibraryErrorMessages.UNEXPECTED)
        }
    }

    /** The outcome of one GET before it is turned into a read's own result type. */
    private sealed interface Read<out T> {
        data class Ok<out T>(val value: T) : Read<T>
        data class Failed(val message: String) : Read<Nothing>
        data object NoServer : Read<Nothing>
    }

    internal companion object {
        const val TAG = "DefaultLibraryRepository"

        const val EBOOKS_PATH = "api/ebooks"
        const val RECENT_PROGRESS_PATH = "api/progress/recent"
        const val SHELVES_PATH = "api/shelves"

        const val PAGE_SIZE = 60
        const val RECENT_LIMIT = 5
        const val SHELF_COVER_COUNT = 4

        const val NEXT_CURSOR_HEADER = "X-Next-Cursor"
        const val TOTAL_COUNT_HEADER = "X-Total-Count"
        const val HIDDEN_COUNT_HEADER = "X-Hidden-Count"
    }
}
