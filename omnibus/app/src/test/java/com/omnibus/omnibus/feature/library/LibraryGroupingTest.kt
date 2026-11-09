package com.omnibus.omnibus.feature.library

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.SeriesBooks
import com.omnibus.omnibus.data.library.randomBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/** Books with ids equal to their positions. A code of 0 is a book with no series, and n is a book in series n. */
private fun booksOf(codes: List<Int>): List<Book> = codes.mapIndexed { index, code ->
    randomBook().copy(id = index.toLong(), seriesId = code.takeIf { it > 0 }?.toLong())
}

/** The key of an entry: its book id, or its series id. */
private fun LibraryEntry.key(): String = when (this) {
    is LibraryEntry.Single -> "book-${book.id}"
    is LibraryEntry.Series -> "series-${group.seriesId}"
}

/** The entries' keys worked out from [books] alone: each series at its first book, every other book in place. */
private fun expectedKeys(books: List<Book>): List<String> {
    val seen = mutableSetOf<Long>()
    return books.mapNotNull { book ->
        val series = book.seriesId
        when {
            series == null -> "book-${book.id}"
            seen.add(series) -> "series-$series"
            else -> null
        }
    }
}

/** A book in series [seriesId] at [index], with its own id. */
private fun seriesBook(id: Long, index: String?, seriesId: Long = 7L, series: String? = "Dune Chronicles"): Book =
    randomBook().copy(id = id, seriesId = seriesId, seriesIndex = index, series = series)

/** The series read and the loaded books of one series: the read holds three books, and the loaded list repeats one of them. */
private data class ReadAndLoaded(val read: SeriesBooks, val loaded: List<Book>)

private fun readAndLoaded(): ReadAndLoaded {
    val read = SeriesBooks(
        name = "Dune Chronicles",
        books = listOf(
            seriesBook(1L, "2"),
            seriesBook(2L, "10"),
            seriesBook(3L, "1.5").copy(creators = listOf(Creator(name = "Frank Herbert", role = null, fileAs = null, id = null))),
        ),
    )
    // A book with no index and one with a non-numeric index, as the loaded page holds them.
    val loaded = listOf(read.books[0].copy(), seriesBook(4L, null), seriesBook(5L, "part"))
    return ReadAndLoaded(read, loaded)
}

/** The ebooks a category filter loads, and the audiobook of the same series that only the read holds. */
private data class CategoryFixture(val ebook: Book, val audiobook: Book, val seriesBooks: Map<Long, SeriesBooks>)

private fun categoryFixture(): CategoryFixture {
    val ebook = seriesBook(id = 1L, index = "1").copy(formats = listOf("epub"))
    val audiobook = seriesBook(id = 2L, index = "2").copy(formats = listOf("m4b"))
    val otherSeries = seriesBook(id = 3L, index = "1", seriesId = 9L).copy(formats = listOf("m4b"))
    val seriesBooks = mapOf(
        7L to SeriesBooks("Dune Chronicles", listOf(ebook.copy(), audiobook)),
        9L to SeriesBooks("Expanse", listOf(otherSeries)),
    )
    return CategoryFixture(ebook, audiobook, seriesBooks)
}

/** Stacks and unfolded series, with singles around them. */
private data class GridFixture(
    val a: Book,
    val b: Book,
    val folded: SeriesGroup,
    val open: SeriesGroup,
    val openSolo: SeriesGroup,
) {
    val entries: List<LibraryEntry> = listOf(
        LibraryEntry.Single(a),
        LibraryEntry.Series(folded),
        LibraryEntry.Single(b),
        LibraryEntry.Series(open),
        LibraryEntry.Series(openSolo),
    )
}

private fun gridFixture(): GridFixture = GridFixture(
    a = randomBook().copy(id = 1L, seriesId = null),
    b = randomBook().copy(id = 2L, seriesId = null),
    folded = SeriesGroup(7L, "Folded", listOf(randomBook().copy(id = 30L, seriesId = 7L))),
    open = SeriesGroup(
        8L,
        "Open",
        listOf(
            randomBook().copy(id = 41L, seriesId = 8L),
            randomBook().copy(id = 42L, seriesId = 8L),
            randomBook().copy(id = 43L, seriesId = 8L),
        ),
    ),
    openSolo = SeriesGroup(9L, "Solo open", listOf(randomBook().copy(id = 51L, seriesId = 9L))),
)

