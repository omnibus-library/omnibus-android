package com.omnibus.omnibus.feature.bookdetail.overview.reading

import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.readstatus.randomReadStatusRecord
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

/** A sitting with no fractions, as the server sends today, so the pace comes from the book's fraction. */
private fun session(startedAt: Long = randomLong(), seconds: Long = randomLong(1L..36_000L)) =
    ReadingSession(startedAt, seconds, startFraction = null, endFraction = null)

private fun known(status: ReadStatus, finishedAt: Long? = null) =
    ReadStatusState.Known(randomReadStatusRecord().copy(status = status, finishedAtMillis = finishedAt))

private fun loaded(vararg sessions: ReadingSession) = SessionsState.Loaded(sessions.toList())

private inline fun <reified T : ReadingRow> List<ReadingRow>.only(): T = filterIsInstance<T>().single()

class ReadingCardModelTest : DescribeSpec({
    /** The card of a book that is in the library, rather than wishlist-only. */
    fun sut(
        status: ReadStatusState,
        sessions: SessionsState,
        hasFile: Boolean = randomBoolean(),
        progress: CardProgress? = randomCardProgress(),
    ) = readingCardModel(wishlistOnly = false, hasFile, status, sessions, progress)

    fun noLine(line: ReadingLine) = ReadingCardModel(line, emptyList())

    describe("the card's line") {
        context("when the book is wishlist-only") {
            it("TC-1: is the wishlist line with no rows, whatever else is known") {
                readingCardModel(
                    wishlistOnly = true,
                    hasFile = true,
                    status = known(ReadStatus.Finished, randomLong()),
                    sessions = loaded(session()),
                    progress = randomCardProgress(),
                ) shouldBe noLine(ReadingLine.Wishlist)
            }
        }

        context("when an answer is still missing") {
            it("TC-2: holds the heading alone, for an unknown status or sessions still loading") {
                val cases = Exhaustive.of(
                    ReadStatusState.Unknown to SessionsState.Failed,
                    ReadStatusState.Unknown to loaded(session()),
                    ReadStatusState.Unknown to SessionsState.Loading,
                    known(ReadStatus.Reading) to SessionsState.Loading,
                    ReadStatusState.Failed to SessionsState.Loading,
                )

                checkAll(cases) { (status, sessions) ->
                    sut(status, sessions) shouldBe ReadingCardModel(line = null, rows = emptyList())
                }
            }
        }

        context("when the status failed") {
            it("TC-3: is the error line, whatever the sessions") {
                checkAll(Exhaustive.of(SessionsState.Failed, loaded(), loaded(session()))) { sessions ->
                    sut(ReadStatusState.Failed, sessions) shouldBe noLine(ReadingLine.HistoryFailed)
                }
            }
        }

        context("when the sessions failed") {
            it("TC-5: is the error line, for any status without a finished date") {
                checkAll(Exhaustive.of(ReadStatus.Unread, ReadStatus.Reading, ReadStatus.Finished)) { status ->
                    sut(known(status), SessionsState.Failed) shouldBe noLine(ReadingLine.HistoryFailed)
                }
            }
        }

        context("when the book is unread") {
            context("when it has a file") {
                it("TC-4: is the no-reading line with no rows, even with sessions") {
                    sut(known(ReadStatus.Unread), loaded(session()), hasFile = true) shouldBe noLine(ReadingLine.NoReading)
                }
            }

            context("when it is a paper copy") {
                it("TC-6: is the paper line with no rows, even with sessions") {
                    sut(known(ReadStatus.Unread), loaded(session()), hasFile = false) shouldBe noLine(ReadingLine.PaperCopy)
                }
            }
        }

        context("when the book is being read") {
            context("when it has sessions") {
                it("TC-7: reads since the earliest start") {
                    sut(known(ReadStatus.Reading), loaded(session(startedAt = 2_000L), session(startedAt = 1_000L))).line shouldBe
                        ReadingLine.ReadingSince(1_000L)
                }
            }

            context("when it has no sessions") {
                context("when a position is saved") {
                    it("TC-8: is the sync-pending line") {
                        sut(known(ReadStatus.Reading), loaded(), progress = randomCardProgress().copy(hasPosition = true)) shouldBe
                            noLine(ReadingLine.SyncPending)
                    }
                }
            }
        }

        context("when the book is finished with a date") {
            it("TC-9: reads the finished date above the rows") {
                val finishedAt = randomLong()

                val card = sut(known(ReadStatus.Finished, finishedAt), loaded(session()))

                card.line shouldBe ReadingLine.Finished(finishedAt)
                card.rows.shouldNotBeEmpty()
            }

            context("when the sessions failed") {
                it("TC-9: still reads the finished date") {
                    val finishedAt = randomLong()

                    sut(known(ReadStatus.Finished, finishedAt), SessionsState.Failed) shouldBe noLine(ReadingLine.Finished(finishedAt))
                }
            }
        }

        context("when the book is finished without a date") {
            it("TC-10: has no line above the rows") {
                val card = sut(known(ReadStatus.Finished), loaded(session()))

                card.line shouldBe null
                card.rows.shouldNotBeEmpty()
            }
        }
    }

    describe("the card's rows") {
        it("TC-11: come in the documented order") {
            readingRows(listOf(session(seconds = 3_600)), 0.5f, isReading = true).map { it.kind } shouldContainExactly listOf(
                ReadingRowKind.Started,
                ReadingRowKind.TimeRead,
                ReadingRowKind.Pace,
                ReadingRowKind.LastRead,
                ReadingRowKind.LongestSitting,
                ReadingRowKind.TimeLeft,
            )
        }

        context("when there is no pace") {
            it("TC-12: leave out the rows that need a pace, for no fraction, a zero fraction or no time read") {
                val cases = Exhaustive.of(
                    null to session(),
                    0f to session(),
                    0.5f to session(seconds = 0),
                )

                checkAll(cases) { (fraction, sitting) ->
                    readingRows(listOf(sitting), fraction, isReading = true).map { it.kind } shouldContainExactly listOf(
                        ReadingRowKind.Started,
                        ReadingRowKind.TimeRead,
                        ReadingRowKind.LastRead,
                        ReadingRowKind.LongestSitting,
                    )
                }
            }
        }

        it("TC-13: start at the earliest start") {
            val rows = readingRows(listOf(session(startedAt = 2_000L), session(startedAt = 1_000L)), null, isReading = false)

            rows.only<ReadingRow.Started>() shouldBe ReadingRow.Started(1_000L)
        }

        it("TC-13: total the time read without overflowing an Int") {
            val big = Int.MAX_VALUE.toLong()

            readingRows(listOf(session(seconds = big), session(seconds = big)), null, isReading = false)
                .only<ReadingRow.TimeRead>() shouldBe ReadingRow.TimeRead(2 * big, 2)
        }

        it("TC-14: round the pace to the nearest percent") {
            val cases = Exhaustive.of(
                Triple(3_600L, 0.196f, 20),
                Triple(3_600L, 0.204f, 20),
                Triple(360_000L, 0.5f, 1),
            )

            checkAll(cases) { (seconds, fraction, percent) ->
                readingRows(listOf(session(seconds = seconds)), fraction, isReading = true)
                    .only<ReadingRow.Pace>() shouldBe ReadingRow.Pace(percent)
            }
        }

        context("when the pace rounds to zero") {
            it("TC-14: leave out the pace") {
                readingRows(listOf(session(seconds = 225_000)), 0.25f, isReading = true)
                    .map { it.kind } shouldNotContain ReadingRowKind.Pace
            }
        }

        context("when there is no reading time left to show") {
            it("TC-15: leave out time left, for a finished sitting or a full fraction") {
                checkAll(Exhaustive.of(false to 0.5f, true to 1f)) { (isReading, fraction) ->
                    readingRows(listOf(session()), fraction, isReading).map { it.kind } shouldNotContain ReadingRowKind.TimeLeft
                }
            }
        }

        it("TC-16: show time left only for a minute or more") {
            readingRows(listOf(session(seconds = 180)), 0.75f, isReading = true)
                .only<ReadingRow.TimeLeft>() shouldBe ReadingRow.TimeLeft(60L)
            readingRows(listOf(session(seconds = 177)), 0.75f, isReading = true)
                .map { it.kind } shouldNotContain ReadingRowKind.TimeLeft
        }

        it("TC-17: work out time left from the unrounded pace") {
            readingRows(listOf(session(seconds = 11_040)), 11_040f / 17_820f, isReading = true)
                .only<ReadingRow.TimeLeft>() shouldBe ReadingRow.TimeLeft(6_780L)
        }

        it("TC-18: read last read as the latest end, not the latest start") {
            val rows = readingRows(
                listOf(session(startedAt = 100_000L, seconds = 60), session(startedAt = 0L, seconds = 4_000)),
                null,
                isReading = false,
            )

            rows.only<ReadingRow.LastRead>() shouldBe ReadingRow.LastRead(4_000_000L)
        }

        it("TC-19: read the longest sitting as the newest of the longest") {
            val rows = readingRows(
                listOf(
                    session(startedAt = 3_000L, seconds = 600),
                    session(startedAt = 2_000L, seconds = 2_880),
                    session(startedAt = 1_000L, seconds = 2_880),
                ),
                null,
                isReading = false,
            )

            rows.only<ReadingRow.LongestSitting>() shouldBe ReadingRow.LongestSitting(2_880L, 2_000L)
        }
    }
})
