package com.omnibus.omnibus.feature.series

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.series.seriesNumber
import kotlin.math.floor

/** One row of the Series Details list. */
sealed interface SeriesEntry {

    /** A book the library holds. */
    data class BookEntry(val book: Book) : SeriesEntry

    /** Whole numbers [first]..[last] that no book holds; one number when they are equal. */
    data class Missing(val first: Long, val last: Long) : SeriesEntry
}

/** The first series number a placeholder can stand for. */
internal const val FIRST_FILLED_NUMBER = 1L

/** The longest run of missing numbers that gets one placeholder per number; a longer run gets one range row. */
internal const val LONGEST_SEPARATE_GAP = 3L

/**
 * [books] in their given order, with a placeholder for each run of whole numbers no book holds, from
 * [FIRST_FILLED_NUMBER] up to the highest whole number held. A placeholder goes before the first book whose number is
 * above it. Books whose number is missing or not numeric never trigger one, so they come after them.
 */
fun seriesEntries(books: List<Book>): List<SeriesEntry> {
    val gaps = ArrayDeque<SeriesEntry.Missing>()
    var expected = FIRST_FILLED_NUMBER
    for (held in books.mapNotNull { wholeNumber(it.seriesIndex) }.toSortedSet()) {
        if (held > expected) gaps.addAll(missingRows(expected, held - 1))
        expected = held + 1
    }
    return buildList {
        for (book in books) {
            val value = seriesNumber(book.seriesIndex)
            if (value != null) {
                while (gaps.isNotEmpty() && gaps.first().first < value) add(gaps.removeFirst())
            }
            add(SeriesEntry.BookEntry(book))
        }
        addAll(gaps) // only when the server's order was not ascending
    }
}

/** One row per number for a short run of missing numbers, one range row for a long one. */
private fun missingRows(first: Long, last: Long): List<SeriesEntry.Missing> =
    if (last - first + 1 <= LONGEST_SEPARATE_GAP) {
        (first..last).map { SeriesEntry.Missing(it, it) }
    } else {
        listOf(SeriesEntry.Missing(first, last))
    }

/** The whole number of at least [FIRST_FILLED_NUMBER] that [index] holds ("3", "3.0"), or null. */
internal fun wholeNumber(index: String?): Long? {
    val value = seriesNumber(index) ?: return null
    if (value < FIRST_FILLED_NUMBER || value != floor(value) || value >= Long.MAX_VALUE.toDouble()) return null
    return value.toLong()
}

/** The count line's author: the first creator with a non-blank name on the first book, or null. */
fun seriesCountAuthor(books: List<Book>): String? =
    books.firstOrNull()?.creators?.firstNotNullOfOrNull { it.name.trim().takeIf(String::isNotEmpty) }
