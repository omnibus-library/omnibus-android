package com.omnibus.omnibus.feature.bookdetail.overview.reading

import com.omnibus.omnibus.data.bookprogress.BookProgressError
import com.omnibus.omnibus.data.bookprogress.BookProgressRepository
import com.omnibus.omnibus.data.bookprogress.BookProgressResult
import com.omnibus.omnibus.data.bookprogress.ReadingSessionsResult
import com.omnibus.omnibus.data.bookprogress.TestBookProgressRepository
import com.omnibus.omnibus.data.bookprogress.readingRecord
import com.omnibus.omnibus.data.library.ProgressRecord
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.readstatus.TestReadStatusRepository
import com.omnibus.omnibus.data.readstatus.randomReadStatusRecord
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession
import com.omnibus.omnibus.testing.CollectingScope
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private val hold = ReadingCardModel(line = null, rows = emptyList())

/** A sitting with no fractions, as the server sends today, so the pace comes from the book's fraction. */
private fun session(startedAt: Long = randomLong(), seconds: Long = randomLong(1L..36_000L)) =
    ReadingSession(startedAt, seconds, startFraction = null, endFraction = null)

private fun success(vararg sessions: ReadingSession) = ReadingSessionsResult.Success(sessions.toList())

private fun known(status: ReadStatus, finishedAt: Long? = null) =
    ReadStatusState.Known(randomReadStatusRecord().copy(status = status, finishedAtMillis = finishedAt))

/** Sessions reads that wait until the test completes them, in call order. Everything else is the shared fake. */
private class GatedSessionsRepository(
    delegate: TestBookProgressRepository = TestBookProgressRepository(),
) : BookProgressRepository by delegate {
    val pending = mutableListOf<CompletableDeferred<ReadingSessionsResult>>()

    override suspend fun getSessions(bookUuid: String): ReadingSessionsResult =
        CompletableDeferred<ReadingSessionsResult>().also { pending += it }.await()
}

