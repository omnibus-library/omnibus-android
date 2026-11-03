package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.suggestions.BookSuggestion
import com.omnibus.omnibus.ui.components.CoverIdentity
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** How long to wait before asking again while the server is still searching. */
internal const val SUGGESTIONS_POLL_INTERVAL_MILLIS = 4_000L

/** How many times the section asks again while the server is still searching, after the first read. */
internal const val SUGGESTIONS_POLL_ATTEMPTS = 5

/** A suggestion the shelf can show: a safe Hardcover link, its cover path and the plate drawn under its cover. */
data class ReadAlike(
    val hardcoverId: Long,
    val hardcoverUrl: String,
    val title: String,
    val author: String,
    val identity: CoverIdentity,
    val coverPath: String?,
)

/**
 * The shelf's items from the server's [items], in their order. Drops suggestions whose link is not a Hardcover page,
 * and any repeat of an earlier `hardcover_id`, because the shelf keys its cards by that id.
 */
internal fun readAlikesOf(items: List<BookSuggestion>): List<ReadAlike> =
    items
        .filter { isHardcoverBookUrl(it.hardcoverUrl) }
        .distinctBy { it.hardcoverId }
        .map {
            ReadAlike(
                hardcoverId = it.hardcoverId,
                hardcoverUrl = it.hardcoverUrl,
                title = it.title,
                author = it.author,
                identity = CoverIdentity(
                    uuid = "hardcover-${it.hardcoverId}",
                    title = it.title,
                    creator = it.author.ifBlank { null },
                    hasCover = false,
                ),
                coverPath = it.coverPath,
            )
        }

/**
 * Whether [url] is an `https` link to Hardcover, on `hardcover.app` or one of its subdomains. Anything else (`http`,
 * `intent:`, `javascript:`, a lookalike such as `hardcover.app.evil.com`) is not opened from the shelf.
 */
internal fun isHardcoverBookUrl(url: String): Boolean {
    val parsed = url.trim().toHttpUrlOrNull() ?: return false
    return parsed.scheme == "https" &&
        (parsed.host == HARDCOVER_HOST || parsed.host.endsWith(".$HARDCOVER_HOST"))
}

/**
 * Whether the section appears for [book]. A wishlist-only book has no copy to read, so it gets no suggestions. A book
 * with no copy at all (no formats, no files, not physical) is treated the same way, because `isWishlisted` is not
 * decoded yet and such a book is in practice a wishlist record. Remove the copy clause once it is.
 */
internal fun showsReadAlikes(book: Book): Boolean =
    !book.isWishlistOnly && (book.formats.isNotEmpty() || book.bookFiles.isNotEmpty() || book.hasPhysical)

private const val HARDCOVER_HOST = "hardcover.app"
