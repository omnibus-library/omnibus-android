package com.omnibus.omnibus.feature.bookdetail.progress

import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.chapters
import com.omnibus.omnibus.data.bookprogress.listeningRecord
import com.omnibus.omnibus.data.bookprogress.readingRecord
import com.omnibus.omnibus.data.library.PositionConfidence
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.ResolvedPosition
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

private fun resolved(ordinal: Int?) = ResolvedPosition(
    chapterOrdinal = ordinal,
    chaptersTotal = null,
    chapterTitle = null,
    percentThroughBook = null,
    confidence = PositionConfidence.High,
)

private fun alignment(
    ebook: List<BookChapter> = emptyList(),
    audioTotal: Long? = null,
) = BookChaptersResult.Success(ebookChapters = ebook, audioChapters = emptyList(), audioFileId = null, audioTotalSeconds = audioTotal)

class BookReadingProgressTest : DescribeSpec({

    describe("the chapter containing a fraction") {
        it("TC-24: is the last to start by that fraction, for each kind of list") {
            checkAll(
                Exhaustive.of(
                    Triple(listOf(BookChapter(0, null, .5f), BookChapter(1, null, 0f), BookChapter(2, null, .25f)), .3f, 2),
                    Triple(chapters(0f, .5f), .49995f, 1),
                    Triple(chapters(0f, .5f), 1f, 1),
                    Triple(listOf(BookChapter(0, null, .5f), BookChapter(1, null, .5f)), .6f, 1),
                    Triple(emptyList(), .5f, null),
                    Triple(chapters(0f, .5f), Float.NaN, null),
                    Triple(chapters(.2f, .5f), .1f, null),
                ),
            ) { (list, fraction, index) ->
                list.chapterAt(fraction)?.index shouldBe index
            }
        }
    }

    describe("the progress of a book") {
        context("when there are no records") {
            it("TC-27: is missing") {
                bookReadingProgress(emptyList(), null) shouldBe null
            }
        }

        it("TC-27: has the fraction of each kind of reading percent") {
            checkAll(
                Exhaustive.of(
                    50.0 to .5f,
                    null to null,
                    Double.NaN to null,
                    120.0 to 1f,
                    -5.0 to 0f,
                ),
            ) { (percent, fraction) ->
                bookReadingProgress(listOf(readingRecord(percent, 100)), null)?.fraction shouldBe fraction
            }
        }

        it("TC-30: has an audio fraction only for each record with a known total") {
            checkAll(
                Exhaustive.of(
                    Triple(listeningRecord(3600.0, 100, totalSeconds = 7200.0), null, .5f),
                    Triple(listeningRecord(3600.0, 100), alignment(audioTotal = 7200), .5f),
                    Triple(listeningRecord(3600.0, 100), null, null),
                    Triple(listeningRecord(3600.0, 100, totalSeconds = 0.0), null, null),
                    Triple(listeningRecord(9000.0, 100, totalSeconds = 7200.0), null, 1f),
                ),
            ) { (record, chapters, fraction) ->
                bookReadingProgress(listOf(record), chapters)?.fraction shouldBe fraction
            }
        }

        it("TC-31: follows the newest record by its clock, a tie going to Reading") {
            checkAll(
                Exhaustive.of(
                    listOf(listeningRecord(3600.0, 100), readingRecord(80.0, 200)) to ProgressFormat.Reading,
                    listOf(listeningRecord(3600.0, 300), readingRecord(80.0, 200)) to ProgressFormat.Listening,
                    listOf(listeningRecord(3600.0, 200), readingRecord(80.0, 200)) to ProgressFormat.Reading,
                    listOf(
                        readingRecord(10.0, 0, updatedAt = 99, clientUpdatedAt = 10),
                        listeningRecord(3600.0, 0, updatedAt = 20, clientUpdatedAt = null),
                    ) to ProgressFormat.Listening,
                ),
            ) { (records, medium) ->
                bookReadingProgress(records, null)?.latestMedium shouldBe medium
            }
        }

        it("TC-34: numbers the chapter from the server, else the alignment, never for audio") {
            val alignment = alignment(ebook = chapters(0f, .25f, .5f, .75f))

            checkAll(
                Exhaustive.of(
                    listOf(readingRecord(60.0, 100, resolved = resolved(6))) to 6,
                    listOf(readingRecord(60.0, 100, resolved = null)) to 3,
                    listOf(readingRecord(60.0, 100, resolved = resolved(0))) to 3,
                    listOf(listeningRecord(3600.0, 100), readingRecord(50.0, 50)) to null,
                ),
            ) { (records, number) ->
                bookReadingProgress(records, alignment)?.chapterNumber shouldBe number
            }
        }

        context("when a reading record is newer than a listening one") {
            it("TC-35: keeps the audio fields") {
                val records = listOf(listeningRecord(3600.0, 100, totalSeconds = 7200.0, bookFileId = 7L), readingRecord(80.0, 200))
                val progress = bookReadingProgress(records, null)

                progress?.audioFileId shouldBe 7L
                progress?.audioElapsedSeconds shouldBe 3600L
                progress?.audioTotalSeconds shouldBe 7200L
            }
        }
    }
})
