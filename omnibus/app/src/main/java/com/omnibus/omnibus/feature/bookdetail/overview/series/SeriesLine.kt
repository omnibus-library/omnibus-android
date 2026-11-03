package com.omnibus.omnibus.feature.bookdetail.overview.series

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.data.series.seriesNumber
import com.omnibus.omnibus.feature.bookdetail.BookDetailAnchor
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext

/**
 * The stage series line as a link: the series' book count and what a tap does.
 *
 * @property total The number of books in the series.
 * @property onClick Scrolls to the Series section.
 */
@Immutable
data class SeriesLineState(val total: Int, val onClick: () -> Unit)

/** How the stage series line words the book's number. */
internal sealed interface SeriesLineWording {

    /** No number: the series name alone. */
    data object NameOnly : SeriesLineWording

    /** The number without a total, e.g. "Dune, book 7". */
    data class WithIndex(val index: String) : SeriesLineWording

    /** The number and the total, e.g. "Dune, book 2 of 5". */
    data class WithTotal(val index: String, val total: Int) : SeriesLineWording
}

/**
 * The wording for a book numbered [index] in a series of [total] books. The total is left out when
 * the number is not numeric or is greater than the total (iOS `DetailRead.seriesLabel`).
 */
internal fun seriesLineWording(index: String?, total: Int?): SeriesLineWording {
    val trimmed = index?.trim()?.takeIf { it.isNotEmpty() } ?: return SeriesLineWording.NameOnly
    val number = seriesNumber(trimmed)
    return if (total == null || total <= 1 || number == null || number > total) {
        SeriesLineWording.WithIndex(trimmed)
    } else {
        SeriesLineWording.WithTotal(trimmed, total)
    }
}

/**
 * The link state for the stage line. It is non-null exactly when the Series section would show
 * ([seriesFanModel] is non-null), and then carries the member count.
 */
internal fun seriesLineState(book: Book, members: List<SeriesMember>?, onClick: () -> Unit): SeriesLineState? =
    if (members == null || seriesFanModel(book, members) == null) null else SeriesLineState(members.size, onClick)

/**
 * The stage line's link state, or null (a plain line) unless the Series section would show. A book without a series
 * id, or with a blank series name, never reaches [LocalSeriesSource], so it never reaches Hilt.
 */
@Composable
internal fun rememberSeriesLine(context: BookDetailSectionContext): SeriesLineState? {
    val book = context.book
    val seriesId = book.seriesId ?: return null
    if (book.series.isNullOrBlank()) return null
    val members = LocalSeriesSource.current.members(context, seriesId)
    val tabs = context.tabs
    return remember(book, members, tabs) {
        seriesLineState(book, members) { tabs.scrollTo(BookDetailAnchor.Series) }
    }
}
