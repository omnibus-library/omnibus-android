package com.omnibus.omnibus.feature.bookdetail.overview.reading

import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusRecord
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession
import com.omnibus.omnibus.feature.bookdetail.progress.pacePerHour
import com.omnibus.omnibus.feature.bookdetail.progress.timeLeftSeconds
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.GregorianCalendar
import java.util.TimeZone

class ReadingCardModelTest : DescribeSpec({

    val utc = TimeZone.getTimeZone("UTC")

    fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        GregorianCalendar(utc).apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

    fun session(startedAt: Long, seconds: Long) = ReadingSession(startedAt, seconds, null, null)

    /** The ticket's sample: 11 sittings, newest first, totalling 11 040 s; the longest, 2 880 s, starts Oct 19. */
    val sample = listOf(
        session(at(2026, 11, 1, 22, 4), 600),
        session(at(2026, 10, 31, 20), 900),
        session(at(2026, 10, 30, 19), 1_200),
        session(at(2026, 10, 19, 18), 2_880),
        session(at(2026, 10, 18, 20), 720),
        session(at(2026, 10, 17, 20), 600),
        session(at(2026, 10, 16, 20), 480),
        session(at(2026, 10, 15, 20), 1_000),
        session(at(2026, 10, 14, 20), 900),
        session(at(2026, 10, 13, 20), 660),
        session(at(2026, 10, 12, 20), 1_100),
    )
    val sampleFraction = 11_040f / 17_820f
    val oct12 = at(2026, 10, 12, 20)
    val oct19 = at(2026, 10, 19, 18)
    val finishedOct29 = at(2026, 10, 29, 22, 14)

    fun known(status: ReadStatus, finishedAt: Long? = null) =
        ReadStatusState.Known(ReadStatusRecord(status, 1L, finishedAt))

    val unknown = ReadStatusState.Unknown
    val failedStatus = ReadStatusState.Failed
    val loading = SessionsState.Loading
    val sessionsFailed = SessionsState.Failed
    val loadedEmpty = SessionsState.Loaded(emptyList())
    val loadedSample = SessionsState.Loaded(sample)
    val hold = ReadingCardModel(line = null, rows = emptyList())
    val somePosition = CardProgress(0.4f, hasPosition = true)

    fun model(
        status: ReadStatusState,
        sessions: SessionsState,
        hasFile: Boolean = true,
        progress: CardProgress? = null,
        wishlistOnly: Boolean = false,
    ) = readingCardModel(wishlistOnly, hasFile, status, sessions, progress)

    describe("the card model") {

        it("TC-1: a wishlist-only book is the wishlist line with no rows") {
            model(
                status = known(ReadStatus.Finished, finishedOct29),
                sessions = loadedSample,
                progress = CardProgress(0.5f, true),
                wishlistOnly = true,
            ) shouldBe ReadingCardModel(ReadingLine.Wishlist, emptyList())
        }

        it("TC-2: the card holds the heading alone until the status and the sessions have both answered") {
            val holds = listOf(
                unknown to sessionsFailed,
                unknown to loadedEmpty,
                unknown to loadedSample,
                unknown to loading,
                known(ReadStatus.Unread) to loading,
                known(ReadStatus.Reading) to loading,
                known(ReadStatus.Finished, finishedOct29) to loading,
                failedStatus to loading,
            )
            for ((status, sessions) in holds) {
                for (hasFile in listOf(true, false)) {
                    for (progress in listOf(null, somePosition)) {
                        withClue("$status, $sessions, hasFile=$hasFile, progress=$progress") {
                            model(status, sessions, hasFile, progress) shouldBe hold
                        }
                    }
                }
            }
        }

        it("TC-3: a failed status is the error line with no rows, whatever the sessions") {
            for (sessions in listOf(sessionsFailed, loadedEmpty, loadedSample)) {
                for (hasFile in listOf(true, false)) {
                    withClue("$sessions, hasFile=$hasFile") {
                        model(failedStatus, sessions, hasFile) shouldBe ReadingCardModel(ReadingLine.HistoryFailed, emptyList())
                    }
                }
            }
        }

        it("TC-4: an unread book with a file is the no-reading line with no rows, even with sessions") {
            model(known(ReadStatus.Unread), loadedEmpty) shouldBe ReadingCardModel(ReadingLine.NoReading, emptyList())
            model(known(ReadStatus.Unread), loadedSample) shouldBe ReadingCardModel(ReadingLine.NoReading, emptyList())
        }

        it("TC-5: a failed sessions read is the error line, never the empty lines") {
            for (status in listOf(known(ReadStatus.Unread), known(ReadStatus.Reading), known(ReadStatus.Finished))) {
                for (hasFile in listOf(true, false)) {
                    withClue("$status, hasFile=$hasFile") {
                        model(status, sessionsFailed, hasFile) shouldBe ReadingCardModel(ReadingLine.HistoryFailed, emptyList())
                    }
                }
            }
        }

        it("TC-6: an unread paper copy is the paper line with no rows") {
            model(known(ReadStatus.Unread), loadedEmpty, hasFile = false) shouldBe ReadingCardModel(ReadingLine.PaperCopy, emptyList())
            model(known(ReadStatus.Unread), loadedSample, hasFile = false) shouldBe ReadingCardModel(ReadingLine.PaperCopy, emptyList())
        }

        it("TC-7: a reading book with sessions shows since the earliest start, then the rows") {
            val card = model(known(ReadStatus.Reading), loadedSample, progress = null)

            card.line shouldBe ReadingLine.ReadingSince(oct12)
            card.rows.map { it.kind } shouldBe listOf(
                ReadingRowKind.Started,
                ReadingRowKind.TimeRead,
                ReadingRowKind.LastRead,
                ReadingRowKind.LongestSitting,
            )
            card.rows.first() shouldBe ReadingRow.Started(oct12)
        }

        it("TC-8: a reading book with no sessions shows the empty state for its position") {
            model(known(ReadStatus.Reading), loadedEmpty, hasFile = true, progress = CardProgress(0.4f, true)) shouldBe
                ReadingCardModel(ReadingLine.SyncPending, emptyList())
            model(known(ReadStatus.Reading), loadedEmpty, hasFile = true, progress = CardProgress(null, false)) shouldBe
                ReadingCardModel(ReadingLine.NoReading, emptyList())
            model(known(ReadStatus.Reading), loadedEmpty, hasFile = true, progress = null) shouldBe
                ReadingCardModel(ReadingLine.NoReading, emptyList())
            model(known(ReadStatus.Reading), loadedEmpty, hasFile = false, progress = null) shouldBe
                ReadingCardModel(ReadingLine.PaperCopy, emptyList())
        }

        it("TC-9: a finished book with a date shows the finished line, with rows when there are sessions") {
            val withPace = model(known(ReadStatus.Finished, finishedOct29), loadedSample, progress = CardProgress(0.5f, true))
            withPace.line shouldBe ReadingLine.Finished(finishedOct29)
            withPace.rows.map { it.kind } shouldBe listOf(
                ReadingRowKind.Started,
                ReadingRowKind.TimeRead,
                ReadingRowKind.Pace,
                ReadingRowKind.LastRead,
                ReadingRowKind.LongestSitting,
            )

            model(known(ReadStatus.Finished, finishedOct29), loadedEmpty) shouldBe
                ReadingCardModel(ReadingLine.Finished(finishedOct29), emptyList())
            model(known(ReadStatus.Finished, finishedOct29), sessionsFailed) shouldBe
                ReadingCardModel(ReadingLine.Finished(finishedOct29), emptyList())
            model(known(ReadStatus.Finished, finishedOct29), loadedEmpty, hasFile = false) shouldBe
                ReadingCardModel(ReadingLine.Finished(finishedOct29), emptyList())
        }

        it("TC-10: a finished book without a date has no line when it has sessions, else its empty line") {
            val withSessions = model(known(ReadStatus.Finished), loadedSample, progress = CardProgress(0.5f, true))
            withSessions.line shouldBe null
            withSessions.rows.map { it.kind } shouldBe listOf(
                ReadingRowKind.Started,
                ReadingRowKind.TimeRead,
                ReadingRowKind.Pace,
                ReadingRowKind.LastRead,
                ReadingRowKind.LongestSitting,
            )
            model(known(ReadStatus.Finished), loadedEmpty, hasFile = true) shouldBe ReadingCardModel(ReadingLine.NoReading, emptyList())
            model(known(ReadStatus.Finished), loadedEmpty, hasFile = false) shouldBe ReadingCardModel(ReadingLine.PaperCopy, emptyList())
            model(known(ReadStatus.Finished), loadedEmpty, hasFile = true, progress = CardProgress(0.4f, true)) shouldBe
                ReadingCardModel(ReadingLine.NoReading, emptyList())
        }

        it("TC-11: the rows come in the documented order") {
            readingRows(sample, sampleFraction, isReading = true).map { it.kind } shouldBe listOf(
                ReadingRowKind.Started,
                ReadingRowKind.TimeRead,
                ReadingRowKind.Pace,
                ReadingRowKind.LastRead,
                ReadingRowKind.LongestSitting,
                ReadingRowKind.TimeLeft,
            )
        }

        it("TC-12: with no pace the rows leave out pace and time left") {
            val withoutPace = listOf(
                null to sample,
                0f to sample,
                0.5f to sample.map { it.copy(durationSeconds = 0L) },
            )
            for ((fraction, sessions) in withoutPace) {
                withClue("fraction=$fraction") {
                    readingRows(sessions, fraction, isReading = true).map { it.kind } shouldBe listOf(
                        ReadingRowKind.Started,
                        ReadingRowKind.TimeRead,
                        ReadingRowKind.LastRead,
                        ReadingRowKind.LongestSitting,
                    )
                }
            }
            readingRows(emptyList(), 0.5f, isReading = true) shouldBe emptyList()
        }

        it("TC-13: started is the earliest start, and time read is a long sum") {
            val rows = readingRows(sample, sampleFraction, isReading = true)
            rows[0] shouldBe ReadingRow.Started(oct12)
            rows[1] shouldBe ReadingRow.TimeRead(11_040L, 11)

            val one = session(oct19, 600)
            val oneRows = readingRows(listOf(one), null, isReading = false)
            oneRows.only<ReadingRow.TimeRead>() shouldBe ReadingRow.TimeRead(600L, 1)
            oneRows.only<ReadingRow.Started>() shouldBe ReadingRow.Started(oct19)
            oneRows.only<ReadingRow.LastRead>() shouldBe ReadingRow.LastRead(oct19 + 600_000L)
            oneRows.only<ReadingRow.LongestSitting>() shouldBe ReadingRow.LongestSitting(600L, oct19)

            val big = Int.MAX_VALUE.toLong()
            readingRows(listOf(session(oct19, big), session(oct12, big)), null, isReading = false)
                .only<ReadingRow.TimeRead>() shouldBe ReadingRow.TimeRead(2 * big, 2)
        }

        it("TC-14: pace rounds to the nearest percent and is hidden when it rounds to zero") {
            val hour = listOf(session(oct19, 3_600))
            readingRows(hour, 0.196f, isReading = true).only<ReadingRow.Pace>() shouldBe ReadingRow.Pace(20)
            readingRows(hour, 0.204f, isReading = true).only<ReadingRow.Pace>() shouldBe ReadingRow.Pace(20)
            readingRows(listOf(session(oct19, 360_000)), 0.5f, isReading = true).only<ReadingRow.Pace>() shouldBe ReadingRow.Pace(1)

            val tiny = readingRows(listOf(session(oct19, 225_000)), 0.25f, isReading = true)
            tiny.filterIsInstance<ReadingRow.Pace>().shouldBeEmpty()
            tiny.only<ReadingRow.TimeLeft>() shouldBe ReadingRow.TimeLeft(675_000L)
        }

        it("TC-15: time left only while reading and with something left") {
            val notReading = readingRows(sample, 0.5f, isReading = false)
            notReading.map { it.kind } shouldContain ReadingRowKind.Pace
            notReading.map { it.kind } shouldNotContain ReadingRowKind.TimeLeft

            val full = readingRows(sample, 1f, isReading = true)
            full.map { it.kind } shouldContain ReadingRowKind.Pace
            full.map { it.kind } shouldNotContain ReadingRowKind.TimeLeft
        }

        it("TC-16: time left needs at least a minute") {
            val fraction = 0.75f
            val atMinute = listOf(session(oct19, 180))
            timeLeftSeconds(fraction, pacePerHour(atMinute, fraction)!!) shouldBe 60L
            readingRows(atMinute, fraction, isReading = true).only<ReadingRow.TimeLeft>() shouldBe ReadingRow.TimeLeft(60L)

            val underMinute = listOf(session(oct19, 177))
            timeLeftSeconds(fraction, pacePerHour(underMinute, fraction)!!) shouldBe 59L
            readingRows(underMinute, fraction, isReading = true).filterIsInstance<ReadingRow.TimeLeft>().shouldBeEmpty()
        }

        it("TC-17: the sample's pace and time left use the unrounded pace") {
            val rows = readingRows(sample, sampleFraction, isReading = true)
            rows.only<ReadingRow.Pace>() shouldBe ReadingRow.Pace(20)
            val exact = timeLeftSeconds(sampleFraction, pacePerHour(sample, sampleFraction)!!)
            rows.only<ReadingRow.TimeLeft>() shouldBe ReadingRow.TimeLeft(6_780L)
            exact shouldBe 6_780L
            timeLeftSeconds(sampleFraction, 20f) shouldNotBe exact
        }

        it("TC-18: last read is the latest end, not the latest start") {
            val sessions = listOf(
                session(at(2026, 11, 2, 10, 0), 60),
                session(at(2026, 11, 2, 8, 0), 4 * 3_600L),
            )
            readingRows(sessions, null, isReading = false).only<ReadingRow.LastRead>() shouldBe
                ReadingRow.LastRead(at(2026, 11, 2, 12, 0))
        }

        it("TC-19: longest sitting is the longest, and the newest one wins a tie") {
            val longest = listOf(
                session(at(2026, 10, 25, 10), 600),
                session(at(2026, 10, 24, 10), 2_880),
                session(at(2026, 10, 23, 10), 900),
            )
            readingRows(longest, null, isReading = false).only<ReadingRow.LongestSitting>() shouldBe
                ReadingRow.LongestSitting(2_880L, at(2026, 10, 24, 10))

            val tie = listOf(
                session(at(2026, 10, 20, 10), 2_880),
                session(at(2026, 10, 10, 10), 2_880),
            )
            readingRows(tie, null, isReading = false).only<ReadingRow.LongestSitting>() shouldBe
                ReadingRow.LongestSitting(2_880L, at(2026, 10, 20, 10))
        }
    }
})

private inline fun <reified T : ReadingRow> List<ReadingRow>.only(): T = filterIsInstance<T>().single()
