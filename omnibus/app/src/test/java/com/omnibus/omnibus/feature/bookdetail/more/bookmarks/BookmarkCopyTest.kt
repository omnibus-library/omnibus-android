package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Locale
import java.util.TimeZone

/** 2025-11-02 12:00 UTC: the "now" of the same-year dates. */
private const val NOW_2025 = 1_762_084_800_000L

/** 2026-11-02 12:00 UTC: the "now" of the other-year dates. */
private const val NOW_2026 = 1_793_620_800_000L

/** 2025-10-28 00:00 UTC and 2025-10-25 00:00 UTC, in epoch millis. */
private const val OCT_28_2025 = 1_761_609_600_000L
private const val OCT_25_2025 = 1_761_350_400_000L

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

private fun bookmark(
    position: String = "epubcfi(/6/14!/4/2)",
    title: String? = null,
    chapterTitle: String? = null,
    fraction: Float? = 0.47f,
    createdAtSeconds: Long? = 1_761_609_600L,
) = Bookmark(
    id = 1L,
    clientId = null,
    bookUuid = "b",
    position = position,
    title = title,
    chapterTitle = chapterTitle,
    fraction = fraction,
    createdAtSeconds = createdAtSeconds,
)

/** Nine ebook chapters; 0.47 falls in the seventh (starts at 0.45). */
private val ebookChapters = listOf(0f, 0.1f, 0.2f, 0.3f, 0.35f, 0.4f, 0.45f, 0.5f, 0.6f)
    .mapIndexed { index, start -> BookChapter(index, null, start) }

/** Five audio chapters; 0.28 falls in the fourth (starts at 0.25). */
private val audioChapters = listOf(0f, 0.1f, 0.2f, 0.25f, 0.3f)
    .mapIndexed { index, start -> BookChapter(index, null, start) }

private fun chapters(
    ebook: List<BookChapter> = ebookChapters,
    audio: List<BookChapter> = audioChapters,
    total: Long? = 7_200L,
) = BookChaptersResult.Success(
    ebookChapters = ebook,
    audioChapters = audio,
    audioFileId = 3L,
    audioTotalSeconds = total,
)

/** The meta line a row shows for [bookmark], built the way the sheet builds it (UTC, US English). */
private fun metaOf(bookmark: Bookmark, chapters: BookChaptersResult.Success?, now: Long = NOW_2025): String? {
    val placement = bookmarkPlacement(bookmark, chapters)
    val parts = bookmarkMetaParts(
        placement = placement,
        createdAtMillis = bookmark.createdAtSeconds?.times(1_000L),
        nowMillis = now,
        locale = Locale.US,
        timeZone = UTC,
    )
    return bookmarkMeta(parts)
}

