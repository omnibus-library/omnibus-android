package com.omnibus.omnibus.data.library

import kotlinx.coroutines.flow.Flow

/**
 * Library reads. Online-only for now, so each flow emits once and completes. Callers still collect
 * every emission, because a cached-then-network emission is planned and must not need caller changes.
 */
interface LibraryRepository {

    /** One page of books. Pass the previous page's [BooksPage.nextCursor] as [cursor] for the next one. */
    fun getBooksPageFlow(
        query: BooksPageQuery = BooksPageQuery(),
        cursor: String? = null,
    ): Flow<LibraryPageResult>

    /** The most recent resume points, newest first. */
    fun getRecentProgressFlow(): Flow<RecentProgressResult>

    /** Every shelf the user can see, in the server's order, each with up to four covers. */
    fun getShelfPreviewsFlow(): Flow<ShelfPreviewsResult>
}

/** The sort keys the server accepts for a books page. [apiName] is what goes on the wire. */
enum class LibrarySort(val apiName: String) {
    Title("title"),
    Author("author"),
    Series("series"),
    RecentlyInteracted("recently_interacted"),
    LastUpdated("last_updated"),
    NewestAdded("newest_added");

    companion object {

        /** The sort whose [apiName] is [raw], or null when none matches. The match is exact and case-sensitive. */
        fun fromApiName(raw: String?): LibrarySort? = entries.firstOrNull { it.apiName == raw }
    }
}

/** The order of a books page. [apiName] is what goes on the wire. */
enum class SortDirection(val apiName: String) {
    Asc("asc"),
    Desc("desc");

    /** The opposite order. */
    fun toggled(): SortDirection = if (this == Asc) Desc else Asc

    companion object {

        /** The direction whose [apiName] is [raw], or null when none matches. The match is exact and case-sensitive. */
        fun fromApiName(raw: String?): SortDirection? = entries.firstOrNull { it.apiName == raw }
    }
}

/**
 * Filters and ordering for a books page.
 *
 * @property formats Only books with one of these formats. Case-insensitive.
 * @property excludeFormats Hides books whose formats are all listed. Case-insensitive.
 */
data class BooksPageQuery(
    val sort: LibrarySort = LibrarySort.NewestAdded,
    val dir: SortDirection = SortDirection.Desc,
    val formats: Set<String> = emptySet(),
    val excludeFormats: Set<String> = emptySet(),
)

/**
 * One page of books.
 *
 * @property nextCursor Pass to the next [LibraryRepository.getBooksPageFlow] call, or null on the last page.
 * @property total The library size for the query, when the server reports it.
 * @property hiddenCount How many books the `excludeFormats` filter hid. Only sent on the first page.
 */
data class BooksPage(
    val books: List<Book>,
    val nextCursor: String?,
    val total: Long?,
    val hiddenCount: Int?,
)

/** The outcome of [LibraryRepository.getBooksPageFlow]. */
sealed interface LibraryPageResult {

    data class Success(val page: BooksPage) : LibraryPageResult

    /** No server is saved, so no request was made. */
    data object NoServer : LibraryPageResult

    /** [error] is why the read failed. The UI layer decides what text to show for it. */
    data class Failure(val error: LibraryError) : LibraryPageResult
}

/** The outcome of [LibraryRepository.getRecentProgressFlow]. */
sealed interface RecentProgressResult {

    data class Success(val points: List<ResumePoint>) : RecentProgressResult

    /** No server is saved, so no request was made. */
    data object NoServer : RecentProgressResult

    /** [error] is why the read failed. The UI layer decides what text to show for it. */
    data class Failure(val error: LibraryError) : RecentProgressResult
}

/** The outcome of [LibraryRepository.getShelfPreviewsFlow]. */
sealed interface ShelfPreviewsResult {

    data class Success(val previews: List<ShelfPreview>) : ShelfPreviewsResult

    /** No server is saved, so no request was made. */
    data object NoServer : ShelfPreviewsResult

    /** [error] is why the read failed. A failed cover fetch for one shelf never gets here. */
    data class Failure(val error: LibraryError) : ShelfPreviewsResult
}

/** Why a library read failed. The UI layer decides what text to show for each. */
sealed interface LibraryError {

    /** The server could not be reached. */
    data object Unreachable : LibraryError

    /** The response could not be decoded, or the read failed for any other reason. */
    data object UnexpectedResponse : LibraryError

    /** The server answered 401, so the user must sign in again. */
    data object SessionExpired : LibraryError

    /**
     * The server answered with another non-2xx status.
     *
     * @property code The HTTP status code.
     * @property serverMessage The server's own text: the JSON `error`, `message` or `detail` string,
     * else the trimmed plain-text body capped at 300 characters, else null.
     */
    data class Http(val code: Int, val serverMessage: String?) : LibraryError
}
