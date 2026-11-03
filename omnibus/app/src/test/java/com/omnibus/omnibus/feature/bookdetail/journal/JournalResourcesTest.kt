package com.omnibus.omnibus.feature.bookdetail.journal

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File

class JournalResourcesTest : DescribeSpec({

    val valuesDir = File(System.getProperty("user.dir"), "src/main/res/values")
    val entryRegex = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    /** The string names and their texts in [file], with the surrounding quotes and XML's `\'` escapes undone. */
    fun stringsIn(file: File): Map<String, String> =
        entryRegex.findAll(file.readText()).associate { match ->
            match.groupValues[1] to match.groupValues[2].removeSurrounding("\"").replace("\\'", "'")
        }

    describe("book detail journal resources") {

        it("TC-46: strings are exact, prefixed, and defined in no other file") {
            val journalFile = File(valuesDir, "strings_book_detail_journal.xml")
            journalFile.exists() shouldBe true
            val strings = stringsIn(journalFile)

            strings shouldContainExactly mapOf(
                "book_detail_journal_heading" to "Journal",
                "book_detail_journal_empty" to
                    "No entries yet. Be the first to write one. Anyone reading this book can add to the journal.",
                "book_detail_journal_failed" to "Entries couldn't be loaded.",
                "book_detail_journal_retry" to "Try again",
                "book_detail_journal_draft" to "Draft",
                "book_detail_journal_spoiler_hidden" to "spoiler, hidden",
            )
            strings.keys.all { it.startsWith("book_detail_journal_") } shouldBe true

            val otherNames = valuesDir.listFiles { file -> file.name.endsWith(".xml") && file != journalFile }!!
                .flatMap { stringsIn(it).keys }
            otherNames.filter { it in strings.keys } shouldBe emptyList()
        }
    }
})
