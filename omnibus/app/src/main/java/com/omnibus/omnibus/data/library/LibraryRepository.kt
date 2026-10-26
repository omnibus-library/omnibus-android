package com.omnibus.omnibus.data.library

import kotlinx.coroutines.flow.Flow

/**
 * Library reads. Online-only for now, so each flow emits once and completes. Callers still collect
 * every emission, because a cached-then-network emission is planned and must not need caller changes.
 */
interface LibraryRepository {

    /** One page of books. Pass the previous page's [BooksPage.nextCursor] as [cursor] for the next one. */
    fun booksPage(
        query: BooksPageQuery = BooksPageQuery(),
        cursor: String? = null,
    ): Flow<LibraryPageResult>

    /** The most recent resume points, newest first. */
    fun recentProgress(): Flow<RecentProgressResult>

    /** Every shelf the user can see, in the server's order, each with up to four covers. */
    fun shelfPreviews(): Flow<ShelfPreviewsResult>
}

/** The sort keys the server accepts for a books page. [apiName] is what goes on the wire. */
enum class LibrarySort(val apiName: String) {
    Title("title"),
    Author("author"),
    Series("series"),
    RecentlyInteracted("recently_interacted"),
    LastUpdated("last_updated"),
    NewestAdded("newest_added"),
}

/** The order of a books page. [apiName] is what goes on the wire. */
enum class SortDirection(val apiName: String) {
    Asc("asc"),
    Desc("desc"),
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
 * @property nextCursor Pass to the next [LibraryRepository.booksPage] call, or null on the last page.
 * @property total The library size for the query, when the server reports it.
 * @property hiddenCount How many books the `excludeFormats` filter hid. Only sent on the first page.
 */
data class BooksPage(
    val books: List<Book>,
    val nextCursor: String?,
    val total: Long?,
    val hiddenCount: Int?,
)

/** The outcome of [LibraryRepository.booksPage]. */
sealed interface LibraryPageResult {

    data class Success(val page: BooksPage) : LibraryPageResult

    /** No server is saved, so no request was made. */
    data object NoServer : LibraryPageResult

    /** [message] is the text to show the user. */
    data class Failure(val message: String) : LibraryPageResult
}

/** The outcome of [LibraryRepository.recentProgress]. */
sealed interface RecentProgressResult {

    data class Success(val points: List<ResumePoint>) : RecentProgressResult

    /** No server is saved, so no request was made. */
    data object NoServer : RecentProgressResult

    /** [message] is the text to show the user. */
    data class Failure(val message: String) : RecentProgressResult
}

/** The outcome of [LibraryRepository.shelfPreviews]. */
sealed interface ShelfPreviewsResult {

    data class Success(val previews: List<ShelfPreview>) : ShelfPreviewsResult

    /** No server is saved, so no request was made. */
    data object NoServer : ShelfPreviewsResult

    /** [message] is the text to show the user. A failed cover fetch for one shelf never gets here. */
    data class Failure(val message: String) : ShelfPreviewsResult
}