class BookmarkCopyTest : DescribeSpec({

    describe("naming") {

        it("#117 TC-18: the title wins and is trimmed, also for an audio position") {
            bookmarkName(bookmark(title = " Barrel \n")) shouldBe "Barrel"
            bookmarkName(bookmark(position = "4332.5", title = " Barrel ")) shouldBe "Barrel"
        }

        it("#117 TC-19: a blank title names an audio position by its listening time") {
            listOf(
                "4332.5" to "Listening, 1 h 12 m in",
                "3120" to "Listening, 52 m in",
                "30" to "Listening, under a minute in",
                "0" to "Listening, under a minute in",
                "59.9" to "Listening, under a minute in",
                "3600" to "Listening, 1 h 00 m in",
            ).forEach { (position, expected) ->
                withClue("position $position") {
                    bookmarkName(bookmark(position = position, title = "  ")) shouldBe expected
                    bookmarkName(bookmark(position = position, title = null)) shouldBe expected
                }
            }
        }

        it("#117 TC-20: a text position without a title is saved position, and the chapter title is never used") {
            bookmarkName(bookmark(title = null, chapterTitle = "Chapter 3")) shouldBe "Saved position"
            bookmarkName(bookmark(title = "   ", chapterTitle = "Chapter 3")) shouldBe "Saved position"
        }
    }

    describe("placement and meta") {

        it("#117 TC-22: a text bookmark with chapters reads chapter, percent and date") {
            val placement = bookmarkPlacement(bookmark(fraction = 0.47f), chapters())
            placement shouldBe BookmarkPlacement(7, 47)
            metaOf(bookmark(fraction = 0.47f), chapters()) shouldBe "Chapter 7 · 47% · October 28"
        }

        it("#117 TC-23: a text bookmark without chapters drops the chapter part") {
            bookmarkPlacement(bookmark(fraction = 0.47f), null) shouldBe BookmarkPlacement(null, 47)
            metaOf(bookmark(fraction = 0.47f), null) shouldBe "47% · October 28"
        }

        it("#117 TC-24: the percent is rounded and clamped, and NaN or missing gives none") {
            listOf(
                0.4749 to 47,
                0.476 to 48,
                0.999 to 100,
                -0.1 to 0,
                1.2 to 100,
            ).forEach { (fraction, expected) ->
                withClue("fraction $fraction") { wholePercent(fraction) shouldBe expected }
            }
            wholePercent(Double.NaN) shouldBe null
            wholePercent(null) shouldBe null
        }

        it("#117 TC-27: an audio bookmark uses its seconds over the total, with the audio chapter") {
            val audio = bookmark(position = "2016", fraction = 0.9f, createdAtSeconds = 1_761_350_400L)
            bookmarkPlacement(audio, chapters()) shouldBe BookmarkPlacement(4, 28)
            metaOf(audio, chapters()) shouldBe "Chapter 4 · 28% · October 25"
        }

        it("#117 TC-28: an audio bookmark without a known total or audio chapters shows what it can") {
            val audio = bookmark(position = "2016")

            metaOf(audio, chapters(total = null)) shouldBe "October 28"
            metaOf(audio, chapters(total = 0L)) shouldBe "October 28"
            metaOf(audio, chapters(total = -5L)) shouldBe "October 28"
            metaOf(audio, null) shouldBe "October 28"

            metaOf(audio, chapters(audio = emptyList())) shouldBe "28% · October 28"

            bookmarkPlacement(bookmark(position = "9000"), chapters()).percent shouldBe 100
        }

        it("#117 TC-29: an audio bookmark ignores the server's percent") {
            bookmarkPlacement(bookmark(position = "2016", fraction = 0.9f), chapters()).percent shouldBe 28
        }

        it("#117 TC-30: the date is the same-year or other-year form, and a missing part leaves no stray separator") {
            bookmarkMetaParts(BookmarkPlacement(null, null), OCT_28_2025, NOW_2025, Locale.US, UTC) shouldBe
                listOf("October 28")
            bookmarkMetaParts(BookmarkPlacement(null, null), OCT_28_2025, NOW_2026, Locale.US, UTC) shouldBe
                listOf("October 28, 2025")
            bookmarkMeta(bookmarkMetaParts(BookmarkPlacement(null, 47), null, NOW_2025, Locale.US, UTC)) shouldBe "47%"
            metaOf(bookmark(fraction = 0.47f, createdAtSeconds = null), null) shouldBe "47%"
            metaOf(bookmark(fraction = null, createdAtSeconds = null), null) shouldBe null
            bookmarkMeta(emptyList()) shouldBe null
        }

        it("#117 TC-30: the date follows the zone, so a UTC midnight is the previous day in Los Angeles") {
            val losAngeles = TimeZone.getTimeZone("America/Los_Angeles")
            bookmarkMetaParts(BookmarkPlacement(null, null), OCT_28_2025, NOW_2025, Locale.US, losAngeles) shouldBe
                listOf("October 27")
        }

        it("#117 TC-31: a single part has no separator") {
            bookmarkMeta(bookmarkMetaParts(BookmarkPlacement(7, null), null, NOW_2025, Locale.US, UTC)) shouldBe
                "Chapter 7"
            bookmarkMeta(bookmarkMetaParts(BookmarkPlacement(null, 47), null, NOW_2025, Locale.US, UTC)) shouldBe "47%"
            bookmarkMeta(bookmarkMetaParts(BookmarkPlacement(null, null), OCT_28_2025, NOW_2025, Locale.US, UTC)) shouldBe
                "October 28"
        }
    }

    describe("audio decision") {

        it("#117 TC-34: plain decimals are audio, with their parsed seconds") {
            listOf(
                "0" to 0.0,
                "4332.5" to 4332.5,
                "120" to 120.0,
                " 120 " to 120.0,
                "1000000000" to 1_000_000_000.0,
                "0.0" to 0.0,
            ).forEach { (position, seconds) ->
                withClue("position '$position'") {
                    listeningSeconds(position) shouldBe seconds
                    isAudio(bookmark(position = position)) shouldBe true
                }
            }
        }

        it("#117 TC-35: everything else is text") {
            listOf(
                "-1", "+5", "1e3", "NaN", "Infinity", "0x10", "1.0d", "1.0f", ".5", "5.", "1,5",
                "1000000001", "epubcfi(/6/14!/4/2)", "", "   ",
            ).forEach { position ->
                withClue("position '$position'") {
                    listeningSeconds(position) shouldBe null
                    isAudio(bookmark(position = position)) shouldBe false
                }
            }
        }

        it("#117 TC-36: the decision reads the position only, and an overflowing digit string is text") {
            val huge = "9".repeat(400)
            listeningSeconds(huge) shouldBe null
            isAudio(bookmark(position = huge)) shouldBe false
            isAudio(bookmark(position = "120")) shouldBe isAudio(bookmark(position = " 120 ", title = "x"))
        }
    }

    describe("destinations") {

        it("#117 TC-47: a text bookmark opens the reader at its trimmed CFI") {
            bookmarkDestination("u", bookmark(position = " epubcfi(/6/14!/4/2) ")) shouldBe
                BookDetailDestination.Reader("u", startCfi = "epubcfi(/6/14!/4/2)")
        }

        it("#117 TC-48: a text position that is not a CFI resumes normally") {
            listOf("", "chapter-3", "garbage").forEach { position ->
                withClue("position '$position'") {
                    bookmarkDestination("u", bookmark(position = position)) shouldBe
                        BookDetailDestination.Reader("u", startCfi = null)
                }
            }
        }

        it("#117 TC-53: an audio bookmark opens the player at its time") {
            bookmarkDestination("u", bookmark(position = "4332.5")) shouldBe
                BookDetailDestination.Player("u", bookFileId = null, positionSeconds = 4332.5)
            bookmarkDestination("u", bookmark(position = " 120 ")) shouldBe
                BookDetailDestination.Player("u", bookFileId = null, positionSeconds = 120.0)
        }
    }

    describe("row description") {

        it("#117 TC-60: the description joins the name and its parts, with no trailing comma") {
            bookmarkRowDescription("The Episode of the Barrel", listOf("Chapter 7", "47%", "October 28")) shouldBe
                "The Episode of the Barrel, Chapter 7, 47%, October 28"
            bookmarkRowDescription("X", listOf("47%")) shouldBe "X, 47%"
            bookmarkRowDescription("X", emptyList()) shouldBe "X"
        }
    }
})
