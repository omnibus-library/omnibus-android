package com.omnibus.omnibus.feature.bookdetail

import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.bookdetail.stub.BookDetailPlaceholderRoute
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute
import com.omnibus.omnibus.feature.requests.BookRequestRoute

/** Where a tap on the Book Detail page leads. The shell maps each destination to a route or a platform action. */
sealed interface BookDetailDestination {
    /**
     * Opens the reader for the book, at [startCfi] instead of the saved position when it is given.
     *
     * @property startCfi An EPUB CFI to open at, or null to resume.
     */
    data class Reader(val bookUuid: String, val startCfi: String? = null) : BookDetailDestination

    /**
     * Opens the player on the root stack at [bookFileId], or at the server's choice when it is null.
     *
     * @property positionSeconds Where to start, in seconds, or null for the saved place.
     */
    data class Player(
        val bookUuid: String,
        val bookFileId: Long?,
        val positionSeconds: Double? = null,
    ) : BookDetailDestination

    data class Book(val bookUuid: String) : BookDetailDestination

    data class Author(val authorId: Long, val name: String) : BookDetailDestination

    data class Series(val seriesId: Long?, val name: String) : BookDetailDestination

    data class Genre(val name: String) : BookDetailDestination

    data class Tag(val name: String) : BookDetailDestination

    data class CheckIn(val bookUuid: String) : BookDetailDestination

    /** Opens the book's request page. */
    data class Request(val bookUuid: String) : BookDetailDestination

    data object Settings : BookDetailDestination

    data class ExternalUrl(val url: String) : BookDetailDestination
}

/** The two placeholder titles that come from string resources. */
data class PlaceholderTitles(val checkIn: String, val settings: String)

/**
 * The shell route this destination pushes, or null for Reader, Player and ExternalUrl, which the shell hands to the root
 * stack or the platform.
 */
internal fun BookDetailDestination.shellRoute(titles: PlaceholderTitles): NavKey? = when (this) {
    is BookDetailDestination.Reader, is BookDetailDestination.Player, is BookDetailDestination.ExternalUrl -> null
    is BookDetailDestination.Book -> BookDetailRoute(bookUuid)
    is BookDetailDestination.Request -> BookRequestRoute(bookUuid)
    is BookDetailDestination.Author -> BookDetailPlaceholderRoute(name)
    is BookDetailDestination.Series -> BookDetailPlaceholderRoute(name)
    is BookDetailDestination.Genre -> BookDetailPlaceholderRoute(name)
    is BookDetailDestination.Tag -> BookDetailPlaceholderRoute(name)
    is BookDetailDestination.CheckIn -> BookDetailPlaceholderRoute(titles.checkIn)
    BookDetailDestination.Settings -> BookDetailPlaceholderRoute(titles.settings)
}

/** Sends this destination to the shell. A link no app can open is ignored. */
internal fun BookDetailDestination.dispatch(
    titles: PlaceholderTitles,
    push: (NavKey) -> Unit,
    openReader: (bookUuid: String, startCfi: String?) -> Unit,
    openPlayer: (bookUuid: String, bookFileId: Long?, positionSeconds: Double?) -> Unit,
    openUri: (url: String) -> Unit,
) {
    when (this) {
        is BookDetailDestination.Reader -> openReader(bookUuid, startCfi)
        is BookDetailDestination.Player -> openPlayer(bookUuid, bookFileId, positionSeconds)
        is BookDetailDestination.ExternalUrl -> runCatching { openUri(url) }
        else -> shellRoute(titles)?.let(push)
    }
}
