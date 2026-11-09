package com.omnibus.omnibus.feature.library

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.SeriesBooks
import com.omnibus.omnibus.data.series.inSeriesOrderBy

/** A series' books in series order, with the name to show. Never empty. */
data class SeriesGroup(val seriesId: Long, val name: String, val books: List<Book>) {

    /** How many books the series holds in the library. */
    val count: Int get() = books.size

    /** The series' first book, which its stack shows on top. */
    val top: Book get() = books.first()

    /** The author line for the series, taken from its first book. */
    val author: String get() = top.authorDisplay
}

/** One slot of the grouped Library: a book on its own, or a series. */
sealed interface LibraryEntry {

    data class Single(val book: Book) : LibraryEntry

    data class Series(val group: SeriesGroup) : LibraryEntry
}

/** One cell of the grid. A series is a [Stack] when folded, or a [Summary] followed by its [Member]s when unfolded. */
sealed interface LibraryGridItem {

    /** A key unique within one grid. */
    val key: Any

    data class Single(val book: Book) : LibraryGridItem {
        override val key: Any get() = book.id
    }

    data class Stack(val group: SeriesGroup) : LibraryGridItem {
        override val key: Any get() = "stack-${group.seriesId}"
    }

    data class Summary(val group: SeriesGroup) : LibraryGridItem {
        override val key: Any get() = "series-${group.seriesId}"
    }

    data class Member(val group: SeriesGroup, val book: Book) : LibraryGridItem {
        override val key: Any get() = "member-${group.seriesId}-${book.id}"
    }
}

/**
 * The grouped Library's entries from the [books] loaded so far, in their order. Each series sits at the position of
 * its first loaded book, and its other loaded books leave their own slots. Its books are the read's books and the
 * loaded ones, de-duplicated by id and in series order. Its name is the read's, else the first loaded book's series.
 *
 * @param seriesBooks Successful series reads by series id. A series without one is built from the loaded books.
 */
fun libraryEntries(books: List<Book>, seriesBooks: Map<Long, SeriesBooks>): List<LibraryEntry> {
    val loaded = books.filter { it.seriesId != null }.groupBy { it.seriesId!! }
    val placed = HashSet<Long>()
    return books.mapNotNull { book ->
        val id = book.seriesId ?: return@mapNotNull LibraryEntry.Single(book)
        if (!placed.add(id)) return@mapNotNull null
        val read = seriesBooks[id]
        val ownLoaded = loaded.getValue(id)
        // The read's books come first, so its copy of a book wins the de-duplication.
        val members = (read?.books.orEmpty() + ownLoaded).distinctBy { it.id }.inSeriesOrderBy { it.seriesIndex }
        val name = read?.name?.takeIf { it.isNotBlank() }
            ?: ownLoaded.inSeriesOrderBy { it.seriesIndex }.firstNotNullOfOrNull { it.series?.takeIf(String::isNotBlank) }
            ?: members.first().displayTitle
        LibraryEntry.Series(SeriesGroup(seriesId = id, name = name, books = members))
    }
}

/**
 * The grid's cells for [entries]. A series in [unfolded] becomes its summary and then one member per book, at its
 * entry's position. Every other series is one stack.
 */
fun libraryGridItems(entries: List<LibraryEntry>, unfolded: Set<Long>): List<LibraryGridItem> = entries.flatMap { entry ->
    when (entry) {
        is LibraryEntry.Single -> listOf(LibraryGridItem.Single(entry.book))
        is LibraryEntry.Series -> {
            val group = entry.group
            if (group.seriesId in unfolded) {
                listOf(LibraryGridItem.Summary(group)) + group.books.map { LibraryGridItem.Member(group, it) }
            } else {
                listOf(LibraryGridItem.Stack(group))
            }
        }
    }
}
