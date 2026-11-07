package com.omnibus.omnibus.feature.bookdetail.status

import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusError
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.readstatus.ReadStatusWriteResult
import com.omnibus.omnibus.data.readstatus.TestReadStatusRepository
import com.omnibus.omnibus.data.readstatus.randomReadStatusRecord
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.CollectingScope
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private fun known(status: ReadStatus) = ReadStatusState.Known(randomReadStatusRecord().copy(status = status))

/** Runs [block] with [vm]'s state kept active, as a subscribed screen would. */
private inline fun collecting(vm: ReadStatusViewModel, block: ReadStatusViewModel.() -> Unit) {
    val scope = CollectingScope()
    scope.collect(vm.uiState)
    try {
        vm.block()
    } finally {
        scope.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReadStatusViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        bookUuid: String = randomString(),
        repository: TestReadStatusRepository = TestReadStatusRepository(),
        timeSource: TimeSource = TestTimeSource(randomLong()),
    ) = ReadStatusViewModel(bookUuid, repository, timeSource)

    /** A ViewModel over a repository holding [state]. */
    fun sutWith(state: ReadStatusState, repository: TestReadStatusRepository = TestReadStatusRepository()): ReadStatusViewModel {
        val uuid = randomString()
        repository.seed(uuid, state)
        return sut(uuid, repository)
    }

    describe("the status shown") {

        context("when the status is not known") {
            it("TC-38 / TC-39: shows none, whether unknown or failed") {
                checkAll(Exhaustive.of(ReadStatusState.Unknown, ReadStatusState.Failed)) { state ->
                    collecting(sutWith(state)) {
                        uiState.value.status shouldBe null
                    }
                }
            }
        }

        context("when the status is known") {
            it("TC-40: shows it, for every status") {
                checkAll(Exhaustive.enum<ReadStatus>()) { status ->
                    collecting(sutWith(known(status))) {
                        uiState.value.status shouldBe status
                    }
                }
            }
        }

        it("TC-43: reads the clock each time the status changes") {
            val uuid = randomString()
            val clock = TestTimeSource(randomLong())
            val repository = TestReadStatusRepository()
            repository.seed(uuid, known(ReadStatus.Reading))

            collecting(sut(uuid, repository, clock)) {
                clock.advanceBy(1_000L)
                repository.seed(uuid, known(ReadStatus.Finished))

                uiState.value.nowMillis shouldBe clock.currentMillis
            }
        }
    }

    describe("the finished date") {

        context("when the status is Finished") {
            it("TC-41: is the record's date") {
                val finishedAt = randomLong()
                val record = randomReadStatusRecord().copy(status = ReadStatus.Finished, finishedAtMillis = finishedAt)

                collecting(sutWith(ReadStatusState.Known(record))) {
                    uiState.value.finishedAtMillis shouldBe finishedAt
                }
            }
        }

        context("when the status is not Finished") {
            it("TC-42: drops a stale date, for Reading or Unread") {
                checkAll(Exhaustive.of(ReadStatus.Reading, ReadStatus.Unread)) { status ->
                    val record = randomReadStatusRecord().copy(status = status, finishedAtMillis = randomLong())

                    collecting(sutWith(ReadStatusState.Known(record))) {
                        uiState.value.finishedAtMillis shouldBe null
                    }
                }
            }
        }
    }

    describe("reloading") {

        it("TC-45 / TC-46: refreshes once per key") {
            val repository = TestReadStatusRepository()
            val vm = sut(repository = repository)

            vm.reload(0)
            vm.reload(0)
            vm.reload(1)

            repository.refreshCount shouldBe 2
        }
    }

    describe("selecting a status") {

        context("when it is the current status") {
            it("TC-47: writes nothing") {
                val repository = TestReadStatusRepository()

                collecting(sutWith(known(ReadStatus.Reading), repository)) {
                    selectStatus(ReadStatus.Reading)

                    repository.setStatusCalls.shouldBeEmpty()
                }
            }
        }

        context("when it differs from the current status") {
            it("TC-48: writes it") {
                val uuid = randomString()
                val repository = TestReadStatusRepository()
                repository.seed(uuid, known(ReadStatus.Reading))
                repository.writeGates += CompletableDeferred<Unit>()

                collecting(sut(uuid, repository)) {
                    selectStatus(ReadStatus.Finished)

                    repository.setStatusCalls shouldBe listOf(uuid to ReadStatus.Finished)
                }
            }
        }

        context("when the write fails") {
            context("when the device is offline") {
                it("TC-50: reports Offline, with no server or no connection") {
                    checkAll(Exhaustive.of(ReadStatusError.NoServer, ReadStatusError.Unreachable)) { error ->
                        val repository = TestReadStatusRepository()
                        repository.writeResults.addLast(ReadStatusWriteResult.Failed(error))

                        collecting(sutWith(known(ReadStatus.Reading), repository)) {
                            selectStatus(ReadStatus.Finished)

                            uiState.value.saveFailure shouldBe ReadStatusSaveFailure.Offline
                        }
                    }
                }
            }

            context("when the server answered") {
                it("TC-51: reports Other, for every other error") {
                    checkAll(
                        Exhaustive.of(
                            ReadStatusError.Http(500),
                            ReadStatusError.SessionExpired,
                            ReadStatusError.BookNotFound,
                            ReadStatusError.UnexpectedResponse,
                        ),
                    ) { error ->
                        val repository = TestReadStatusRepository()
                        repository.writeResults.addLast(ReadStatusWriteResult.Failed(error))

                        collecting(sutWith(known(ReadStatus.Reading), repository)) {
                            selectStatus(ReadStatus.Finished)

                            uiState.value.saveFailure shouldBe ReadStatusSaveFailure.Other
                        }
                    }
                }
            }

            context("when the failure has been shown") {
                it("TC-52: clears it") {
                    val repository = TestReadStatusRepository()
                    repository.writeResults.addLast(ReadStatusWriteResult.Failed(ReadStatusError.Http(500)))

                    collecting(sutWith(known(ReadStatus.Reading), repository)) {
                        selectStatus(ReadStatus.Finished)

                        saveFailureShown()

                        uiState.value.saveFailure shouldBe null
                    }
                }
            }
        }
    }
})
