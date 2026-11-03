package com.omnibus.omnibus.feature.bookdetail.highlights

import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Locale
import java.util.TimeZone

/** 2026-10-24 12:00 UTC, and 2026-10-24 00:30 UTC, in epoch millis. */
private const val OCT_24_NOON_2026 = 1_792_843_200_000L
private const val OCT_24_0030_2026 = 1_792_801_800_000L

/** 2026-11-02 12:00 UTC, the "now" of these tests. */
private const val NOW_NOV_2_2026 = 1_793_620_800_000L

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

private fun epub(fraction: Float?, format: String? = "epub", location: String? = "epubcfi(/6/4)") = Highlight(
    id = 1L,
    text = "a",
    note = null,
    color = HighlightColor.Amber,
    location = location,
    format = format,
    createdAtMillis = OCT_24_NOON_2026,
    fraction = fraction,
)

private fun chapter(index: Int, start: Float) = BookChapter(index, null, start)

class HighlightFooterTest : DescribeSpec({

    describe("the chapter lookup") {

        it("TC-20: a fraction places the highlight in the chapter that contains it, numbered from the index") {
            val four = listOf(chapter(0, 0f), chapter(1, 0.25f), chapter(2, 0.5f), chapter(3, 0.75f))
            listOf(
                Triple(four, 0.3f, 2),
                Triple(four, 0.25f, 2),
                Triple(four, 0.75f, 4),
                Triple(four, 1.0f, 4),
                Triple(listOf(chapter(0, 0.1f)), 0.05f, null),
                Triple(listOf(chapter(5, 0f), chapter(6, 0.5f)), 0.2f, 6),
            ).forEach { (chapters, fraction, expected) ->
                highlightChapterNumber(epub(fraction), chapters) shouldBe expected
            }
        }

        it("TC-21: near-equal starts give the lowest index, in either list order") {
            val pairs = listOf(
                Triple(listOf(chapter(2, 0.4f), chapter(1, 0.4f)), 0.45f, 2),
                Triple(listOf(chapter(1, 0.4f), chapter(2, 0.4f)), 0.45f, 2),
                Triple(listOf(chapter(3, 0.3f), chapter(1, 0.30005f)), 0.31f, 2),
                Triple(listOf(chapter(1, 0.30005f), chapter(3, 0.3f)), 0.31f, 2),
            )
            pairs.forEach { (chapters, fraction, expected) ->
                highlightChapterNumber(epub(fraction), chapters) shouldBe expected
            }
        }

        it("TC-22: anything that cannot be placed has no chapter, and nothing throws") {
            val four = listOf(chapter(0, 0f), chapter(1, 0.25f), chapter(2, 0.5f), chapter(3, 0.75f))
            listOf(
                epub(0.3f, format = "pdf", location = "pdf:12") to four,
                epub(0.3f, format = "pdf", location = "pdf-page:3") to four,
                epub(null, format = null, location = null) to four,
                epub(0.3f, format = null, location = null) to four,
                epub(null) to four,
                epub(Float.NaN) to four,
                epub(0.3f) to emptyList(),
            ).forEach { (highlight, chapters) ->
                highlightChapterNumber(highlight, chapters) shouldBe null
            }
        }
    }

    describe("the footer") {

        it("TC-23: the footer joins the chapter and the date, and either part alone, or nothing") {
            val rows = listOf(
                Triple(3, OCT_24_NOON_2026, "Chapter 3 · Oct 24"),
                Triple(null, OCT_24_NOON_2026, "Oct 24"),
                Triple(3, null, "Chapter 3"),
                Triple(null, null, null),
            )
            rows.forEach { (chapter, millis, expected) ->
                highlightFooter(chapter, millis, NOW_NOV_2_2026, Locale.US, UTC) shouldBe expected
            }
        }

        it("TC-24: the date part is the shared short date, per zone and locale, and the label and separator can change") {
            val sameYear = BookDetailFormat.shortDate(OCT_24_NOON_2026, NOW_NOV_2_2026, Locale.US, UTC)
            val previousYear = BookDetailFormat.shortDate(1_761_307_200_000L, NOW_NOV_2_2026, Locale.US, UTC)
            val afterNow = BookDetailFormat.shortDate(1_793_880_000_000L, NOW_NOV_2_2026, Locale.US, UTC)
            sameYear shouldBe "Oct 24"
            previousYear shouldBe "Oct 24, 2025"
            afterNow shouldBe "Nov 5"
            highlightFooter(null, OCT_24_NOON_2026, NOW_NOV_2_2026, Locale.US, UTC) shouldBe sameYear
            highlightFooter(null, 1_761_307_200_000L, NOW_NOV_2_2026, Locale.US, UTC) shouldBe previousYear
            highlightFooter(null, 1_793_880_000_000L, NOW_NOV_2_2026, Locale.US, UTC) shouldBe afterNow

            val utc = BookDetailFormat.shortDate(OCT_24_0030_2026, NOW_NOV_2_2026, Locale.US, UTC)
            val losAngeles = BookDetailFormat.shortDate(
                OCT_24_0030_2026, NOW_NOV_2_2026, Locale.US, TimeZone.getTimeZone("America/Los_Angeles"),
            )
            utc shouldBe "Oct 24"
            losAngeles shouldBe "Oct 23"
            highlightFooter(null, OCT_24_0030_2026, NOW_NOV_2_2026, Locale.US, UTC) shouldBe utc
            highlightFooter(
                null, OCT_24_0030_2026, NOW_NOV_2_2026, Locale.US, TimeZone.getTimeZone("America/Los_Angeles"),
            ) shouldBe losAngeles

            highlightFooter(null, OCT_24_NOON_2026, NOW_NOV_2_2026, Locale.FRANCE, UTC) shouldBe
                BookDetailFormat.shortDate(OCT_24_NOON_2026, NOW_NOV_2_2026, Locale.FRANCE, UTC)

            highlightFooter(
                chapterNumber = 3,
                createdAtMillis = OCT_24_NOON_2026,
                nowMillis = NOW_NOV_2_2026,
                locale = Locale.US,
                timeZone = UTC,
                chapterLabel = { "Ch. $it" },
                separator = " / ",
            ) shouldBe "Ch. 3 / Oct 24"
        }
    }
})
