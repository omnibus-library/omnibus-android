package com.omnibus.omnibus.feature.bookdetail.actionbar

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File

class ActionBarResourcesTest : DescribeSpec({

    val valuesDir = File(System.getProperty("user.dir"), "src/main/res/values")
    val entryRegex = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    /** The string names and their texts in [file], with XML's `\'` escapes undone. */
    fun stringsIn(file: File): Map<String, String> =
        entryRegex.findAll(file.readText()).associate { match ->
            match.groupValues[1] to match.groupValues[2].replace("\\'", "'")
        }

    describe("book detail action bar resources") {

        it("TC-29: strings are prefixed and say the expected thing") {
            val file = File(valuesDir, "strings_book_detail_actionbar.xml")
            file.exists() shouldBe true
            val strings = stringsIn(file)

            strings shouldContainExactly mapOf(
                "book_detail_actionbar_start_reading" to "Start reading",
                "book_detail_actionbar_continue_chapter" to "Continue · Chapter %1\$d",
                "book_detail_actionbar_continue_reading" to "Continue reading",
                "book_detail_actionbar_read_again" to "Read again",
                "book_detail_actionbar_start_listening" to "Start listening",
                "book_detail_actionbar_continue_listening" to "Continue listening",
                "book_detail_actionbar_listen_again" to "Listen again",
                "book_detail_actionbar_physical_copy" to "On your shelf · physical copy",
                "book_detail_actionbar_no_copy" to "No readable copy",
            )
            strings.keys.all { it.startsWith("book_detail_actionbar_") } shouldBe true

            val otherNames = valuesDir.listFiles { f -> f.name.endsWith(".xml") && f != file }!!
                .flatMap { stringsIn(it).keys }
            otherNames.filter { it in strings.keys } shouldBe emptyList()
        }

        it("TC-30: the chapter string takes the chapter number") {
            val raw = stringsIn(File(valuesDir, "strings_book_detail_actionbar.xml"))
                .getValue("book_detail_actionbar_continue_chapter")
            (raw.split("%1\$d").size - 1) shouldBe 1
            String.format(raw, 7) shouldBe "Continue · Chapter 7"
        }

        it("#99 TC-52: listen strings are prefixed and say the expected thing") {
            val file = File(valuesDir, "strings_book_detail_listen.xml")
            file.exists() shouldBe true
            val strings = stringsIn(file)

            strings shouldContainExactly mapOf(
                "book_detail_listen_listen" to "Listen",
                "book_detail_listen_listen_offline" to "Listen. The audiobook needs a connection.",
                "book_detail_listen_read" to "Read",
                "book_detail_listen_immersive_read" to "Immersive read",
                "book_detail_listen_choose_file" to "Choose a file",
                "book_detail_listen_close" to "Close",
                "book_detail_listen_continues" to "Continues where you left off",
                "book_detail_listen_starts_over" to "Starts from the beginning",
            )
            strings.keys.all { it.startsWith("book_detail_listen_") } shouldBe true

            val otherNames = valuesDir.listFiles { f -> f.name.endsWith(".xml") && f != file }!!
                .flatMap { stringsIn(it).keys }
            otherNames.filter { it in strings.keys } shouldBe emptyList()
        }
    }
})
