package com.omnibus.omnibus.data.export

import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ExportFileNamesTest : DescribeSpec({

    describe("naming an exported file") {
        it("#118 TC-31: sanitises the title, across separators, control characters, long and empty titles") {
            checkAll(
                Exhaustive.of(
                    "A/B\\C:D" to "A B C D.epub",
                    " \tTi\u0000tle\n " to "Title.epub",
                    "a".repeat(300) to "a".repeat(120) + ".epub",
                    "" to "Book.epub",
                    "   " to "Book.epub",
                    "///" to "Book.epub",
                ),
            ) { (title, expected) ->
                ExportFileNames.fileName(title, suggested = "x.epub", fallbackExtension = "epub") shouldBe expected
            }
        }

        it("#118 TC-32: keeps the server's extension, lowercase, across KEPUB, plain and dotted names") {
            checkAll(
                Exhaustive.of(
                    "X.kepub.epub" to "kepub.epub",
                    "X.KEPUB.EPUB" to "kepub.epub",
                    "uuid.epub" to "epub",
                    "Book.PDF" to "pdf",
                    "a.b.M4B" to "m4b",
                ),
            ) { (suggested, extension) ->
                val title = randomString()

                ExportFileNames.fileName(title, suggested, fallbackExtension = randomString()) shouldBe "$title.$extension"
            }
        }

        context("when the server's name has no extension") {
            it("#118 TC-32: uses the fallback extension for missing, empty, bare and dot-ended names") {
                checkAll(Exhaustive.of(null, "", "noext", "name.")) { suggested ->
                    val title = randomString()
                    val fallback = randomString().lowercase()

                    ExportFileNames.fileName(title, suggested, fallback) shouldBe "$title.$fallback"
                }
            }
        }

        context("when the file is an audiobook part") {
            it("#118 TC-33: numbers it with at least two digits, for one, two and three digit parts") {
                checkAll(Exhaustive.of(1 to "01", 10 to "10", 100 to "100")) { (part, number) ->
                    val title = randomString()

                    ExportFileNames.fileName(title, suggested = "p.m4b", fallbackExtension = "m4b", part = part) shouldBe
                        "$title - part $number.m4b"
                }
            }

            it("#118 TC-33: sanitises the title") {
                ExportFileNames.fileName("A/B", suggested = "p.m4b", fallbackExtension = "m4b", part = 3) shouldBe
                    "A B - part 03.m4b"
            }
        }
    }
})
