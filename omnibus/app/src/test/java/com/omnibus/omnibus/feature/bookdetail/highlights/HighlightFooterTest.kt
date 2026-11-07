package com.omnibus.omnibus.feature.bookdetail.highlights

import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.randomHighlight
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.util.Locale
import java.util.TimeZone

/** 2026-10-24 12:00 UTC, and 2026-10-24 00:30 UTC, in epoch millis. */
private const val OCT_24_NOON_2026 = 1_792_843_200_000L
private const val OCT_24_0030_2026 = 1_792_801_800_000L

/** 2026-11-02 12:00 UTC, the "now" of these tests. */
private const val NOW_NOV_2_2026 = 1_793_620_800_000L

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

private fun epub(fraction: Float?): Highlight =
    randomHighlight().copy(format = "epub", location = "epubcfi(/6/4)", fraction = fraction)

private fun chapter(index: Int, start: Float) = BookChapter(index, null, start)

private val FOUR_CHAPTERS = listOf(chapter(0, 0f), chapter(1, 0.25f), chapter(2, 0.5f), chapter(3, 0.75f))

class HighlightFooterTest : DescribeSpec({

    describe("the chapter a highlight is in") {

        it("TC-20: is the one containing it, numbered from the index, at and past each start") {
            checkAll(
                Exhaustive.of(
                    Triple(FOUR_CHAPTERS, 0.3f, 2),
                    Triple(FOUR_CHAPTERS, 0.25f, 2),
                    Triple(FOUR_CHAPTERS, 1.0f, 4),
                    Triple(listOf(chapter(5, 0f), chapter(6, 0.5f)), 0.2f, 6),
                ),
            ) { (chapters, fraction, expected) ->
                highlightChapterNumber(epub(fraction), chapters) shouldBe expected
            }
        }

        context("when chapters start at nearly the same place") {
            it("TC-21: is the lowest index, in either list order") {
                checkAll(
                    Exhaustive.of(
                        listOf(chapter(2, 0.4f), chapter(1, 0.4f)) to 0.45f,
                        listOf(chapter(1, 0.4f), chapter(2, 0.4f)) to 0.45f,
                        listOf(chapter(3, 0.3f), chapter(1, 0.30005f)) to 0.31f,
                        listOf(chapter(1, 0.30005f), chapter(3, 0.3f)) to 0.31f,
                    ),
                ) { (chapters, fraction) ->
                    highlightChapterNumber(epub(fraction), chapters) shouldBe 2
                }
            }
        }

        context("when the highlight can't be placed") {
            it("TC-20 / TC-22: is none, for a pdf, no location, no fraction, a NaN, a fraction before the chapters or no chapters") {
                checkAll(
                    Exhaustive.of(
                        epub(0.3f).copy(format = "pdf", location = "pdf:12") to FOUR_CHAPTERS,
                        epub(0.3f).copy(format = null, location = null) to FOUR_CHAPTERS,
                        epub(null) to FOUR_CHAPTERS,
                        epub(Float.NaN) to FOUR_CHAPTERS,
                        epub(0.05f) to listOf(chapter(0, 0.1f)),
                        epub(0.3f) to emptyList(),
                    ),
                ) { (highlight, chapters) ->
                    highlightChapterNumber(highlight, chapters) shouldBe null
                }
            }
        }
    }

    describe("the footer") {

        it("TC-23: joins the parts it has, for chapter and date, either alone, or neither") {
            checkAll(
                Exhaustive.of(
                    Triple(3, OCT_24_NOON_2026, "Chapter 3 · Oct 24"),
                    Triple(null, OCT_24_NOON_2026, "Oct 24"),
                    Triple(3, null, "Chapter 3"),
                    Triple(null, null, null),
                ),
            ) { (chapter, millis, expected) ->
                highlightFooter(chapter, millis, NOW_NOV_2_2026, Locale.US, UTC) shouldBe expected
            }
        }

        context("when the zone moves the date") {
            it("TC-24: dates it in the supplied zone") {
                highlightFooter(
                    null, OCT_24_0030_2026, NOW_NOV_2_2026, Locale.US, TimeZone.getTimeZone("America/Los_Angeles"),
                ) shouldBe "Oct 23"
            }
        }
    }
})
