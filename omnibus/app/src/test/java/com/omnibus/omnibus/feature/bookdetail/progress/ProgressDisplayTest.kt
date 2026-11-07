package com.omnibus.omnibus.feature.bookdetail.progress

import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.chapters
import com.omnibus.omnibus.data.bookprogress.listeningRecord
import com.omnibus.omnibus.data.bookprogress.readingRecord
import com.omnibus.omnibus.data.library.PositionConfidence
import com.omnibus.omnibus.data.library.ProgressRecord
import com.omnibus.omnibus.data.library.ResolvedPosition
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.readstatus.randomReadStatusRecord
import com.omnibus.omnibus.testing.randomBoolean
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of

private fun known(status: ReadStatus) = ReadStatusState.Known(randomReadStatusRecord().copy(status = status))

/** A session of [seconds] with no fractions. */
private fun session(seconds: Long) = ReadingSession(0L, seconds, null, null)

class ProgressDisplayTest : DescribeSpec({

    /** The bar for a reader on [records], whose status is Reading. */
    fun bar(
        records: List<ProgressRecord>,
        chapters: BookChaptersResult.Success? = null,
        sessions: List<ReadingSession>? = null,
        hasEbook: Boolean = randomBoolean(),
    ) = progressDisplay(known(ReadStatus.Reading), hasEbook, records, chapters, sessions)
        .shouldBeInstanceOf<ProgressDisplay.Bar>()

    describe("which statuses show progress") {
        it("TC-36: is hidden for each status with no place to show") {
            val place = listOf(readingRecord(62.0, 100))

            checkAll(
                Exhaustive.of(
                    ReadStatusState.Unknown to place,
                    ReadStatusState.Failed to place,
                    known(ReadStatus.Unread) to place,
                    known(ReadStatus.Finished) to place,
                    known(ReadStatus.Reading) to emptyList(),
                ),
            ) { (status, records) ->
                progressDisplay(status, randomBoolean(), records, null, null) shouldBe ProgressDisplay.Hidden
            }
        }

        context("when the status is Reading") {
            context("when the position has no honest fraction") {
                it("TC-38: is In progress, for each medium") {
                    checkAll(Exhaustive.of(readingRecord(null, 100), listeningRecord(3600.0, 100))) { record ->
                        progressDisplay(known(ReadStatus.Reading), randomBoolean(), listOf(record), null, null) shouldBe
                            ProgressDisplay.InProgress
                    }
                }
            }
        }
    }

    describe("the bar") {
        it("TC-40: floors each percent, a float's rounding error aside") {
            checkAll(Exhaustive.of(62.9 to 62, 58.0 to 58)) { (percent, floored) ->
                bar(listOf(readingRecord(percent, 100))).percent shouldBe floored
            }
        }

        context("when the chapter is known") {
            it("TC-40: flags the chapter") {
                val position = ResolvedPosition(6, null, null, null, PositionConfidence.High)

                bar(listOf(readingRecord(62.9, 100, resolved = position))).flag shouldBe FlagLabel.Chapter(6)
            }
        }

        context("when no chapter is known") {
            it("TC-41: flags the percentage") {
                bar(listOf(readingRecord(62.0, 100))).flag shouldBe FlagLabel.Percent(62)
            }
        }

        context("when the position is audio") {
            it("TC-42: flags the elapsed time") {
                bar(listOf(listeningRecord(9060.0, 100, totalSeconds = 17340.0))).flag shouldBe FlagLabel.Elapsed(9060)
            }

            it("TC-42: says what is left of the total, for each position up to past the end") {
                checkAll(Exhaustive.of(9060.0 to 8280L, 20000.0 to 0L)) { (position, left) ->
                    bar(listOf(listeningRecord(position, 100, totalSeconds = 17340.0))).tail shouldBe LineTail.LeftOf(left, 17340)
                }
            }
        }

        it("TC-43, TC-45: says the time left only from a minute, inside the book, for each kind of sessions") {
            checkAll(
                Exhaustive.of(
                    Triple(50.0, null, null),
                    Triple(50.0, emptyList(), null),
                    Triple(50.0, listOf(session(59)), null),
                    Triple(50.0, listOf(session(60)), LineTail.LeftAtPace(60)),
                    Triple(0.0, listOf(session(3600)), null),
                    Triple(100.0, listOf(session(3600)), null),
                ),
            ) { (percent, sessions, tail) ->
                bar(listOf(readingRecord(percent, 100)), sessions = sessions).tail shouldBe tail
            }
        }

        it("TC-46: takes its segments from the chapters of the medium shown, for each place") {
            val both = BookChaptersResult.Success(
                ebookChapters = chapters(0f, .5f),
                audioChapters = chapters(0f, .25f, .75f),
                audioFileId = 7L,
                audioTotalSeconds = 7200L,
            )
            val listening = listOf(listeningRecord(3600.0, 100, totalSeconds = 7200.0))
            val reading = listOf(readingRecord(50.0, 100))

            checkAll(
                Exhaustive.of(
                    Triple(listening, true, 3),
                    Triple(reading, true, 2),
                    Triple(reading, false, 3),
                ),
            ) { (records, hasEbook, segments) ->
                bar(records, both, hasEbook = hasEbook).segments.size shouldBe segments
            }
        }
    }

    describe("the offline note") {
        it("TC-74: shows only for a Reading status, across every status, every connection") {
            checkAll(
                Exhaustive.of(
                    ReadStatusState.Unknown to false,
                    ReadStatusState.Failed to false,
                    known(ReadStatus.Unread) to false,
                    known(ReadStatus.Reading) to true,
                    known(ReadStatus.Finished) to false,
                ),
                Exhaustive.boolean(),
            ) { (status, reading), offline ->
                showsOfflineNote(status, offline) shouldBe (reading && offline)
            }
        }
    }
})
