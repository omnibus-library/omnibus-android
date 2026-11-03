package com.omnibus.omnibus.feature.bookdetail.more.export

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Locale

class ExportSizeTest : DescribeSpec({

    describe("exportSize") {

        it("#118 TC-13: formats sizes in decimal units with one decimal below 10 and whole numbers above") {
            val cases: List<Triple<Long, Locale, String?>> = listOf(
                Triple(0, Locale.ROOT, null),
                Triple(-5, Locale.ROOT, null),
                Triple(999, Locale.ROOT, "999 bytes"),
                Triple(412_000, Locale.ROOT, "412 KB"),
                Triple(138_000_000, Locale.ROOT, "138 MB"),
                Triple(1_400_000, Locale.ROOT, "1.4 MB"),
                Triple(2_500_000_000, Locale.ROOT, "2.5 GB"),
                Triple(999_999, Locale.ROOT, "1 MB"),
                Triple(999_500, Locale.ROOT, "1 MB"),
                Triple(1_400_000, Locale.GERMANY, "1,4 MB"),
            )
            cases.forEach { (bytes, locale, expected) ->
                withClue("bytes=$bytes locale=$locale") {
                    exportSize(bytes, locale) shouldBe expected
                }
            }
        }

        it("#118 TC-13: uses the default locale when none is given") {
            exportSize(412_000) shouldBe exportSize(412_000, Locale.getDefault())
        }
    }

    describe("ebookMeta and audioMeta") {

        it("#118 TC-14: joins the known parts with a middle dot and ends with the opens copy") {
            val opens = "opens the share sheet"
            val ebookCases: List<Pair<String?, String>> = listOf(
                "412 KB" to "412 KB · opens the share sheet",
                null to "opens the share sheet",
            )
            ebookCases.forEach { (size, expected) ->
                withClue("ebook size=$size") {
                    ebookMeta(size, opens) shouldBe expected
                }
            }

            val audioCases: List<Triple<String?, String?, String>> = listOf(
                Triple("M4B", "138 MB", "M4B · 138 MB · opens the share sheet"),
                Triple("M4B", null, "M4B · opens the share sheet"),
                Triple(null, "138 MB", "138 MB · opens the share sheet"),
                Triple(null, null, "opens the share sheet"),
            )
            audioCases.forEach { (format, size, expected) ->
                withClue("audio format=$format size=$size") {
                    audioMeta(format, size, opens) shouldBe expected
                }
            }
        }
    }
})
