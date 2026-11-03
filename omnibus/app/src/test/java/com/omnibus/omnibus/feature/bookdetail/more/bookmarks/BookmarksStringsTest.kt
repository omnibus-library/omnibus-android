package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

class BookmarksStringsTest : DescribeSpec({

    val appDir = File(System.getProperty("user.dir"))
    val bookmarkStrings = File(appDir, "src/main/res/values/strings_book_detail_bookmarks.xml")
    val stringRegex = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    // The raw XML values: the apostrophe is escaped, and each format string has its placeholder.
    val expected = mapOf(
        "book_detail_bookmarks_title" to "Bookmarks",
        "book_detail_bookmarks_close" to "Close",
        "book_detail_bookmarks_empty" to "No bookmarks yet. The ones you place while reading or listening gather here.",
        "book_detail_bookmarks_loading" to "Loading bookmarks",
        "book_detail_bookmarks_error" to "Couldn\\'t load bookmarks.",
        "book_detail_bookmarks_retry" to "Try again",
        "book_detail_bookmarks_saved_position" to "Saved position",
        "book_detail_bookmarks_listening" to "Listening, %1\$s in",
        "book_detail_bookmarks_under_a_minute" to "under a minute",
        "book_detail_bookmarks_chapter" to "Chapter %1\$d",
        "book_detail_bookmarks_percent" to "%1\$d%%",
    )

    describe("bookmarks sheet strings") {

        it("#117 TC-21: every bookmarks string has its exact raw value") {
            val values = stringRegex.findAll(bookmarkStrings.readText())
                .associate { it.groupValues[1] to it.groupValues[2] }
            values shouldBe expected
        }

        it("#117 TC-21: each format string has exactly one placeholder") {
            val formats = listOf(
                "book_detail_bookmarks_listening",
                "book_detail_bookmarks_chapter",
                "book_detail_bookmarks_percent",
            )
            val placeholder = Regex("""%\d+\$[sd]""")
            formats.forEach { name ->
                placeholder.findAll(expected.getValue(name)).count() shouldBe 1
            }
        }
    }
})
