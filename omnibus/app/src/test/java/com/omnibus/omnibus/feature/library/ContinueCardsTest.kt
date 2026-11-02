package com.omnibus.omnibus.feature.library

import com.omnibus.omnibus.data.library.BookFile
import com.omnibus.omnibus.data.library.PositionConfidence
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.ResolvedPosition
import com.omnibus.omnibus.data.library.ResumePoint
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.data.library.testResumePoint
import com.omnibus.omnibus.feature.library.PositionLabel.Chapter
import com.omnibus.omnibus.feature.library.PositionLabel.InProgress
import com.omnibus.omnibus.feature.library.PositionLabel.Part
import com.omnibus.omnibus.feature.library.PositionLabel.PercentRead
import com.omnibus.omnibus.feature.library.PositionLabel.TimeLeft
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

/** The fixed "now" for relative-age rows, in milliseconds. */
private const val NOW = 1_780_000_000_000L

private infix fun Double?.shouldBeApprox(expected: Double?) {
    if (expected == null) {
        this shouldBe null
    } else {
        requireNotNull(this) { "expected $expected but was null" } shouldBe (expected plusOrMinus 1e-9)
    }
}

class ContinueCardsTest : DescribeSpec({

    describe("resumeMedium") {
        val rows = listOf(
            Triple(emptyList<String>(), ProgressFormat.Listening, ProgressFormat.Listening),
            Triple(emptyList(), ProgressFormat.Reading, ProgressFormat.Reading),
            Triple(listOf("m4b"), ProgressFormat.Listening, ProgressFormat.Listening),
            Triple(listOf("epub"), ProgressFormat.Listening, ProgressFormat.Reading),
            Triple(listOf("m4b"), ProgressFormat.Reading, ProgressFormat.Listening),
            Triple(listOf("epub", "m4b"), ProgressFormat.Reading, ProgressFormat.Reading),
            Triple(listOf("epub", "m4b"), ProgressFormat.Listening, ProgressFormat.Listening),
        )
        rows.forEach { (formats, requested, expected) ->
            it("#22 TC-5: formats $formats requested $requested presents $expected") {
                testBook(1, formats = formats).resumeMedium(requested) shouldBe expected
            }
        }
    }

    describe("fraction") {
        val listening = ProgressFormat.Listening
        val reading = ProgressFormat.Reading
        val rows = listOf(
            Triple("listening 300 of 1200", testResumePoint(format = listening, audioPositionSeconds = 300.0, totalDurationSeconds = 1200.0) to listening, 0.25),
            Triple("listening past the end clamps to 1", testResumePoint(format = listening, audioPositionSeconds = 1500.0, totalDurationSeconds = 1200.0) to listening, 1.0),
            Triple("listening before the start clamps to 0", testResumePoint(format = listening, audioPositionSeconds = -10.0, totalDurationSeconds = 1200.0) to listening, 0.0),
            Triple("listening with no duration", testResumePoint(format = listening, audioPositionSeconds = 300.0) to listening, null),
            Triple("listening with zero duration", testResumePoint(format = listening, audioPositionSeconds = 300.0, totalDurationSeconds = 0.0) to listening, null),
            Triple("listening with no position", testResumePoint(format = listening, totalDurationSeconds = 1200.0) to listening, null),
            Triple("reading percent 42 with no CFI", testResumePoint(progressPercent = 42.0) to reading, 0.42),
            Triple("reading percent 140 clamps to 1", testResumePoint(progressPercent = 140.0) to reading, 1.0),
            Triple("reading percent -5 clamps to 0", testResumePoint(progressPercent = -5.0) to reading, 0.0),
            Triple("reading CFI with no percent", testResumePoint(epubCfi = "epubcfi(/6/4)") to reading, null),
            Triple("listening record presented as reading", testResumePoint(format = listening, progressPercent = 50.0, audioPositionSeconds = 300.0, totalDurationSeconds = 1200.0) to reading, null),
            Triple("reading record presented as listening", testResumePoint(progressPercent = 50.0, audioPositionSeconds = 300.0, totalDurationSeconds = 1200.0) to listening, null),
        )
        rows.forEach { (name, pair, expected) ->
            it("#22 TC-6: $name") {
                val (point, presented) = pair
                point.fraction(presented).shouldBeApprox(expected)
            }
        }
    }

    describe("positionLabelOf") {
        val listening = ProgressFormat.Listening
        val reading = ProgressFormat.Reading
        val high = ResolvedPosition(chapterOrdinal = 3, chaptersTotal = 12, chapterTitle = null, percentThroughBook = null, confidence = PositionConfidence.High)
        val timed = { rate: Double? ->
            testResumePoint(
                format = listening,
                audioPositionSeconds = 600.0,
                totalDurationSeconds = 3600.0,
                playbackRate = rate,
            ) to listening
        }
        val rows: List<Pair<String, Pair<ResumePoint, ProgressFormat>>> = listOf(
            "high chapter 3 of 12 wins over part and time" to
                (testResumePoint(format = listening, resolved = high, audioPart = 2, audioPartCount = 4, audioPositionSeconds = 600.0, totalDurationSeconds = 3600.0) to listening),
            "low confidence falls through to time left" to
                (testResumePoint(format = listening, resolved = high.copy(confidence = PositionConfidence.Low), audioPositionSeconds = 600.0, totalDurationSeconds = 3600.0) to listening),
            "chapter ordinal null falls through to time left" to
                (testResumePoint(format = listening, resolved = high.copy(chapterOrdinal = null), audioPositionSeconds = 600.0, totalDurationSeconds = 3600.0) to listening),
            "chapter total null falls through to time left" to
                (testResumePoint(format = listening, resolved = high.copy(chaptersTotal = null), audioPositionSeconds = 600.0, totalDurationSeconds = 3600.0) to listening),
            "part 2 of 4 with no chapter" to
                (testResumePoint(format = listening, audioPart = 2, audioPartCount = 4, audioPositionSeconds = 600.0, totalDurationSeconds = 3600.0) to listening),
            "part with count null falls through to time left" to
                (testResumePoint(format = listening, audioPart = 2, audioPositionSeconds = 600.0, totalDurationSeconds = 3600.0) to listening),
            "no rate counts as 1" to timed(null),
            "rate 1.5" to timed(1.5),
            "rate 2.0" to timed(2.0),
            "rate 0 counts as 1" to timed(0.0),
            "rate -1 counts as 1" to timed(-1.0),
            "rate NaN counts as 1" to timed(Double.NaN),
            "rate infinity counts as 1" to timed(Double.POSITIVE_INFINITY),
            "position past the duration gives 0 left" to
                (testResumePoint(format = listening, audioPositionSeconds = 4000.0, totalDurationSeconds = 3600.0) to listening),
            "no duration is in progress" to
                (testResumePoint(format = listening, audioPositionSeconds = 600.0) to listening),
            "reading at 0.424 is 42% read" to
                (testResumePoint(progressPercent = 42.4) to reading),
            "reading at 0.996 is 100% read" to
                (testResumePoint(progressPercent = 99.6) to reading),
            "reading at 0 is 0% read" to
                (testResumePoint(progressPercent = 0.0) to reading),
            "reading with only a CFI is in progress" to
                (testResumePoint(epubCfi = "epubcfi(/6/4)") to reading),
            "medium mismatch is in progress" to
                (testResumePoint(format = listening, progressPercent = 50.0, audioPositionSeconds = 600.0, totalDurationSeconds = 3600.0) to reading),
        )
        val expectations: Map<String, PositionLabel> = mapOf(
            "high chapter 3 of 12 wins over part and time" to Chapter(3, 12),
            "low confidence falls through to time left" to TimeLeft(3000),
            "chapter ordinal null falls through to time left" to TimeLeft(3000),
            "chapter total null falls through to time left" to TimeLeft(3000),
            "part 2 of 4 with no chapter" to Part(2, 4),
            "part with count null falls through to time left" to TimeLeft(3000),
            "no rate counts as 1" to TimeLeft(3000),
            "rate 1.5" to TimeLeft(2000),
            "rate 2.0" to TimeLeft(1500),
            "rate 0 counts as 1" to TimeLeft(3000),
            "rate -1 counts as 1" to TimeLeft(3000),
            "rate NaN counts as 1" to TimeLeft(3000),
            "rate infinity counts as 1" to TimeLeft(3000),
            "position past the duration gives 0 left" to TimeLeft(0),
            "no duration is in progress" to InProgress,
            "reading at 0.424 is 42% read" to PercentRead(42),
            "reading at 0.996 is 100% read" to PercentRead(100),
            "reading at 0 is 0% read" to PercentRead(0),
            "reading with only a CFI is in progress" to InProgress,
            "medium mismatch is in progress" to InProgress,
        )
        rows.forEach { (name, pair) ->
            it("#22 TC-7: $name") {
                val (point, presented) = pair
                positionLabelOf(point, presented) shouldBe expectations.getValue(name)
            }
        }
    }

    describe("formatDuration") {
        val rows = listOf(
            11100L to "3h 5m",
            7200L to "2h",
            3600L to "1h",
            3661L to "1h 1m",
            3599L to "59m",
            2700L to "45m",
            60L to "1m",
            59L to "59s",
            30L to "30s",
            0L to "0m",
            -5L to "0m",
        )
        rows.forEach { (seconds, expected) ->
            it("#22 TC-8: $seconds seconds reads as $expected") {
                formatDuration(seconds) shouldBe expected
            }
        }
    }

    describe("relativeAgeOf") {
        val nowSeconds = NOW / 1000
        val rows = listOf(
            "a future timestamp is just now" to (nowSeconds + 60 to RelativeAge.JustNow),
            "a timestamp of now is just now" to (nowSeconds to RelativeAge.JustNow),
            "1 s old" to (nowSeconds - 1 to RelativeAge.Seconds(1)),
            "59 s old" to (nowSeconds - 59 to RelativeAge.Seconds(59)),
            "60 s old" to (nowSeconds - 60 to RelativeAge.Minutes(1)),
            "3599 s old" to (nowSeconds - 3599 to RelativeAge.Minutes(59)),
            "3600 s old" to (nowSeconds - 3600 to RelativeAge.Hours(1)),
            "86399 s old" to (nowSeconds - 86_399 to RelativeAge.Hours(23)),
            "1 day old" to (nowSeconds - 86_400 to RelativeAge.Days(1)),
            "6 days old" to (nowSeconds - 6 * 86_400 to RelativeAge.Days(6)),
            "7 days old" to (nowSeconds - 7 * 86_400 to RelativeAge.Weeks(1)),
            "29 days old" to (nowSeconds - 29 * 86_400 to RelativeAge.Weeks(4)),
            "30 days old" to (nowSeconds - 30 * 86_400 to RelativeAge.Months(1)),
            "364 days old" to (nowSeconds - 364 * 86_400 to RelativeAge.Months(12)),
            "365 days old" to (nowSeconds - 365 * 86_400 to RelativeAge.Years(1)),
            "updated at the epoch" to (0L to RelativeAge.Years(56)),
        )
        rows.forEach { (name, row) ->
            val (updatedAt, expected) = row
            it("#22 TC-9: $name") {
                relativeAgeOf(updatedAt, NOW) shouldBe expected
            }
        }
        it("#22 TC-9: 500 ms after the update is just now") {
            relativeAgeOf(nowSeconds, NOW + 500) shouldBe RelativeAge.JustNow
        }
    }

    describe("card identity") {
        it("#22 TC-10: a reading record is epub and a listening record is audio") {
            testResumePoint(book = testBook(1, uniqueIdentifier = "b1"), format = ProgressFormat.Reading).cardId shouldBe "b1:epub"
            testResumePoint(book = testBook(1, uniqueIdentifier = "b1"), format = ProgressFormat.Listening).cardId shouldBe "b1:audio"
        }

        it("#22 TC-10: reading and listening to one book give two cards with their media") {
            val book = testBook(1, uniqueIdentifier = "b1", formats = listOf("epub", "m4b"))
            val cards = continueCardsOf(
                listOf(
                    testResumePoint(book = book, format = ProgressFormat.Reading, progressPercent = 10.0),
                    testResumePoint(book = book, format = ProgressFormat.Listening, audioPositionSeconds = 60.0, totalDurationSeconds = 600.0),
                ),
                NOW,
            )

            cards.map { it.id } shouldBe listOf("b1:epub", "b1:audio")
            cards.map { it.medium } shouldBe listOf(ProgressFormat.Reading, ProgressFormat.Listening)
        }

        it("#22 TC-10: two points with the same book and format give one card, the first") {
            val cards = continueCardsOf(
                listOf(
                    testResumePoint(book = testBook(1, title = "First"), progressPercent = 10.0),
                    testResumePoint(book = testBook(1, title = "Second"), progressPercent = 20.0),
                ),
                NOW,
            )

            cards.size shouldBe 1
            cards.single().book.displayTitle shouldBe "First"
        }
    }

    describe("continueCardsOf") {
        it("#22 TC-11: keeps the first five points in server order") {
            val points = (1L..7L).map { testResumePoint(book = testBook(it)) }

            continueCardsOf(points, NOW).map { it.book.id } shouldBe listOf(1L, 2L, 3L, 4L, 5L)
        }

        it("#22 TC-11: an empty list gives no cards") {
            continueCardsOf(emptyList(), NOW) shouldBe emptyList()
        }
    }

    describe("resumeFileId") {
        val book = testBook(1, formats = listOf("m4b"))
            .copy(
                bookFiles = listOf(
                    BookFile(10, "m4b", "a.m4b", 0, null, 0, null),
                    BookFile(20, "m4b", "b.m4b", 1, null, 0, null),
                ),
            )

        it("#22 TC-12: a listening card carries the saved file, not the first") {
            val card = continueCardsOf(
                listOf(testResumePoint(book = book, format = ProgressFormat.Listening, bookFileId = 20L)),
                NOW,
            ).single()

            card.resumeFileId shouldBe 20L
        }

        it("#22 TC-12: a reading record on an audio-only book carries no file id") {
            val audioOnly = testBook(2, formats = listOf("m4b"))
            val card = continueCardsOf(
                listOf(testResumePoint(book = audioOnly, format = ProgressFormat.Reading, bookFileId = 7L)),
                NOW,
            ).single()

            card.medium shouldBe ProgressFormat.Listening
            card.resumeFileId shouldBe null
        }
    }

    describe("selectionAfterRefresh") {
        val rows = listOf(
            Triple("b2:epub", listOf("b0:epub", "b1:epub", "b2:epub"), "b2:epub"),
            Triple("b9:epub", listOf("b0:epub", "b1:epub"), "b0:epub"),
            Triple(null, listOf("b0:epub"), "b0:epub"),
            Triple("b0:epub", emptyList<String>(), null),
        )
        rows.forEach { (current, ids, expected) ->
            it("#22 TC-13: current $current in $ids selects $expected") {
                selectionAfterRefresh(current, ids) shouldBe expected
            }
        }
    }

    describe("settledCardId") {
        val rows = listOf(
            Triple(0, listOf("a", "b"), "a"),
            Triple(1, listOf("a", "b"), "b"),
            Triple(2, listOf("a", "b"), null),
            Triple(-1, listOf("a", "b"), null),
            Triple(0, emptyList<String>(), null),
        )
        rows.forEach { (index, ids, expected) ->
            it("#22 TC-14: settled $index of $ids gives $expected") {
                settledCardId(index, ids) shouldBe expected
            }
        }
    }
})
