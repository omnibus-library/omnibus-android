package com.omnibus.omnibus.feature.bookdetail.finish

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

class FinishResourcesTest : DescribeSpec({

    describe("finish card resources") {

        it("TC-26: every name is prefixed, the set is complete and the plurals are well formed") {
            val file = File(System.getProperty("user.dir"), "src/main/res/values/strings_book_detail_finish.xml")
            file.exists() shouldBe true
            val text = file.readText()
            val names = Regex("""<(?:string|plurals) name="([^"]+)"""").findAll(text).map { it.groupValues[1] }.toList()

            names.toSet() shouldBe setOf(
                "book_detail_finish_title",
                "book_detail_finish_days",
                "book_detail_finish_sessions",
                "book_detail_finish_highlights",
                "book_detail_finish_review_title",
                "book_detail_finish_review_detail",
                "book_detail_finish_review_action",
                "book_detail_finish_next_title",
                "book_detail_finish_next_detail",
                "book_detail_finish_next_detail_unnumbered",
                "book_detail_finish_next_action",
            )
            names.size shouldBe 11
            names.all { it.startsWith("book_detail_finish_") } shouldBe true

            for (plural in listOf("days", "sessions", "highlights")) {
                val body = text.substringAfter("<plurals name=\"book_detail_finish_$plural\">").substringBefore("</plurals>")
                body.contains("quantity=\"one\"") shouldBe true
                body.contains("quantity=\"other\"") shouldBe true
                body.contains("%1\$d") shouldBe true
            }

            val title = text.lines().single { it.contains("name=\"book_detail_finish_title\"") }
            title.contains("%1\$s") shouldBe true
            val nextTitle = text.lines().single { it.contains("name=\"book_detail_finish_next_title\"") }
            nextTitle.contains("%1\$s") shouldBe true
            val nextDetail = text.lines().single { it.contains("name=\"book_detail_finish_next_detail\"") }
            nextDetail.contains("%1\$s") shouldBe true
            nextDetail.contains("%2\$s") shouldBe true
        }
    }
})
