package com.omnibus.omnibus.feature.bookdetail.more.export

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.util.Locale

class ExportSizeTest : DescribeSpec({

    describe("formatting a file size") {
        it("#118 TC-13: uses decimal units with one decimal below 10, for each locale") {
            checkAll(
                Exhaustive.of(
                    Triple(999L, Locale.ROOT, "999 bytes"),
                    Triple(412_000L, Locale.ROOT, "412 KB"),
                    Triple(1_400_000L, Locale.ROOT, "1.4 MB"),
                    Triple(2_500_000_000L, Locale.ROOT, "2.5 GB"),
                    Triple(999_500L, Locale.ROOT, "1 MB"),
                    Triple(1_400_000L, Locale.GERMANY, "1,4 MB"),
                ),
            ) { (bytes, locale, expected) ->
                exportSize(bytes, locale) shouldBe expected
            }
        }

        context("when the size is unknown") {
            it("#118 TC-13: gives no size, for zero or negative bytes") {
                checkAll(Exhaustive.of(0L, -5L)) { bytes ->
                    exportSize(bytes, Locale.ROOT) shouldBe null
                }
            }
        }
    }

    describe("the subtitle of a save row") {
        it("#118 TC-14: joins the known parts with a middle dot, ending with the opens copy") {
            checkAll(
                Exhaustive.of(
                    ebookMeta("412 KB", "opens") to "412 KB · opens",
                    ebookMeta(null, "opens") to "opens",
                    audioMeta("M4B", "138 MB", "opens") to "M4B · 138 MB · opens",
                    audioMeta("M4B", null, "opens") to "M4B · opens",
                    audioMeta(null, "138 MB", "opens") to "138 MB · opens",
                ),
            ) { (actual, expected) ->
                actual shouldBe expected
            }
        }
    }
})
