package com.omnibus.omnibus.feature.bookdetail.files.details

import com.omnibus.omnibus.data.library.Book
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Locale
import java.util.TimeZone

class BookDetailRowsTest : DescribeSpec({

    val utc = TimeZone.getTimeZone("UTC")
    val wording = DetailsWording(
        seriesBook = { name, index -> "$name, book $index" },
        unknownLanguage = "Unknown",
    )

    fun book(
        filename: String = "hound.epub",
        series: String? = null,
        seriesIndex: String? = null,
        publisher: String? = null,
        publishedDate: String? = null,
        language: String? = null,
        isbn13: String? = null,
        addedAt: String? = null,
    ) = Book(
        id = 1,
        uniqueIdentifier = "uuid-1",
        filename = filename,
        title = "The Hound of the Baskervilles",
        creators = emptyList(),
        coverUrl = null,
        accent = null,
        formats = listOf("epub"),
        series = series,
        seriesIndex = seriesIndex,
        hasPhysical = false,
        addedAt = addedAt,
        lastInteractedAt = null,
        bookFiles = emptyList(),
        publisher = publisher,
        publishedDate = publishedDate,
        language = language,
        isbn13 = isbn13,
    )

    fun items(book: Book) = bookDetailItems(book, Locale.US, utc, wording)

    describe("which rows the Details list shows") {

        it("#114 AC1/AC5: a book with every detail shows all seven rows in order") {
            val rows = items(
                book(
                    publisher = "Spencer Blackett",
                    publishedDate = "1890-02-01",
                    series = "Sherlock Holmes",
                    seriesIndex = "2",
                    language = "en",
                    isbn13 = "9780141034357",
                    addedAt = "2026-10-02T09:30:00Z",
                ),
            )

            rows.map { it.row } shouldBe DetailRow.entries.toList()
            rows.map { it.value } shouldBe listOf(
                "Spencer Blackett",
                "February 1, 1890",
                "Sherlock Holmes, book 2",
                "English",
                "9780141034357",
                "October 2, 2026",
                "hound.epub",
            )
        }

        it("#114 AC1/AC5: a book with no details but its file shows only the File row") {
            items(book()) shouldBe listOf(DetailItem(DetailRow.File, "hound.epub"))
        }

        it("#114 AC1/AC5: each row appears only when its own detail is there") {
            items(book(publisher = "Tor")).map { it.row } shouldBe listOf(DetailRow.Publisher, DetailRow.File)
            items(book(isbn13 = "123")).map { it.row } shouldBe listOf(DetailRow.Isbn, DetailRow.File)
            items(book(language = "fr")).map { it.row } shouldBe listOf(DetailRow.Language, DetailRow.File)
            items(book(addedAt = "2026-10-02")).map { it.row } shouldBe listOf(DetailRow.Added, DetailRow.File)
            items(book(publishedDate = "1925")).map { it.row } shouldBe listOf(DetailRow.Published, DetailRow.File)
        }

        it("#114 AC1: blank details count as missing") {
            val rows = items(
                book(
                    filename = "  ",
                    publisher = "  ",
                    publishedDate = "",
                    series = " ",
                    seriesIndex = "3",
                    language = " ",
                    isbn13 = "",
                    addedAt = "   ",
                ),
            )

            rows shouldBe emptyList()
        }

        it("#114 AC1: values are trimmed") {
            items(book(publisher = "  Tor Books \n")).first { it.row == DetailRow.Publisher }.value shouldBe "Tor Books"
        }
    }

    describe("the series wording") {

        it("#114 AC5: a numbered series reads name, book number") {
            items(book(series = "Sherlock Holmes", seriesIndex = "2")).first { it.row == DetailRow.Series }.value shouldBe
                "Sherlock Holmes, book 2"
        }

        it("#114 AC5: a fractional number is kept as the server sent it") {
            items(book(series = "Dune", seriesIndex = "1.5")).first { it.row == DetailRow.Series }.value shouldBe
                "Dune, book 1.5"
        }

        it("#114 AC5: a series with no number reads as its name alone") {
            items(book(series = "Sherlock Holmes")).first { it.row == DetailRow.Series }.value shouldBe "Sherlock Holmes"
            items(book(series = "Sherlock Holmes", seriesIndex = "  ")).first { it.row == DetailRow.Series }.value shouldBe
                "Sherlock Holmes"
        }

        it("#114 AC5: a number with no series name makes no Series row") {
            items(book(seriesIndex = "2")).none { it.row == DetailRow.Series } shouldBe true
        }
    }
})
