package com.omnibus.omnibus.data.export

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ExportFileNamesTest : DescribeSpec({

    describe("ExportFileNames.fileName") {

        it("#118 TC-31: sanitises the title, strips control characters, caps it at 120 and falls back to Book") {
            val cases: List<Pair<String, String>> = listOf(
                "A/B\\C:D" to "A B C D.epub",
                " \tTi\u0000tle\n " to "Title.epub",
                "a".repeat(300) to "a".repeat(120) + ".epub",
                "" to "Book.epub",
                "   " to "Book.epub",
                "///" to "Book.epub",
            )
            cases.forEach { (title, expected) ->
                withClue("title=${title.take(20)}") {
                    ExportFileNames.fileName(title, suggested = "x.epub", fallbackExtension = "epub") shouldBe expected
                }
            }
        }

        it("#118 TC-32: keeps the server's extension (KEPUB as .kepub.epub) and falls back when there is none") {
            // Each case: suggested name, fallback extension, expected file name for the title "T".
            val cases: List<Triple<String?, String, String>> = listOf(
                Triple("X.kepub.epub", "epub", "T.kepub.epub"),
                Triple("X.KEPUB.EPUB", "epub", "T.kepub.epub"),
                Triple("uuid.epub", "epub", "T.epub"),
                Triple("Book.PDF", "epub", "T.pdf"),
                Triple("a.b.M4B", "epub", "T.m4b"),
                Triple(null, "epub", "T.epub"),
                Triple(null, "pdf", "T.pdf"),
                Triple(null, "m4b", "T.m4b"),
                Triple("", "epub", "T.epub"),
                Triple("noext", "pdf", "T.pdf"),
                Triple("name.", "m4b", "T.m4b"),
            )
            cases.forEach { (suggested, fallback, expected) ->
                withClue("suggested=$suggested fallback=$fallback") {
                    ExportFileNames.fileName("T", suggested, fallback) shouldBe expected
                }
            }
        }

        it("#118 TC-33: numbers audiobook parts with at least two digits and sanitises the title") {
            val cases: List<Triple<String, Int?, String>> = listOf(
                Triple("T", 1, "T - part 01.m4b"),
                Triple("T", 2, "T - part 02.m4b"),
                Triple("T", 10, "T - part 10.m4b"),
                Triple("T", 100, "T - part 100.m4b"),
                Triple("A/B", 3, "A B - part 03.m4b"),
                Triple("T", null, "T.m4b"),
            )
            cases.forEach { (title, part, expected) ->
                withClue("title=$title part=$part") {
                    ExportFileNames.fileName(title, suggested = "p.m4b", fallbackExtension = "m4b", part = part) shouldBe expected
                }
            }
        }
    }
})
