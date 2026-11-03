package com.omnibus.omnibus.feature.bookdetail.journal

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Locale
import java.util.TimeZone

/** 2025-10-24 00:00 UTC. */
private const val OCT_24_2025 = 1_761_264_000_000L

/** 2025-10-29, the same year as [OCT_24_2025]. */
private const val NOW_2025 = 1_761_775_445_000L

/** 2026-11-02 00:00 UTC, a later year than [OCT_24_2025]. */
private const val NOW_2026 = 1_793_620_800_000L

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

private fun byline(
    authorName: String = "Ann",
    isOwn: Boolean = false,
    isDraft: Boolean = false,
    createdAtMillis: Long? = OCT_24_2025,
    nowMillis: Long = NOW_2025,
    timeZone: TimeZone = UTC,
) = journalByline(
    authorName = authorName,
    isOwn = isOwn,
    isDraft = isDraft,
    createdAtMillis = createdAtMillis,
    nowMillis = nowMillis,
    locale = Locale.US,
    timeZone = timeZone,
    draftLabel = "Draft",
)

class JournalBylineTest : DescribeSpec({

    describe("the byline") {

        it("TC-34: parts join with a separator, the viewer's own name is left out, and Draft comes last") {
            byline() shouldBe "Ann · October 24"
            byline(isOwn = true) shouldBe "October 24"
            byline(isOwn = true, isDraft = true) shouldBe "October 24 · Draft"
            byline(isDraft = true) shouldBe "Ann · October 24 · Draft"
        }

        it("TC-35: a date in another year carries the year; a missing date or name is omitted; no parts is null") {
            byline(nowMillis = NOW_2026) shouldBe "Ann · October 24, 2025"
            byline(createdAtMillis = null) shouldBe "Ann"
            byline(authorName = "   ") shouldBe "October 24"
            byline(authorName = "", createdAtMillis = null) shouldBe null
            byline(authorName = "", isOwn = true, createdAtMillis = null, isDraft = false) shouldBe null

            // The same instant is a different calendar day in Los Angeles, so the date follows the zone given.
            byline(timeZone = TimeZone.getTimeZone("America/Los_Angeles")) shouldBe "Ann · October 23"
            byline(timeZone = UTC) shouldBe "Ann · October 24"
        }
    }
})
