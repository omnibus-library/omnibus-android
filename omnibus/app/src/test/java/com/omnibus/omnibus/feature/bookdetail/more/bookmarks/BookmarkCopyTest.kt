package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import com.omnibus.omnibus.data.bookmarks.randomBookmark
import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.util.Locale
import java.util.TimeZone

private const val TEXT_POSITION = "epubcfi(/6/14!/4/2)"

/** 2025-10-28 00:00 UTC, and a "now" in the same year, in epoch millis. */
private const val OCT_28_2025 = 1_761_609_600_000L
private const val NOW_2025 = 1_762_084_800_000L

private fun chaptersAt(vararg starts: Float): List<BookChapter> =
    starts.mapIndexed { index, start -> BookChapter(index, randomString(), start) }

private fun chapters(
    ebook: List<BookChapter> = emptyList(),
    audio: List<BookChapter> = emptyList(),
    total: Long? = null,
) = BookChaptersResult.Success(
    ebookChapters = ebook,
    audioChapters = audio,
    audioFileId = randomLong(),
    audioTotalSeconds = total,
)

/** A text bookmark 47% through the book. */
private fun textBookmark() = randomBookmark().copy(position = TEXT_POSITION, fraction = 0.47f)

/** An audio bookmark at 2016 seconds, whose server fraction disagrees with its time. */
private fun audioBookmark() = randomBookmark().copy(position = "2016", fraction = 0.9f)

private fun meta(placement: BookmarkPlacement, createdAtMillis: Long?): String? = bookmarkMeta(
    bookmarkMetaParts(placement, createdAtMillis, NOW_2025, Locale.US, TimeZone.getTimeZone("UTC")),
)

