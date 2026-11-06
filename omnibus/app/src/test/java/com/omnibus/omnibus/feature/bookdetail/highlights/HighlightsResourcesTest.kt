package com.omnibus.omnibus.feature.bookdetail.highlights

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.Locale

class HighlightsResourcesTest : DescribeSpec({

    val valuesDir = File(System.getProperty("user.dir"), "src/main/res/values")
    val entryRegex = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    /** The string names and their texts in [file], with the surrounding quotes and XML's `\'` escapes undone. */
    fun stringsIn(file: File): Map<String, String> =
        entryRegex.findAll(file.readText()).associate { match ->
            match.groupValues[1] to match.groupValues[2].removeSurrounding("\"").replace("\\'", "'")
        }

    describe("book detail highlights resources") {

        it("TC-34: strings are exact, prefixed, and defined in no other file") {
            val highlightsFile = File(valuesDir, "strings_book_detail_highlights.xml")
            highlightsFile.exists() shouldBe true
            val strings = stringsIn(highlightsFile)

            strings shouldContainExactly mapOf(
                "book_detail_highlights_heading" to "Highlights",
                "book_detail_highlights_empty" to "Highlight while you read to keep passages here.",
                "book_detail_highlights_no_text" to "A passage kept on another device, with no text to show here.",
                "book_detail_highlights_chapter" to "Chapter %1\$d",
                "book_detail_highlights_failed" to "Highlights couldn't be loaded.",
                "book_detail_highlights_retry" to "Try again",
                "book_detail_highlights_color_amber" to "Amber highlight",
                "book_detail_highlights_color_green" to "Green highlight",
                "book_detail_highlights_color_blue" to "Blue highlight",
                "book_detail_highlights_color_rose" to "Rose highlight",
                "book_detail_highlights_color_violet" to "Violet highlight",
                "book_detail_highlights_actions" to "Highlight actions",
                "book_detail_highlights_quote_card" to "Make a quote card",
                "book_detail_highlights_share" to "Share",
            )
            strings.keys.all { it.startsWith("book_detail_highlights_") } shouldBe true
            String.format(Locale.US, strings.getValue("book_detail_highlights_chapter"), 7) shouldBe "Chapter 7"

            val otherNames = valuesDir.listFiles { file -> file.name.endsWith(".xml") && file != highlightsFile }!!
                .flatMap { stringsIn(it).keys }
            otherNames.filter { it in strings.keys } shouldBe emptyList()
        }
    }
})
