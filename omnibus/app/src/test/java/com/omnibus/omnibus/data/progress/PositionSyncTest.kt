package com.omnibus.omnibus.data.progress

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds

class PositionSyncTest : DescribeSpec({

    /** A remote record with [cfi], [percent] and the given clocks. */
    fun remote(
        cfi: String? = "epubcfi(/6/8)",
        percent: Int? = null,
        clientUpdatedAt: Long? = null,
        updatedAt: Long = 0,
    ) = ServerReadingPosition(
        cfi = cfi,
        percent = percent,
        derivedCfi = null,
        updatedAt = updatedAt,
        clientUpdatedAt = clientUpdatedAt,
    )

    describe("OPEN_DEADLINE") {

        it("is 1200 ms, as iOS") {
            PositionSync.OPEN_DEADLINE shouldBe 1200.milliseconds
        }
    }

    describe("isNewer") {

        val local = ReadingPosition("book-1", "epubcfi(/6/2)", 3, 100)

        it("TC-56: newer is strictly greater on the ordering clock") {
            val rows = listOf(
                Triple(remote(clientUpdatedAt = 101), local, true),
                Triple(remote(clientUpdatedAt = 100), local, false),
                Triple(remote(clientUpdatedAt = 99), local, false),
                Triple(remote(clientUpdatedAt = 1), null, true),
                // The client clock wins over a later server clock.
                Triple(remote(clientUpdatedAt = 99, updatedAt = 500), local, false),
                // Without a client clock the server clock is the fallback.
                Triple(remote(clientUpdatedAt = null, updatedAt = 101), local, true),
            )
            rows.forEach { (record, against, expected) ->
                withClue("remote=$record local=$against") {
                    PositionSync.isNewer(record, against) shouldBe expected
                }
            }
        }

        it("TC-59: a CFI row carries a position at any percent; another anchor only with a percent above zero") {
            val cfis = listOf("epubcfi(/6/4)", "  epubcfi(/6/4)", "", "  ", null, "pdf-page:3", "comic-page:2")
            val percents = listOf<Int?>(null, 0, 1, 57)
            cfis.forEach { cfi ->
                percents.forEach { percent ->
                    val expected = PositionSync.isEpubCfi(cfi) || (percent ?: 0) > 0
                    withClue("cfi=$cfi percent=$percent") {
                        PositionSync.carriesPosition(cfi, percent) shouldBe expected
                    }
                }
            }
            withClue("cfi=epubcfi(/6/4) percent=null") {
                PositionSync.carriesPosition("epubcfi(/6/4)", null) shouldBe true
            }
            withClue("cfi=  epubcfi(/6/4) percent=null") {
                PositionSync.carriesPosition("  epubcfi(/6/4)", null) shouldBe true
            }
            withClue("cfi=pdf-page:3 percent=57") {
                PositionSync.carriesPosition("pdf-page:3", 57) shouldBe true
            }
            withClue("cfi=pdf-page:3 percent=null") {
                PositionSync.carriesPosition("pdf-page:3", null) shouldBe false
            }
            withClue("cfi=pdf-page:3") {
                PositionSync.isEpubCfi("pdf-page:3") shouldBe false
            }
        }

        it("TC-60: a row with no position is never newer") {
            // No CFI, no percent, and a clock far in the future.
            PositionSync.isNewer(remote(cfi = null, percent = null, clientUpdatedAt = Long.MAX_VALUE / 2), null) shouldBe false
            // A non-CFI anchor with no percent.
            PositionSync.isNewer(remote(cfi = "pdf-page:3", percent = null, clientUpdatedAt = 1_000), null) shouldBe false
        }
    }
})
