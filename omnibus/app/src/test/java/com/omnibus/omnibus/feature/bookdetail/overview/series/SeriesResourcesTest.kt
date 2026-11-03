package com.omnibus.omnibus.feature.bookdetail.overview.series

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.Locale

class SeriesResourcesTest : DescribeSpec({

    val valuesDir = File(System.getProperty("user.dir"), "src/main/res/values")
    val seriesFile = File(valuesDir, "strings_book_detail_series.xml")
    val entryRegex = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    /** The string names and their texts in [file], with XML's `\'` escapes undone and one pair of quotes removed. */
    fun stringsIn(file: File): Map<String, String> =
        entryRegex.findAll(file.readText()).associate { match ->
            match.groupValues[1] to match.groupValues[2].replace("\\'", "'").removeSurrounding("\"")
        }

    describe("book detail series resources") {

        it("#104 TC-62: the series strings read as designed") {
            stringsIn(seriesFile) shouldContainExactly mapOf(
                "book_detail_series_heading" to "%1\$s series",
                "book_detail_series_page" to "Series page",
                "book_detail_series_page_click_label" to "Open the series page",
                "book_detail_series_book" to "Book %1\$s",
                "book_detail_series_this_book" to "This book",
                "book_detail_series_up_next" to "Up next",
                "book_detail_series_meta_separator" to " · ",
                "book_detail_series_card_description" to "%1\$s, %2\$s",
                "book_detail_series_spread_click_label" to "Show the whole series",
                "book_detail_series_gather_click_label" to "Gather the series",
                "book_detail_series_open_click_label" to "Open book",
                "book_detail_series_line_of_total" to "%1\$s, book %2\$s of %3\$d",
                "book_detail_series_line_click_label" to "Go to the series",
            )
        }

        it("#104 TC-63: names are prefixed and live only in the series file") {
            val strings = stringsIn(seriesFile)
            strings.keys.all { it.startsWith("book_detail_series_") } shouldBe true

            val others = valuesDir.listFiles { file -> file.name.endsWith(".xml") && file != seriesFile }!!
            val otherNames = others.flatMap { stringsIn(it).keys }
            otherNames.filter { it in strings.keys } shouldBe emptyList()

            val existing = stringsIn(File(valuesDir, "strings_book_detail.xml"))
            existing["book_detail_series_line"] shouldBe "%1\$s, book %2\$s"
            existing["book_detail_series_description"] shouldBe "Series: %1\$s"
        }

        it("#104 TC-64: formatted strings give the ticket wording") {
            val strings = stringsIn(seriesFile)
            fun format(name: String, vararg args: Any) = String.format(Locale.ROOT, strings.getValue(name), *args)

            val book = format("book_detail_series_book", "2")
            val thisBook = strings.getValue("book_detail_series_this_book")
            val separator = strings.getValue("book_detail_series_meta_separator")

            format("book_detail_series_heading", "Dune") shouldBe "Dune series"
            "$book$separator$thisBook" shouldBe "Book 2 · This book"
            "${format("book_detail_series_book", "3")}$separator${strings.getValue("book_detail_series_up_next")}" shouldBe
                "Book 3 · Up next"
            format("book_detail_series_line_of_total", "Dune", "2", 5) shouldBe "Dune, book 2 of 5"
            format("book_detail_series_card_description", "Title", "$book$separator$thisBook") shouldBe
                "Title, Book 2 · This book"
        }
    }
})