class LibraryGroupingTest : DescribeSpec({

    describe("grouping the loaded books") {

        it("#235 TC-1: puts each series at its first loaded book, as one entry") {
            checkAll(Arb.list(Arb.int(0..4), 0..20)) { codes ->
                val books = booksOf(codes)

                libraryEntries(books, emptyMap()).map { it.key() } shouldBe expectedKeys(books)
            }
        }

        it("#235 TC-1: keeps the books without a series in their relative order") {
            checkAll(Arb.list(Arb.int(0..4), 0..20)) { codes ->
                val books = booksOf(codes)

                libraryEntries(books, emptyMap()).filterIsInstance<LibraryEntry.Single>().map { it.book.id } shouldBe
                    books.filter { it.seriesId == null }.map { it.id }
            }
        }

        it("#235 TC-1: places each series at the first of its books in the reversed list") {
            checkAll(Arb.list(Arb.int(0..4), 0..20)) { codes ->
                val books = booksOf(codes).reversed()

                libraryEntries(books, emptyMap()).map { it.key() } shouldBe expectedKeys(books)
            }
        }

        context("when a book has a series name but no series id") {
            it("#235 TC-2: keeps it a single entry, apart from the series with that name") {
                val unlinked = randomBook().copy(id = 1L, seriesId = null, series = "Dune Chronicles", seriesIndex = "1")
                val linked = seriesBook(id = 2L, index = "2")

                libraryEntries(listOf(unlinked, linked), emptyMap()) shouldBe listOf(
                    LibraryEntry.Single(unlinked),
                    LibraryEntry.Series(SeriesGroup(7L, "Dune Chronicles", listOf(linked))),
                )
            }
        }
    }

    describe("the books of a series entry with a series read") {

        it("#235 TC-3: holds the de-duplicated union of the read and the loaded books, in series order") {
            val (read, loaded) = readAndLoaded()

            val group = libraryEntries(loaded, mapOf(7L to read)).single().shouldBeTypeOf<LibraryEntry.Series>().group

            group.books.map { it.id } shouldBe listOf(3L, 1L, 2L, 4L, 5L)
        }

        it("#235 TC-3: counts the books the entry holds") {
            val (read, loaded) = readAndLoaded()

            val group = libraryEntries(loaded, mapOf(7L to read)).single().shouldBeTypeOf<LibraryEntry.Series>().group

            group.count shouldBe 5
        }

        it("#235 TC-3: takes the first book in series order as the top") {
            val (read, loaded) = readAndLoaded()

            val group = libraryEntries(loaded, mapOf(7L to read)).single().shouldBeTypeOf<LibraryEntry.Series>().group

            group.top.id shouldBe 3L
        }

        it("#235 TC-3: takes the author from the top book") {
            val (read, loaded) = readAndLoaded()

            val group = libraryEntries(loaded, mapOf(7L to read)).single().shouldBeTypeOf<LibraryEntry.Series>().group

            // Book 3 has the lowest index, so it is the top, and its author is the series' author.
            group.author shouldBe "Frank Herbert"
        }

        it("#235 TC-3: is unchanged by a later page of books already in the series") {
            val (read, loaded) = readAndLoaded()
            val first = libraryEntries(loaded, mapOf(7L to read)).single()

            val later = libraryEntries(loaded + listOf(read.books[1].copy(), read.books[2].copy()), mapOf(7L to read))

            later.single() shouldBe first
        }
    }

    describe("the books of a series entry without a series read") {

        context("when the series has no read") {
            it("#235 TC-4: is built from the loaded books alone, named by the top loaded book's series") {
                // The book first in the loaded list has another series name, so the name must come from the top book.
                val first = seriesBook(id = 10L, index = "2", series = "Stale")
                val top = seriesBook(id = 11L, index = "1", series = "Dune")

                libraryEntries(listOf(first, top), emptyMap()) shouldBe listOf(
                    LibraryEntry.Series(SeriesGroup(7L, "Dune", listOf(top, first))),
                )
            }
        }
    }

    describe("a series entry under a category filter") {

        it("#235 TC-5: shows a series with a loaded book, with the books the filter hides") {
            val (ebook, audiobook, seriesBooks) = categoryFixture()

            libraryEntries(listOf(ebook), seriesBooks) shouldBe listOf(
                LibraryEntry.Series(SeriesGroup(7L, "Dune Chronicles", listOf(ebook, audiobook))),
            )
        }

        it("#235 TC-5: gives no entry to a series that has no loaded book") {
            val (ebook, _, seriesBooks) = categoryFixture()

            libraryEntries(listOf(ebook), seriesBooks).none {
                it is LibraryEntry.Series && it.group.seriesId == 9L
            } shouldBe true
        }
    }

    describe("the grid's items") {

        it("#235 TC-33: lists the items in order, with unfolded series expanded in place") {
            val f = gridFixture()

            // 99 is unfolded but listed nowhere, so it adds nothing.
            libraryGridItems(f.entries, unfolded = setOf(8L, 9L, 99L)) shouldBe listOf(
                LibraryGridItem.Single(f.a),
                LibraryGridItem.Stack(f.folded),
                LibraryGridItem.Single(f.b),
                LibraryGridItem.Summary(f.open),
                LibraryGridItem.Member(f.open, f.open.books[0]),
                LibraryGridItem.Member(f.open, f.open.books[1]),
                LibraryGridItem.Member(f.open, f.open.books[2]),
                LibraryGridItem.Summary(f.openSolo),
                LibraryGridItem.Member(f.openSolo, f.openSolo.books[0]),
            )
        }

        it("#235 TC-33: gives every item a unique key") {
            val items = libraryGridItems(gridFixture().entries, unfolded = setOf(8L, 9L, 99L))

            items.map { it.key }.distinct().size shouldBe items.size
        }

        context("when no series is unfolded") {
            it("#235 TC-33: makes every series one stack, in place") {
                val f = gridFixture()

                libraryGridItems(f.entries, unfolded = emptySet()) shouldBe listOf(
                    LibraryGridItem.Single(f.a),
                    LibraryGridItem.Stack(f.folded),
                    LibraryGridItem.Single(f.b),
                    LibraryGridItem.Stack(f.open),
                    LibraryGridItem.Stack(f.openSolo),
                )
            }
        }
    }
})
