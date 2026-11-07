package com.omnibus.omnibus.feature.bookdetail.files.details

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Locale
import java.util.TimeZone

private val WORDING = DetailsWording(
    seriesBook = { name, index -> "$name, book $index" },
    unknownLanguage = "Unknown",
)

/** A book with its file and none of the details the list reads. */
private fun bareBook(): Book = randomBook().copy(
    publisher = null,
    publishedDate = null,
    series = null,
    seriesIndex = null,
    language = null,
    isbn13 = null,
    addedAt = null,
)

private fun items(book: Book) = bookDetailItems(book, Locale.US, TimeZone.getTimeZone("UTC"), WORDING)

class BookDetailRowsTest : DescribeSpec({

    describe("which rows the Details list shows") {

        context("when the book has every detail") {
            it("#114 AC1/AC5: shows all seven rows in order, each with its own value") {
                val rows = items(
                    bareBook().copy(
                        filename = "hound.epub",
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
        }

        context("when the book has no details") {
            it("#114 AC1/AC5: shows only the File row") {
                val book = bareBook()

                items(book) shouldBe listOf(DetailItem(DetailRow.File, book.filename))
            }
        }

        context("when the details are blank") {
            it("#114 AC1: shows no rows") {
                val book = bareBook().copy(
                    filename = "  ",
                    publisher = "  ",
                    publishedDate = "",
                    series = " ",
                    seriesIndex = "3",
                    language = " ",
                    isbn13 = "",
                    addedAt = "   ",
                )

                items(book) shouldBe emptyList()
            }
        }

        context("when a value has space around it") {
            it("#114 AC1: trims it") {
                items(bareBook().copy(publisher = "  Tor Books \n")).first().value shouldBe "Tor Books"
            }
        }
    }

    describe("the series wording") {

        fun seriesValue(series: String?, index: String?) =
            items(bareBook().copy(series = series, seriesIndex = index)).firstOrNull { it.row == DetailRow.Series }?.value

        context("when the number is blank") {
            it("#114 AC5: reads as the name alone") {
                val series = randomString()

                seriesValue(series, "  ") shouldBe series
            }
        }

        context("when the series has no name") {
            it("#114 AC5: makes no row") {
                seriesValue(null, "2") shouldBe null
            }
        }
    }
})
