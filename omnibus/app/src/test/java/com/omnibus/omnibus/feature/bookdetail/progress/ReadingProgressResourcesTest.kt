package com.omnibus.omnibus.feature.bookdetail.progress

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.io.File

class ReadingProgressResourcesTest : DescribeSpec({

    val valuesDir = File(System.getProperty("user.dir"), "src/main/res/values")
    val entryRegex = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    /** The string names and their raw texts in [file], as written in the XML. */
    fun rawStringsIn(file: File): Map<String, String> =
        entryRegex.findAll(file.readText()).associate { match -> match.groupValues[1] to match.groupValues[2] }

    /** The string names and their texts in [file], with the surrounding quotes and XML's `\'` escapes undone. */
    fun stringsIn(file: File): Map<String, String> =
        rawStringsIn(file).mapValues { (_, raw) -> raw.removeSurrounding("\"").replace("\\'", "'") }

    describe("book detail progress resources") {

        it("TC-79: strings are exact") {
            val progressFile = File(valuesDir, "strings_book_detail_progress.xml")
            progressFile.exists() shouldBe true
            val strings = stringsIn(progressFile)

            strings shouldContainExactly mapOf(
                "book_detail_progress_flag_chapter" to "Ch. %1\$d",
                "book_detail_progress_percent" to "%1\$d%%",
                "book_detail_progress_left_at_pace" to " · about %1\$s left at your pace",
                "book_detail_progress_left_of" to " · %1\$s left of %2\$s",
                "book_detail_progress_under_a_minute" to "0 m",
                "book_detail_progress_in_progress" to "In progress",
                "book_detail_progress_offline" to "Offline. This book isn't downloaded.",
                "book_detail_progress_reading_cd" to "Reading progress",
                "book_detail_progress_listening_cd" to "Listening progress",
                "book_detail_progress_percent_state" to "%1\$d percent",
            )
            strings.keys.all { it.startsWith("book_detail_progress_") } shouldBe true

            val raw = rawStringsIn(progressFile)
            raw.getValue("book_detail_progress_left_at_pace") shouldStartWith "\" · "
            raw.getValue("book_detail_progress_left_of") shouldStartWith "\" · "

            val otherNames = valuesDir.listFiles { file -> file.name.endsWith(".xml") && file != progressFile }!!
                .flatMap { stringsIn(it).keys }
            otherNames.filter { it in strings.keys } shouldBe emptyList()
        }
    }
})
