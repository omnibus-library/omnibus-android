package com.omnibus.omnibus.feature.bookdetail.overview.reading

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

class ReadingCardResourcesTest : DescribeSpec({

    val valuesDir = File(System.getProperty("user.dir"), "src/main/res/values")

    describe("reading card resources") {

        it("TC-40: every name is prefixed, the set is complete, and the plurals and the percent are well formed") {
            val file = File(valuesDir, "strings_book_detail_reading.xml")
            file.exists() shouldBe true
            val text = file.readText()
            val names = Regex("""<(?:string|plurals) name="([^"]+)"""").findAll(text).map { it.groupValues[1] }.toList()

            names.toSet() shouldBe setOf(
                "book_detail_reading_title",
                "book_detail_reading_line_no_reading",
                "book_detail_reading_line_wishlist",
                "book_detail_reading_line_history_failed",
                "book_detail_reading_line_paper_copy",
                "book_detail_reading_line_sync_pending",
                "book_detail_reading_line_reading_since",
                "book_detail_reading_line_finished",
                "book_detail_reading_row_started",
                "book_detail_reading_row_time_read",
                "book_detail_reading_row_pace",
                "book_detail_reading_row_last_read",
                "book_detail_reading_row_longest_sitting",
                "book_detail_reading_row_time_left",
                "book_detail_reading_time_read_value",
                "book_detail_reading_sessions",
                "book_detail_reading_pace_value",
                "book_detail_reading_longest_sitting_value",
                "book_detail_reading_time_left_value",
                "book_detail_reading_row_description",
                "book_detail_reading_today",
                "book_detail_reading_yesterday",
                "book_detail_reading_under_a_minute",
            )
            names.size shouldBe 23
            names.all { it.startsWith("book_detail_reading_") } shouldBe true

            val plural = text.substringAfter("<plurals name=\"book_detail_reading_sessions\">").substringBefore("</plurals>")
            plural.contains("quantity=\"one\"") shouldBe true
            plural.contains("quantity=\"other\"") shouldBe true
            plural.contains("%1\$d") shouldBe true

            val pace = text.lines().single { it.contains("name=\"book_detail_reading_pace_value\"") }
            pace.contains("%1\$d%%") shouldBe true
        }
    }
})