class BookmarkCopyTest : DescribeSpec({

    describe("naming a bookmark") {
        context("when it has a title") {
            it("#117 TC-18: uses the trimmed title, regardless of the position kind") {
                checkAll(Exhaustive.of(TEXT_POSITION, "4332.5")) { position ->
                    bookmarkName(randomBookmark().copy(position = position, title = " Barrel \n")) shouldBe "Barrel"
                }
            }
        }

        context("when it has no usable title") {
            context("when its position is audio") {
                it("#117 TC-19: uses its listening time") {
                    checkAll(
                        Exhaustive.of(
                            Triple("4332.5", "  ", "Listening, 1 h 12 m in"),
                            Triple("59.9", null, "Listening, under a minute in"),
                        ),
                    ) { (position, title, expected) ->
                        bookmarkName(randomBookmark().copy(position = position, title = title)) shouldBe expected
                    }
                }
            }

            context("when its position is text") {
                it("#117 TC-20: says saved position, never the chapter title") {
                    val bookmark = randomBookmark().copy(position = TEXT_POSITION, title = "  ", chapterTitle = randomString())

                    bookmarkName(bookmark) shouldBe "Saved position"
                }
            }
        }
    }

    describe("reading a fraction as a percent") {
        it("#117 TC-24: rounds to a whole number clamped to 0 through 100") {
            checkAll(Exhaustive.of(0.4749 to 47, 0.476 to 48, -0.1 to 0, 1.2 to 100)) { (fraction, expected) ->
                wholePercent(fraction) shouldBe expected
            }
        }

        context("when the fraction is unusable") {
            it("#117 TC-24: gives no percent") {
                checkAll(Exhaustive.of(null, Double.NaN)) { fraction ->
                    wholePercent(fraction) shouldBe null
                }
            }
        }
    }

    describe("placing a bookmark in the book") {
        context("when it is a text bookmark") {
            context("when the chapters are known") {
                it("#117 TC-22: gives the ebook chapter with the server's percent") {
                    bookmarkPlacement(textBookmark(), chapters(ebook = chaptersAt(0f, 0.45f, 0.5f))) shouldBe BookmarkPlacement(2, 47)
                }
            }

            context("when the chapters are unknown") {
                it("#117 TC-23: gives only the percent") {
                    bookmarkPlacement(textBookmark(), null) shouldBe BookmarkPlacement(null, 47)
                }
            }
        }

        context("when it is an audio bookmark") {
            context("when the total is known") {
                it("#117 TC-27, TC-29: uses its seconds over the total with the audio chapter, not the server's percent") {
                    val loaded = chapters(audio = chaptersAt(0f, 0.25f, 0.3f), total = 7_200L)

                    bookmarkPlacement(audioBookmark(), loaded) shouldBe BookmarkPlacement(2, 28)
                }

                context("when there are no audio chapters") {
                    it("#117 TC-28: gives only the percent") {
                        bookmarkPlacement(audioBookmark(), chapters(total = 7_200L)) shouldBe BookmarkPlacement(null, 28)
                    }
                }
            }

            context("when the total is unknown") {
                it("#117 TC-28: has no placement, for no chapters or a null, zero or negative total") {
                    checkAll(Exhaustive.of(null, chapters(), chapters(total = 0L), chapters(total = -5L))) { loaded ->
                        bookmarkPlacement(audioBookmark(), loaded) shouldBe BookmarkPlacement(null, null)
                    }
                }
            }
        }
    }

    describe("the meta line of a row") {
        it("#117 TC-22: reads chapter, percent, then date") {
            meta(BookmarkPlacement(7, 47), OCT_28_2025) shouldBe "Chapter 7 · 47% · October 28"
        }

        context("when a part is missing") {
            it("#117 TC-30, TC-31: leaves no stray separator") {
                checkAll(
                    Exhaustive.of(
                        Triple(BookmarkPlacement(7, null), null, "Chapter 7"),
                        Triple(BookmarkPlacement(null, 47), null, "47%"),
                        Triple(BookmarkPlacement(null, null), OCT_28_2025, "October 28"),
                        Triple(BookmarkPlacement(7, null), OCT_28_2025, "Chapter 7 · October 28"),
                    ),
                ) { (placement, createdAtMillis, expected) ->
                    meta(placement, createdAtMillis) shouldBe expected
                }
            }
        }

        context("when every part is missing") {
            it("#117 TC-30: is absent") {
                meta(BookmarkPlacement(null, null), null) shouldBe null
            }
        }
    }

    describe("telling audio positions from text") {
        it("#117 TC-34: reads a plain decimal in range as seconds") {
            checkAll(
                Exhaustive.of("0" to 0.0, "4332.5" to 4332.5, " 120 " to 120.0, "1000000000" to 1_000_000_000.0),
            ) { (position, seconds) ->
                listeningSeconds(position) shouldBe seconds
            }
        }

        it("#117 TC-35, TC-36: reads anything else as text") {
            checkAll(
                Exhaustive.of(
                    "-1", "+5", "1e3", "NaN", "Infinity", "0x10", "1.0d", ".5", "5.", "1,5",
                    "1000000001", "9".repeat(400), TEXT_POSITION, "", "   ",
                ),
            ) { position ->
                listeningSeconds(position) shouldBe null
            }
        }
    }

    describe("where a tap on a bookmark leads") {
        context("when the position is a CFI") {
            it("#117 TC-47: opens the reader at the trimmed CFI") {
                val uuid = randomString()

                bookmarkDestination(uuid, randomBookmark().copy(position = " $TEXT_POSITION ")) shouldBe
                    BookDetailDestination.Reader(uuid, startCfi = TEXT_POSITION)
            }
        }

        context("when the position is text that is not a CFI") {
            it("#117 TC-48: resumes the reader normally") {
                val uuid = randomString()

                checkAll(Exhaustive.of("", "chapter-3")) { position ->
                    bookmarkDestination(uuid, randomBookmark().copy(position = position)) shouldBe
                        BookDetailDestination.Reader(uuid, startCfi = null)
                }
            }
        }

        context("when the position is audio") {
            it("#117 TC-53: opens the player at its time") {
                val uuid = randomString()

                bookmarkDestination(uuid, randomBookmark().copy(position = "4332.5")) shouldBe
                    BookDetailDestination.Player(uuid, bookFileId = null, positionSeconds = 4332.5)
            }
        }
    }
})
