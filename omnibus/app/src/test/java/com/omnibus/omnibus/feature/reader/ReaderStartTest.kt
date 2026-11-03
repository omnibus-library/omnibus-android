package com.omnibus.omnibus.feature.reader

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReaderStartTest : DescribeSpec({

    describe("ReaderStart.AtCfi") {

        it("#117 TC-52: at cfi start carries the cfi") {
            ReaderStart.AtCfi("epubcfi(/6/8)").cfi shouldBe "epubcfi(/6/8)"
            readerStartOf("epubcfi(/6/8)", null, null) shouldBe ReaderStart.AtCfi("epubcfi(/6/8)")
        }
    }

    describe("readerStartOf") {

        it("TC-62: a stored or server position resolves to its start") {
            val rows = listOf(
                // Own CFI wins, even with a derived CFI and a percent.
                Triple(Triple("epubcfi(/6/8)", "epubcfi(/6/9)", 57), ReaderStart.AtCfi("epubcfi(/6/8)"), "own cfi"),
                // A percent-only record boots at the derived CFI.
                Triple(Triple(null, "epubcfi(/6/9)", 57), ReaderStart.AtPercent(0.57, "epubcfi(/6/9)"), "derived cfi"),
                Triple(Triple(null, null, 57), ReaderStart.AtPercent(0.57, null), "percent only"),
                // A derived value that is not an EPUB CFI is not a boot point.
                Triple(Triple(null, "pdf-page:9", 57), ReaderStart.AtPercent(0.57, null), "non-cfi derived"),
                Triple(Triple(null, null, 0), ReaderStart.Beginning, "zero percent"),
                Triple(Triple(null, null, null), ReaderStart.Beginning, "nothing"),
                // A non-CFI anchor with a percent opens near the percent.
                Triple(Triple("pdf-page:3", null, 40), ReaderStart.AtPercent(0.4, null), "non-cfi anchor"),
            )
            rows.forEach { (input, expected, label) ->
                val (cfi, derived, percent) = input
                withClue("$label: cfi=$cfi derived=$derived percent=$percent") {
                    readerStartOf(cfi, derived, percent) shouldBe expected
                }
            }
        }
    }
})
