package com.omnibus.omnibus.feature.bookdetail.progress

import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.chapters
import com.omnibus.omnibus.data.bookprogress.listeningRecord
import com.omnibus.omnibus.data.bookprogress.readingRecord
import com.omnibus.omnibus.data.library.PositionConfidence
import com.omnibus.omnibus.data.library.ResolvedPosition
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusRecord
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.math.abs

private fun known(status: ReadStatus) = ReadStatusState.Known(ReadStatusRecord(status, 1L, null))

private val reading = known(ReadStatus.Reading)

private fun resolved(ordinal: Int) = ResolvedPosition(ordinal, null, null, null, PositionConfidence.High)

private fun ebookChapters(vararg starts: Float) = BookChaptersResult.Success(
    ebookChapters = chapters(*starts),
    audioChapters = emptyList(),
    audioFileId = null,
    audioTotalSeconds = null,
)

/** A session of [seconds] with no fractions. */
private fun session(seconds: Long) = ReadingSession(0L, seconds, null, null)

private fun List<Float>.shouldBeAbout(expected: List<Float>) {
    size shouldBe expected.size
    forEachIndexed { i, value -> abs(value - expected[i]) shouldBe (0f plusOrMinus 1e-4f) }
}

class ProgressDisplayTest : DescribeSpec({

    describe("which statuses show progress") {

        it("TC-36: only a Reading status with a position shows progress") {
            val record = readingRecord(62.0, 100)
            listOf(
                ReadStatusState.Unknown,
                ReadStatusState.Failed,
                known(ReadStatus.Unread),
                known(ReadStatus.Finished),
            ).forEach { status ->
                progressDisplay(status, true, listOf(record), null, null) shouldBe ProgressDisplay.Hidden
            }
            progressDisplay(reading, true, emptyList(), null, null) shouldBe ProgressDisplay.Hidden
            progressDisplay(reading, true, listOf(record), null, null).shouldBeInstanceOf<ProgressDisplay.Bar>()
        }

        it("TC-38: no honest fraction gives In progress") {
            progressDisplay(reading, true, listOf(readingRecord(null, 100)), null, null) shouldBe ProgressDisplay.InProgress
            progressDisplay(reading, false, listOf(listeningRecord(3600.0, 100)), null, null) shouldBe
                ProgressDisplay.InProgress
        }
    }

    describe("the bar") {

        it("TC-40: an ebook bar has a chapter flag, the floored percent and the time left") {
            val chapters = ebookChapters(0f, .25f, .5f)
            val record = readingRecord(62.9, 100, resolved = resolved(6))
            val bar = progressDisplay(reading, true, listOf(record), chapters, listOf(session(3600))) as ProgressDisplay.Bar

            bar.percent shouldBe 62
            bar.isAudio shouldBe false
            bar.flag shouldBe FlagLabel.Chapter(6)
            val left = bar.tail.shouldBeInstanceOf<LineTail.LeftAtPace>().seconds
            (left in 2120L..2126L) shouldBe true

            (progressDisplay(reading, true, listOf(readingRecord(58.0, 100)), null, null) as ProgressDisplay.Bar).percent shouldBe 58
        }

        it("TC-41: the flag falls back to the percentage when no chapter is known") {
            val bar = progressDisplay(reading, true, listOf(readingRecord(62.0, 100)), null, null) as ProgressDisplay.Bar
            bar.flag shouldBe FlagLabel.Percent(62)
        }

        it("TC-42: an audio bar shows the elapsed time and what is left of the total") {
            val bar = progressDisplay(
                reading,
                true,
                listOf(listeningRecord(9060.0, 100, totalSeconds = 17340.0)),
                null,
                null,
            ) as ProgressDisplay.Bar
            bar.isAudio shouldBe true
            bar.flag shouldBe FlagLabel.Elapsed(9060)
            bar.tail shouldBe LineTail.LeftOf(8280, 17340)

            val beyond = progressDisplay(
                reading,
                true,
                listOf(listeningRecord(20000.0, 100, totalSeconds = 17340.0)),
                null,
                null,
            ) as ProgressDisplay.Bar
            beyond.flag shouldBe FlagLabel.Elapsed(20000)
            beyond.tail shouldBe LineTail.LeftOf(0, 17340)
        }

        it("TC-43: no pace, or less than a minute left, means no tail") {
            val record = listOf(readingRecord(50.0, 100))
            (progressDisplay(reading, true, record, null, null) as ProgressDisplay.Bar).tail shouldBe null
            (progressDisplay(reading, true, record, null, emptyList()) as ProgressDisplay.Bar).tail shouldBe null
            (progressDisplay(reading, true, record, null, listOf(session(59))) as ProgressDisplay.Bar).tail shouldBe null
            (progressDisplay(reading, true, record, null, listOf(session(60))) as ProgressDisplay.Bar).tail shouldBe
                LineTail.LeftAtPace(60)
        }

        it("TC-45: the bar at 0% and 100% has no time left") {
            val sessions = listOf(session(3600))
            val atStart = progressDisplay(reading, true, listOf(readingRecord(0.0, 100)), null, sessions) as ProgressDisplay.Bar
            atStart.percent shouldBe 0
            atStart.tail shouldBe null

            val atEnd = progressDisplay(reading, true, listOf(readingRecord(100.0, 100)), null, sessions) as ProgressDisplay.Bar
            atEnd.percent shouldBe 100
            atEnd.tail shouldBe null
        }

        it("TC-46: segments come from the chapters of the medium shown") {
            val chapters = BookChaptersResult.Success(
                ebookChapters = chapters(0f, .5f),
                audioChapters = chapters(0f, .25f, .75f),
                audioFileId = 7L,
                audioTotalSeconds = 7200L,
            )
            val audioPosition = listOf(listeningRecord(3600.0, 100, totalSeconds = 7200.0))
            val ebookPosition = listOf(readingRecord(50.0, 100))

            val audioWinner = progressDisplay(reading, true, audioPosition, chapters, null) as ProgressDisplay.Bar
            audioWinner.segments.map { it.weight }.shouldBeAbout(listOf(.25f, .5f, .25f))

            val ebookWinner = progressDisplay(reading, true, ebookPosition, chapters, null) as ProgressDisplay.Bar
            ebookWinner.segments.map { it.weight }.shouldBeAbout(listOf(.5f, .5f))

            val noEbook = progressDisplay(reading, false, ebookPosition, chapters, null) as ProgressDisplay.Bar
            noEbook.segments.map { it.weight }.shouldBeAbout(listOf(.25f, .5f, .25f))

            val unknown = progressDisplay(reading, true, ebookPosition, null, null) as ProgressDisplay.Bar
            unknown.segments.map { it.weight }.shouldBeAbout(listOf(1f))
        }
    }

    describe("the offline note") {

        it("TC-74: the offline note is only for Reading") {
            val statuses = listOf(
                ReadStatusState.Unknown to false,
                ReadStatusState.Failed to false,
                known(ReadStatus.Unread) to false,
                reading to true,
                known(ReadStatus.Finished) to false,
            )
            statuses.forEach { (status, isReading) ->
                for (offline in listOf(false, true)) {
                    showsOfflineNote(status, offline) shouldBe (isReading && offline)
                }
            }
        }
    }
})
