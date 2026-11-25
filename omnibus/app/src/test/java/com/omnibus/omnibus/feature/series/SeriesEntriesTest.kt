package com.omnibus.omnibus.feature.series

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.feature.series.SeriesEntry.BookEntry
import com.omnibus.omnibus.feature.series.SeriesEntry.Missing
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

/** A book with series number [index], and the rest random. */
private fun bookNumbered(index: String?): Book = randomBook().copy(seriesIndex = index)

/**
 * Books in server order: a duplicate "3", a fractional "6.5" inside the gap 4-9, and an unnumbered book last. Books 1,
 * 3 and 10 are held, so 2 is a single missing row and 4 to 9 a range row.
 */
private fun placementBooks(): List<Book> = listOf(
    bookNumbered("1"),
    bookNumbered("3"),
    bookNumbered("3"),
    bookNumbered("6.5"),
    bookNumbered("10"),
    bookNumbered(null),
)

class SeriesEntriesTest : DescribeSpec({

    describe("the placeholder rows of a series") {

        context("when a gap is one to three missing numbers") {

            it("#349 TC-3: has one row per missing number") {
                checkAll(Exhaustive.of(1L, 2L, 3L)) { gap ->
                    val first = bookNumbered("1")
                    val last = bookNumbered("${gap + 2}")

                    seriesEntries(listOf(first, last)) shouldBe
                        listOf<SeriesEntry>(BookEntry(first)) +
                        (2L..gap + 1).map { Missing(it, it) } +
                        BookEntry(last)
                }
            }
        }

        context("when a gap is more than three missing numbers") {

            it("#349 TC-3: has one range row for the whole gap") {
                checkAll(Exhaustive.of(4L, 5L, 6L, 7L, 8L, 9L, 10L)) { gap ->
                    val first = bookNumbered("1")
                    val last = bookNumbered("${gap + 2}")

                    seriesEntries(listOf(first, last)) shouldBe
                        listOf(BookEntry(first), Missing(2, gap + 1), BookEntry(last))
                }
            }
        }

        context("when the highest whole number held is the last book") {

            it("#349 TC-3: adds no row after it") {
                val books = listOf(bookNumbered("1"), bookNumbered("2"))

                seriesEntries(books) shouldBe books.map(::BookEntry)
            }
        }
    }

    describe("the whole numbers a series holds") {

        context("when a number is written with a trailing .0") {

            it("#349 TC-4: counts it as the whole number") {
                val first = bookNumbered("1")
                val third = bookNumbered("3.0")

                seriesEntries(listOf(first, third)) shouldBe listOf(BookEntry(first), Missing(2, 2), BookEntry(third))
            }
        }

        context("when a fractional number sits in a gap") {

            it("#349 TC-4: does not fill the gap") {
                val first = bookNumbered("1")
                val fraction = bookNumbered("2.5")
                val third = bookNumbered("3")

                seriesEntries(listOf(first, fraction, third)) shouldBe
                    listOf(BookEntry(first), Missing(2, 2), BookEntry(fraction), BookEntry(third))
            }
        }

        context("when the series has only fractional, missing or non-numeric numbers") {

            it("#349 TC-4: has no placeholders") {
                checkAll(Exhaustive.of("2.5", "1.5", "0", "abc", null)) { index ->
                    val books = listOf(bookNumbered(index), bookNumbered("4.5"))

                    seriesEntries(books) shouldBe books.map(::BookEntry)
                }
            }
        }
    }

    describe("the order of the entries") {

        it("#349 TC-5: keeps every book in the server's order, duplicates included") {
            val books = placementBooks()

            seriesEntries(books).filterIsInstance<BookEntry>().map { it.book } shouldBe books
        }

        it("#349 TC-5: places each placeholder after every lower-numbered book and before the next higher one") {
            val books = placementBooks()

            seriesEntries(books) shouldBe listOf(
                BookEntry(books[0]),
                Missing(2, 2),
                BookEntry(books[1]),
                BookEntry(books[2]),
                Missing(4, 9),
                BookEntry(books[3]),
                BookEntry(books[4]),
                BookEntry(books[5]),
            )
        }
    }

    describe("the placeholder rows of a series with large numbers") {

        it("#349 TC-6: gives one range row per gap, with no row per number") {
            val first = bookNumbered("1")
            val year = bookNumbered("1984")
            val nextYear = bookNumbered("1991")

            seriesEntries(listOf(first, year, nextYear)) shouldBe listOf(
                BookEntry(first),
                Missing(2, 1983),
                BookEntry(year),
                Missing(1985, 1990),
                BookEntry(nextYear),
            )
        }
    }
})
