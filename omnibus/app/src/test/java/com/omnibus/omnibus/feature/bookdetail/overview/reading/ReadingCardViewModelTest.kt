package com.omnibus.omnibus.feature.bookdetail.overview.reading

import com.omnibus.omnibus.data.bookprogress.BookProgressError
import com.omnibus.omnibus.data.bookprogress.BookProgressRepository
import com.omnibus.omnibus.data.bookprogress.BookProgressResult
import com.omnibus.omnibus.data.bookprogress.FakeBookProgressRepository
import com.omnibus.omnibus.data.bookprogress.ReadingSessionsResult
import com.omnibus.omnibus.data.bookprogress.readingRecord
import com.omnibus.omnibus.data.readstatus.FakeReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusRecord
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.util.GregorianCalendar
import java.util.TimeZone

private const val UUID = "uuid-1"
private val utc: TimeZone = TimeZone.getTimeZone("UTC")

private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
    GregorianCalendar(utc).apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

private val NOW = at(2026, 11, 2)
private val OCT12 = at(2026, 10, 12, 20)
private val FINISHED_OCT29 = at(2026, 10, 29, 22, 14)

/** The ticket's sample: 11 sittings, newest first, totalling 11 040 s. */
private val sample = listOf(
    ReadingSession(at(2026, 11, 1, 22, 4), 600, null, null),
    ReadingSession(at(2026, 10, 31, 20), 900, null, null),
    ReadingSession(at(2026, 10, 30, 19), 1_200, null, null),
    ReadingSession(at(2026, 10, 19, 18), 2_880, null, null),
    ReadingSession(at(2026, 10, 18, 20), 720, null, null),
    ReadingSession(at(2026, 10, 17, 20), 600, null, null),
    ReadingSession(at(2026, 10, 16, 20), 480, null, null),
    ReadingSession(at(2026, 10, 15, 20), 1_000, null, null),
    ReadingSession(at(2026, 10, 14, 20), 900, null, null),
    ReadingSession(at(2026, 10, 13, 20), 660, null, null),
    ReadingSession(at(2026, 10, 12, 20), 1_100, null, null),
)

/** The sample's fraction: 11 040 s over a 17 820 s book, so the pace is about 20% an hour. */
private val SAMPLE_FRACTION = 11_040f / 17_820f

private val hold = ReadingCardModel(line = null, rows = emptyList())

private fun known(status: ReadStatus, finishedAt: Long? = null) =
    ReadStatusState.Known(ReadStatusRecord(status, 1L, finishedAt))

/** Sessions reads that wait until the test completes them, in call order. Everything else is the shared fake. */
private class GatedSessionsRepository(
    val delegate: FakeBookProgressRepository = FakeBookProgressRepository(),
) : BookProgressRepository by delegate {
    val pending = mutableListOf<CompletableDeferred<ReadingSessionsResult>>()

    override suspend fun getSessions(bookUuid: String): ReadingSessionsResult =
        CompletableDeferred<ReadingSessionsResult>().also { pending += it }.await()
}

/** A ViewModel with its fakes, and its state kept active by [collecting] until the test ends. */
private class Harness(
    val fake: FakeBookProgressRepository = FakeBookProgressRepository(),
    val status: FakeReadStatusRepository = FakeReadStatusRepository(),
    hasFile: Boolean = true,
    repository: BookProgressRepository = fake,
) {
    val collecting = CollectingScope()
    val vm = ReadingCardViewModel(UUID, hasFile, repository, status, TimeSource { NOW })
        .also { collecting.collect(it.uiState) }
}

/** Runs [block] on a [Harness], and cancels its collection afterwards. */
private inline fun withHarness(
    fake: FakeBookProgressRepository = FakeBookProgressRepository(),
    status: FakeReadStatusRepository = FakeReadStatusRepository(),
    hasFile: Boolean = true,
    block: Harness.() -> Unit,
) {
    val harness = Harness(fake, status, hasFile)
    try {
        harness.block()
    } finally {
        harness.collecting.cancel()
    }
}

