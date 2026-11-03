package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File

class ReadAlikesResourcesTest : DescribeSpec({

    val valuesDir = File(System.getProperty("user.dir"), "src/main/res/values")
    val readAlikesFile = File(valuesDir, "strings_book_detail_readalikes.xml")
    val entryRegex = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    /** The string names and their texts in [file], with XML's `\'` escapes undone and one pair of quotes removed. */
    fun stringsIn(file: File): Map<String, String> =
        entryRegex.findAll(file.readText()).associate { match ->
            match.groupValues[1] to match.groupValues[2].replace("\\'", "'").removeSurrounding("\"")
        }

    describe("book detail read-alikes resources") {

        it("#106 TC-55: the read-alikes strings read as designed") {
            stringsIn(readAlikesFile) shouldContainExactly mapOf(
                "book_detail_readalikes_heading" to "Readers who finished %1\$s also read",
                "book_detail_readalikes_source" to "From Hardcover",
                "book_detail_readalikes_searching" to "Looking for read-alikes via Hardcover…",
                "book_detail_readalikes_unavailable" to "Suggestions are unavailable right now.",
                "book_detail_readalikes_open" to "open on Hardcover",
            )
        }

        it("#106 TC-56: names are prefixed and live only in the read-alikes file") {
            val strings = stringsIn(readAlikesFile)
            strings.keys.all { it.startsWith("book_detail_readalikes_") } shouldBe true

            val others = valuesDir.listFiles { file -> file.name.endsWith(".xml") && file != readAlikesFile }!!
            val otherNames = others.flatMap { stringsIn(it).keys }
            otherNames.filter { it in strings.keys } shouldBe emptyList()
        }
    }
})
