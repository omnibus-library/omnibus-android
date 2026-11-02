package com.omnibus.omnibus.feature.bookdetail.status

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File

class ReadStatusResourcesTest : DescribeSpec({

    val valuesDir = File(System.getProperty("user.dir"), "src/main/res/values")
    val entryRegex = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    /** The string names and their texts in [file], with XML's `\'` escapes undone. */
    fun stringsIn(file: File): Map<String, String> =
        entryRegex.findAll(file.readText()).associate { match ->
            match.groupValues[1] to match.groupValues[2].replace("\\'", "'")
        }

    describe("book detail status resources") {

        it("TC-79: the eleven strings exist with their exact text and placeholders, under their own names") {
            val statusFile = File(valuesDir, "strings_book_detail_status.xml")
            statusFile.exists() shouldBe true
            val strings = stringsIn(statusFile)

            strings shouldContainExactly mapOf(
                "book_detail_status_unknown" to "Reading status",
                "book_detail_status_unread" to "Unread",
                "book_detail_status_reading" to "Reading",
                "book_detail_status_finished" to "Finished",
                "book_detail_status_cd" to "Read status: %1\$s",
                "book_detail_status_cd_unknown" to "Read status",
                "book_detail_status_menu_label" to "Read status",
                "book_detail_status_open_menu" to "Change read status",
                "book_detail_status_finished_on_cd" to "Finished, %1\$s",
                "book_detail_status_save_failed" to "Your read status wasn't saved.",
                "book_detail_status_save_failed_offline" to "No connection. Your read status wasn't saved.",
            )
            strings.keys.all { it.startsWith("book_detail_status_") } shouldBe true

            val otherNames = valuesDir.listFiles { file -> file.name.endsWith(".xml") && file != statusFile }!!
                .flatMap { stringsIn(it).keys }
            otherNames.filter { it in strings.keys } shouldBe emptyList()
        }
    }
})
