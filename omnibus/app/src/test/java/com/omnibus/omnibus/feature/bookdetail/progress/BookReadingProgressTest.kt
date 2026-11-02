package com.omnibus.omnibus.feature.bookdetail.progress

import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.chapters
import com.omnibus.omnibus.data.bookprogress.listeningRecord
import com.omnibus.omnibus.data.bookprogress.readingRecord
import com.omnibus.omnibus.data.library.PositionConfidence
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.ResolvedPosition
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

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

    describe("chapter lookup") {

        it("TC-24: chapterAt picks the chapter containing the fraction") {
            emptyList<BookChapter>().chapterAt(.5f) shouldBe null
            chapters(0f, .5f).chapterAt(Float.NaN) shouldBe null
            chapters(.2f, .5f).chapterAt(.1f) shouldBe null

            val unsorted = listOf(BookChapter(0, null, .5f), BookChapter(1, null, 0f), BookChapter(2, null, .25f))
            unsorted.chapterAt(.3f) shouldBe BookChapter(2, null, .25f)
            chapters(0f, .5f).chapterAt(.49995f) shouldBe BookChapter(1, null, .5f)
            chapters(0f, .5f).chapterAt(1f) shouldBe BookChapter(1, null, .5f)

            val tied = listOf(BookChapter(0, null, .5f), BookChapter(1, null, .5f))
            tied.chapterAt(.6f) shouldBe BookChapter(1, null, .5f)
        }
    }

    describe("the progress of a book") {

        it("TC-27: a reading record gives the fraction") {
            bookReadingProgress(emptyList(), null) shouldBe null

            val reading = bookReadingProgress(listOf(readingRecord(62.4, 100)), null)!!
            reading.fraction shouldBe (.624f plusOrMinus 1e-4f)
            reading.latestMedium shouldBe ProgressFormat.Reading
            reading.audioFileId shouldBe null
            reading.audioElapsedSeconds shouldBe null
            reading.audioTotalSeconds shouldBe null

            bookReadingProgress(listOf(readingRecord(null, 100)), null)!!.let {
                it.fraction shouldBe null
                it.latestMedium shouldBe ProgressFormat.Reading
            }
            bookReadingProgress(listOf(readingRecord(Double.NaN, 100)), null)!!.fraction shouldBe null
            bookReadingProgress(listOf(readingRecord(120.0, 100)), null)!!.fraction shouldBe (1f plusOrMinus 1e-4f)
            bookReadingProgress(listOf(readingRecord(-5.0, 100)), null)!!.fraction shouldBe (0f plusOrMinus 1e-4f)
        }

        it("TC-30: audio fraction needs a known total") {
            bookReadingProgress(listOf(listeningRecord(3600.0, 100, totalSeconds = 7200.0)), null)!!.fraction shouldBe
                (.5f plusOrMinus 1e-4f)
            bookReadingProgress(listOf(listeningRecord(3600.0, 100)), alignment(audioTotal = 7200))!!.fraction shouldBe
                (.5f plusOrMinus 1e-4f)
            bookReadingProgress(listOf(listeningRecord(3600.0, 100)), null)!!.fraction shouldBe null
            bookReadingProgress(listOf(listeningRecord(3600.0, 100, totalSeconds = 0.0)), null)!!.fraction shouldBe null
            bookReadingProgress(listOf(listeningRecord(9000.0, 100, totalSeconds = 7200.0)), null)!!.fraction shouldBe
                (1f plusOrMinus 1e-4f)
        }

        it("TC-31: the newest record speaks for the book") {
            val readingNewer = bookReadingProgress(
                listOf(listeningRecord(3600.0, 100, totalSeconds = 7200.0), readingRecord(80.0, 200, resolved = resolved(3))),
                null,
            )!!
            readingNewer.latestMedium shouldBe ProgressFormat.Reading
            readingNewer.fraction shouldBe (.8f plusOrMinus 1e-4f)
            readingNewer.chapterNumber shouldBe 3

            val listeningNewer = bookReadingProgress(
                listOf(listeningRecord(3600.0, 300, totalSeconds = 7200.0), readingRecord(80.0, 200)),
                null,
            )!!
            listeningNewer.latestMedium shouldBe ProgressFormat.Listening
            listeningNewer.fraction shouldBe (.5f plusOrMinus 1e-4f)

            val tie = bookReadingProgress(
                listOf(listeningRecord(3600.0, 200, totalSeconds = 7200.0), readingRecord(80.0, 200)),
                null,
            )!!
            tie.latestMedium shouldBe ProgressFormat.Reading

            val byClock = bookReadingProgress(
                listOf(
                    readingRecord(10.0, 0, updatedAt = 99, clientUpdatedAt = 10),
                    listeningRecord(3600.0, 0, updatedAt = 20, clientUpdatedAt = null, totalSeconds = 7200.0),
                ),
                null,
            )!!
            byClock.latestMedium shouldBe ProgressFormat.Listening

            val cfiOnly = bookReadingProgress(
                listOf(readingRecord(null, 300, cfi = "epubcfi(/6/8)"), listeningRecord(3600.0, 100, totalSeconds = 7200.0)),
                null,
            )!!
            cfiOnly.fraction shouldBe null
            cfiOnly.latestMedium shouldBe ProgressFormat.Reading
        }

        it("TC-34: chapter number comes from the server, else from the alignment") {
            val fromServer = bookReadingProgress(listOf(readingRecord(60.0, 100, resolved = resolved(6))), null)!!
            fromServer.chapterNumber shouldBe 6

            val ebook = chapters(0f, .25f, .5f, .75f)
            val alignmentChapters = alignment(ebook = ebook)
            bookReadingProgress(listOf(readingRecord(60.0, 100, resolved = null)), alignmentChapters)!!.chapterNumber shouldBe
                (ebook.chapterAt(.6f)!!.index + 1)
            bookReadingProgress(listOf(readingRecord(60.0, 100, resolved = resolved(0))), alignmentChapters)!!.chapterNumber shouldBe
                (ebook.chapterAt(.6f)!!.index + 1)
            bookReadingProgress(listOf(readingRecord(null, 100)), alignmentChapters)!!.chapterNumber shouldBe null
            bookReadingProgress(listOf(readingRecord(60.0, 100)), null)!!.chapterNumber shouldBe null
            bookReadingProgress(
                listOf(listeningRecord(3600.0, 100, totalSeconds = 7200.0), readingRecord(50.0, 50)),
                alignmentChapters,
            )!!.chapterNumber shouldBe null
            bookReadingProgress(listOf(listeningRecord(3600.0, 100, totalSeconds = 7200.0)), alignmentChapters)!!
                .chapterNumber shouldBe null
        }

        it("TC-35: audio fields survive a newer reading record") {
            val progress = bookReadingProgress(
                listOf(listeningRecord(3600.0, 100, totalSeconds = 7200.0), readingRecord(80.0, 200)),
                null,
            )!!
            progress.audioFileId shouldBe 7L
            progress.audioElapsedSeconds shouldBe 3600L
            progress.audioTotalSeconds shouldBe 7200L

            val fromChapters = bookReadingProgress(
                listOf(listeningRecord(3600.0, 100, totalSeconds = null), readingRecord(80.0, 200)),
                alignment(audioTotal = 7200),
            )!!
            fromChapters.audioTotalSeconds shouldBe 7200L
        }
    }
})