/** The kinds of the card's rows, in order. */
private fun Harness.kinds(): List<ReadingRowKind> = vm.uiState.value.card.rows.map { it.kind }

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingCardViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("the hold") {

        it("TC-29: the card holds until both the status and the sessions have answered") {
            withHarness {
                vm.uiState.value.card shouldBe hold
            }

            val gate = GatedSessionsRepository()
            val pending = Harness(gate.delegate, FakeReadStatusRepository(), repository = gate)
            try {
                pending.status.seed(UUID, known(ReadStatus.Reading))
                pending.vm.reload(0)
                pending.vm.uiState.value.card shouldBe hold
            } finally {
                pending.collecting.cancel()
            }

            withHarness {
                fake.sessionsResults += ReadingSessionsResult.Success(sample)
                vm.reload(0)
                vm.uiState.value.card shouldBe hold
            }
        }
    }

    describe("the card's state") {

        it("TC-30: a reading book loads its card, with pace and time left from the progress") {
            withHarness {
                status.seed(UUID, known(ReadStatus.Reading))
                fake.sessionsResults += ReadingSessionsResult.Success(sample)
                fake.progressFor(UUID).value = BookProgressResult.Success(
                    listOf(readingRecord(SAMPLE_FRACTION * 100.0, 1L, uuid = UUID)),
                    fromServer = true,
                )
                vm.reload(0)

                val card = vm.uiState.value.card
                card.line shouldBe ReadingLine.ReadingSince(OCT12)
                card.rows shouldContain ReadingRow.Pace(20)
                card.rows.map { it.kind } shouldContain ReadingRowKind.TimeLeft
            }
        }

        it("TC-31: the empty states follow the file and the saved position") {
            withHarness(hasFile = true) {
                status.seed(UUID, known(ReadStatus.Unread))
                vm.reload(0)
                vm.uiState.value.card shouldBe ReadingCardModel(ReadingLine.NoReading, emptyList())
            }
            withHarness(hasFile = false) {
                status.seed(UUID, known(ReadStatus.Unread))
                vm.reload(0)
                vm.uiState.value.card shouldBe ReadingCardModel(ReadingLine.PaperCopy, emptyList())
            }
            withHarness(hasFile = false) {
                status.seed(UUID, known(ReadStatus.Reading))
                vm.reload(0)
                vm.uiState.value.card shouldBe ReadingCardModel(ReadingLine.PaperCopy, emptyList())
            }
            withHarness(hasFile = true) {
                status.seed(UUID, known(ReadStatus.Reading))
                fake.progressFor(UUID).value = BookProgressResult.Success(
                    listOf(readingRecord(40.0, 1L, uuid = UUID)),
                    fromServer = false,
                )
                vm.reload(0)
                vm.uiState.value.card shouldBe ReadingCardModel(ReadingLine.SyncPending, emptyList())
            }
        }

        it("TC-32: sessions are read once per reload key, and a new key takes the new answer") {
            withHarness {
                status.seed(UUID, known(ReadStatus.Reading))
                fake.sessionsResults += ReadingSessionsResult.Success(sample)
                fake.sessionsResults += ReadingSessionsResult.Success(
                    listOf(ReadingSession(at(2026, 10, 30, 10), 1_800, null, null)),
                )
                vm.reload(0)
                vm.reload(0)
                fake.sessionsCalls.size shouldBe 1

                vm.reload(1)
                fake.sessionsCalls.size shouldBe 2
                vm.uiState.value.card.rows shouldContain ReadingRow.TimeRead(1_800L, 1)
            }
        }

        it("TC-33: a failed read keeps the sessions already loaded, and a failure with none is the error line") {
            withHarness {
                status.seed(UUID, known(ReadStatus.Reading))
                fake.sessionsResults += ReadingSessionsResult.Failed(BookProgressError.Unreachable)
                vm.reload(0)
                vm.uiState.value.card shouldBe ReadingCardModel(ReadingLine.HistoryFailed, emptyList())
            }
            withHarness {
                status.seed(UUID, known(ReadStatus.Reading))
                fake.sessionsResults += ReadingSessionsResult.Success(sample)
                fake.sessionsResults += ReadingSessionsResult.Failed(BookProgressError.Unreachable)
                vm.reload(0)
                vm.reload(1)
                vm.uiState.value.card.line shouldBe ReadingLine.ReadingSince(OCT12)
                vm.uiState.value.card.rows.map { it.kind } shouldContain ReadingRowKind.LongestSitting
            }
        }

        it("TC-34: a re-read never drops back to the hold, and a newer reload wins over a read in flight") {
            val gate = GatedSessionsRepository()
            val seen = mutableListOf<ReadingCardModel>()
            val recorder = CoroutineScope(UnconfinedTestDispatcher())
            val harness = Harness(gate.delegate, FakeReadStatusRepository(), repository = gate)
            try {
                recorder.launch { harness.vm.uiState.map { it.card }.collect { seen += it } }
                harness.status.seed(UUID, known(ReadStatus.Reading))

                harness.vm.reload(0)
                gate.pending[0].complete(ReadingSessionsResult.Success(sample))
                val loadedAt = seen.size
                harness.vm.uiState.value.card.line shouldBe ReadingLine.ReadingSince(OCT12)

                harness.vm.reload(1)
                harness.vm.uiState.value.card.line shouldBe ReadingLine.ReadingSince(OCT12)

                harness.vm.reload(2)
                gate.pending[1].complete(ReadingSessionsResult.Success(listOf(ReadingSession(OCT12, 60, null, null))))
                harness.vm.uiState.value.card.rows.only<ReadingRow.TimeRead>() shouldBe ReadingRow.TimeRead(11_040L, 11)

                gate.pending[2].complete(ReadingSessionsResult.Success(sample))
                harness.vm.uiState.value.card.rows.only<ReadingRow.TimeRead>() shouldBe ReadingRow.TimeRead(11_040L, 11)
                seen.drop(loadedAt).shouldNotContain(hold)
            } finally {
                recorder.cancel()
                harness.collecting.cancel()
            }
        }

        it("TC-35: a status change updates the card without a reload, and never refreshes") {
            withHarness {
                fake.progressFor(UUID).value = BookProgressResult.Success(
                    listOf(readingRecord(SAMPLE_FRACTION * 100.0, 1L, uuid = UUID)),
                    fromServer = true,
                )
                status.seed(UUID, known(ReadStatus.Reading))
                fake.sessionsResults += ReadingSessionsResult.Success(sample)
                vm.reload(0)
                kinds() shouldContain ReadingRowKind.TimeLeft

                status.seed(UUID, known(ReadStatus.Finished, FINISHED_OCT29))
                vm.uiState.value.card.line shouldBe ReadingLine.Finished(FINISHED_OCT29)
                kinds() shouldNotContain ReadingRowKind.TimeLeft

                fake.sessionsCalls.size shouldBe 1
                status.refreshCount shouldBe 0
            }
            withHarness {
                status.seed(UUID, known(ReadStatus.Unread))
                fake.sessionsResults += ReadingSessionsResult.Success(sample)
                vm.reload(0)
                vm.uiState.value.card shouldBe ReadingCardModel(ReadingLine.NoReading, emptyList())

                status.seed(UUID, known(ReadStatus.Reading))
                vm.uiState.value.card.line shouldBe ReadingLine.ReadingSince(OCT12)
                kinds() shouldContain ReadingRowKind.TimeRead

                fake.sessionsCalls.size shouldBe 1
                status.refreshCount shouldBe 0
            }
        }

        it("TC-36: a failed status is the error line, and a finished date beats a failed sessions read") {
            withHarness {
                status.seed(UUID, ReadStatusState.Failed)
                vm.reload(0)
                vm.uiState.value.card shouldBe ReadingCardModel(ReadingLine.HistoryFailed, emptyList())
            }
            withHarness {
                status.seed(UUID, known(ReadStatus.Finished, FINISHED_OCT29))
                fake.sessionsResults += ReadingSessionsResult.Failed(BookProgressError.Unreachable)
                vm.reload(0)
                vm.uiState.value.card shouldBe ReadingCardModel(ReadingLine.Finished(FINISHED_OCT29), emptyList())
            }
        }

        it("TC-37: progress feeds the pace, is collected again on each reload, and a failed read still supplies it") {
            withHarness {
                status.seed(UUID, known(ReadStatus.Reading))
                fake.sessionsResults += ReadingSessionsResult.Success(sample)
                fake.progressCollections.size shouldBe 0

                vm.reload(0)
                vm.uiState.value.card.line shouldBe ReadingLine.ReadingSince(OCT12)
                kinds() shouldNotContain ReadingRowKind.Pace
                kinds() shouldNotContain ReadingRowKind.TimeLeft

                fake.progressFor(UUID).value = BookProgressResult.Success(
                    listOf(readingRecord(SAMPLE_FRACTION * 100.0, 1L, uuid = UUID)),
                    fromServer = true,
                )
                vm.uiState.value.card.rows shouldContain ReadingRow.Pace(20)

                fake.sessionsResults += ReadingSessionsResult.Success(sample)
                vm.reload(1)
                fake.progressCollections.size shouldBe 2

                fake.progressFor(UUID).value = BookProgressResult.Failed(
                    BookProgressError.Unreachable,
                    listOf(readingRecord(SAMPLE_FRACTION * 100.0, 1L, uuid = UUID)),
                )
                vm.uiState.value.card.rows shouldContain ReadingRow.Pace(20)
            }
        }

        it("TC-38: the card's dates are read against the time source") {
            withHarness {
                status.seed(UUID, known(ReadStatus.Reading))
                fake.sessionsResults += ReadingSessionsResult.Success(sample)
                vm.reload(0)
                vm.uiState.value.nowMillis shouldBe NOW
            }
        }
    }
})

private inline fun <reified T : ReadingRow> List<ReadingRow>.only(): T = filterIsInstance<T>().single()
