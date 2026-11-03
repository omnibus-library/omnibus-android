package com.omnibus.omnibus.feature.bookdetail.overview.series

import androidx.compose.runtime.Immutable
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.data.series.seriesNumber

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
