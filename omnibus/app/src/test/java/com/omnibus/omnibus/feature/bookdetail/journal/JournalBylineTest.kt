package com.omnibus.omnibus.feature.bookdetail.journal

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Locale
import java.util.TimeZone

/** 2025-10-24 00:00 UTC. */
private const val OCT_24_2025 = 1_761_264_000_000L

/** 2025-10-29, the same year as [OCT_24_2025]. */
private const val NOW_2025 = 1_761_775_445_000L

private fun byline(
    authorName: String,
    isOwn: Boolean = false,
    isDraft: Boolean = false,
    createdAtMillis: Long? = OCT_24_2025,
) = journalByline(
    authorName = authorName,
    isOwn = isOwn,
    isDraft = isDraft,
    createdAtMillis = createdAtMillis,
    nowMillis = NOW_2025,
    locale = Locale.US,
    timeZone = TimeZone.getTimeZone("UTC"),
    draftLabel = "Draft",
)

class JournalBylineTest : DescribeSpec({

    describe("the byline") {

        it("TC-34: joins the name with the date") {
            val name = randomString()

            byline(name) shouldBe "$name · October 24"
        }

        context("when the entry is the viewer's own") {
            it("TC-34: leaves out the name") {
                byline(randomString(), isOwn = true) shouldBe "October 24"
            }
        }

        context("when the entry is a draft") {
            it("TC-34: ends with Draft") {
                val name = randomString()

                byline(name, isDraft = true) shouldBe "$name · October 24 · Draft"
            }
        }

        context("when there is nothing to show") {
            it("TC-35: is no byline") {
                byline("   ", isOwn = randomBoolean(), createdAtMillis = null) shouldBe null
            }
        }
    }
})