/** Runs [block] with the card's state collected, since it is only kept while something collects it. */
private inline fun ReadingCardViewModel.whileCollected(block: () -> Unit) {
    val collecting = CollectingScope()
    collecting.collect(uiState)
    try {
        block()
    } finally {
        collecting.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingCardViewModelTest : DescribeSpec({
    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        bookUuid: String = randomString(),
        progress: BookProgressRepository = TestBookProgressRepository(),
        status: TestReadStatusRepository = TestReadStatusRepository(),
        hasFile: Boolean = randomBoolean(),
        timeSource: TimeSource = TimeSource { randomLong() },
    ) = ReadingCardViewModel(bookUuid, hasFile, progress, status, timeSource)

    /** The read status of a book the reader is reading. */
    fun reading(bookUuid: String) = TestReadStatusRepository().apply { seed(bookUuid, known(ReadStatus.Reading)) }

    /** Sets the book's progress to [result] of a record at half way. */
    fun TestBookProgressRepository.progressOf(bookUuid: String, result: (List<ProgressRecord>) -> BookProgressResult) {
        progressFor(bookUuid).value = result(listOf(readingRecord(50.0, randomLong(), uuid = bookUuid)))
    }

    describe("the card before the answers arrive") {
        context("when the sessions have not answered") {
            it("TC-29: holds") {
                val bookUuid = randomString()
                val vm = sut(bookUuid, GatedSessionsRepository(), reading(bookUuid))

                vm.whileCollected {
                    vm.reload(0)

                    vm.uiState.value.card shouldBe hold
                }
            }
        }
    }

    describe("the card once it has loaded") {
        it("TC-30: works out the pace from the book's progress") {
            val bookUuid = randomString()
            val progress = TestBookProgressRepository().apply {
                sessionsResults += success(session(seconds = 3_600))
                progressOf(bookUuid) { BookProgressResult.Success(it, fromServer = randomBoolean()) }
            }
            val vm = sut(bookUuid, progress, reading(bookUuid))

            vm.whileCollected {
                vm.reload(0)

                vm.uiState.value.card.rows shouldContain ReadingRow.Pace(50)
            }
        }

        it("TC-38: reads its dates against the time source") {
            val now = randomLong()
            val vm = sut(timeSource = { now })

            vm.whileCollected {
                vm.reload(0)

                vm.uiState.value.nowMillis shouldBe now
            }
        }

        context("when a position is saved without any sessions") {
            it("TC-31: shows that the sittings have yet to sync") {
                val bookUuid = randomString()
                val progress = TestBookProgressRepository().apply {
                    progressOf(bookUuid) { BookProgressResult.Success(it, fromServer = randomBoolean()) }
                    sessionsResults.addLast(success())
                }
                val vm = sut(bookUuid, progress, reading(bookUuid))

                vm.whileCollected {
                    vm.reload(0)

                    vm.uiState.value.card.line shouldBe ReadingLine.SyncPending
                }
            }
        }

        context("when the progress read has failed") {
            it("TC-37: still works out the pace from the records it kept") {
                val bookUuid = randomString()
                val progress = TestBookProgressRepository().apply {
                    sessionsResults += success(session(seconds = 3_600))
                    progressOf(bookUuid) { BookProgressResult.Failed(BookProgressError.Unreachable, it) }
                }
                val vm = sut(bookUuid, progress, reading(bookUuid))

                vm.whileCollected {
                    vm.reload(0)

                    vm.uiState.value.card.rows shouldContain ReadingRow.Pace(50)
                }
            }
        }

        context("when the status changes") {
            it("TC-35: updates without a reload") {
                val bookUuid = randomString()
                val finishedAt = randomLong()
                val status = reading(bookUuid)
                val progress = TestBookProgressRepository().apply { sessionsResults.addLast(success()) }
                val vm = sut(bookUuid, progress, status)

                vm.whileCollected {
                    vm.reload(0)

                    status.seed(bookUuid, known(ReadStatus.Finished, finishedAt))

                    vm.uiState.value.card.line shouldBe ReadingLine.Finished(finishedAt)
                }
            }
        }
    }

    describe("reading the sessions") {
        it("TC-32: reads once per reload key") {
            val bookUuid = randomString()
            val progress = TestBookProgressRepository().apply {
                sessionsResults += success(session())
                sessionsResults += success(session(seconds = 1_800))
            }
            val vm = sut(bookUuid, progress, reading(bookUuid))

            vm.whileCollected {
                vm.reload(0)
                vm.reload(0)
                vm.reload(1)

                vm.uiState.value.card.rows shouldContain ReadingRow.TimeRead(1_800L, 1)
            }
        }

        context("when the first read fails") {
            it("TC-33: shows the error line") {
                val bookUuid = randomString()
                val progress = TestBookProgressRepository().apply {
                    sessionsResults += ReadingSessionsResult.Failed(BookProgressError.Unreachable)
                }
                val vm = sut(bookUuid, progress, reading(bookUuid))

                vm.whileCollected {
                    vm.reload(0)

                    vm.uiState.value.card.line shouldBe ReadingLine.HistoryFailed
                }
            }
        }

        context("when a later read fails") {
            it("TC-33: keeps the sessions already loaded") {
                val bookUuid = randomString()
                val sitting = session()
                val progress = TestBookProgressRepository().apply {
                    sessionsResults += success(sitting)
                    sessionsResults += ReadingSessionsResult.Failed(BookProgressError.Unreachable)
                }
                val vm = sut(bookUuid, progress, reading(bookUuid))

                vm.whileCollected {
                    vm.reload(0)
                    vm.reload(1)

                    vm.uiState.value.card.line shouldBe ReadingLine.ReadingSince(sitting.startedAtMillis)
                }
            }
        }

        context("when a later read is in flight") {
            it("TC-34: never drops back to the hold") {
                val bookUuid = randomString()
                val sitting = session()
                val gate = GatedSessionsRepository()
                val vm = sut(bookUuid, gate, reading(bookUuid))

                vm.whileCollected {
                    vm.reload(0)
                    gate.pending[0].complete(success(sitting))

                    vm.reload(1)

                    vm.uiState.value.card.line shouldBe ReadingLine.ReadingSince(sitting.startedAtMillis)
                }
            }
        }

        context("when a newer reload has superseded a read") {
            it("TC-34: ignores the older answer") {
                val bookUuid = randomString()
                val gate = GatedSessionsRepository()
                val vm = sut(bookUuid, gate, reading(bookUuid))

                vm.whileCollected {
                    vm.reload(0)
                    vm.reload(1)

                    gate.pending[0].complete(success(session()))

                    vm.uiState.value.card shouldBe hold
                }
            }
        }
    }
})
