package com.omnibus.omnibus.feature.bookdetail.status

import com.omnibus.omnibus.data.readstatus.TestReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusError
import com.omnibus.omnibus.data.readstatus.ReadStatusRecord
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.readstatus.ReadStatusWriteResult
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ReadStatusViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    val uuid = "uuid-1"
    val reading = ReadStatusRecord(ReadStatus.Reading, updatedAtMillis = 100L, finishedAtMillis = null)
    val finished = ReadStatusRecord(ReadStatus.Finished, updatedAtMillis = 200L, finishedAtMillis = 150L)

    /** A ViewModel on [fake] with [now] as its clock, and its state kept active by [collecting]. */
    fun viewModel(fake: TestReadStatusRepository, now: () -> Long = { 1_000L }, collecting: CollectingScope) =
        ReadStatusViewModel(uuid, fake, TimeSource { now() }).also { collecting.collect(it.uiState) }

    describe("state mapping") {

        it("TC-38: an unknown status maps to a null status and no finished date") {
            val fake = TestReadStatusRepository()
            val collecting = CollectingScope()
            val vm = viewModel(fake, collecting = collecting)

            val state = vm.uiState.value
            state.status shouldBe null
            state.finishedAtMillis shouldBe null
            state.saveFailure shouldBe null
            collecting.cancel()
        }

        it("TC-39: a failed read maps to a null status and posts no message") {
            val fake = TestReadStatusRepository()
            val collecting = CollectingScope()
            fake.seed(uuid, ReadStatusState.Failed)
            val vm = viewModel(fake, collecting = collecting)

            vm.uiState.value.status shouldBe null
            vm.uiState.value.saveFailure shouldBe null
            collecting.cancel()
        }

        it("TC-40: each known status maps through, including the null-body Unread") {
            val collecting = CollectingScope()
            listOf(
                ReadStatusRecord(ReadStatus.Unread, null, null),
                ReadStatusRecord(ReadStatus.Reading, 100L, null),
                ReadStatusRecord(ReadStatus.Finished, 100L, 90L),
            ).forEach { record ->
                val fake = TestReadStatusRepository()
                fake.seed(uuid, ReadStatusState.Known(record))
                val vm = viewModel(fake, collecting = collecting)

                vm.uiState.value.status shouldBe record.status
            }
            collecting.cancel()
        }

        it("TC-41: the finished date is kept only for a known Finished status") {
            val collecting = CollectingScope()
            val withDate = TestReadStatusRepository()
            withDate.seed(uuid, ReadStatusState.Known(finished))
            viewModel(withDate, collecting = collecting).uiState.value.finishedAtMillis shouldBe 150L

            val withoutDate = TestReadStatusRepository()
            withoutDate.seed(uuid, ReadStatusState.Known(ReadStatusRecord(ReadStatus.Finished, 100L, null)))
            viewModel(withoutDate, collecting = collecting).uiState.value.finishedAtMillis shouldBe null
            collecting.cancel()
        }

        it("TC-42: a stale finished date is dropped for Reading and Unread") {
            val collecting = CollectingScope()
            listOf(ReadStatus.Reading, ReadStatus.Unread).forEach { status ->
                val fake = TestReadStatusRepository()
                fake.seed(uuid, ReadStatusState.Known(ReadStatusRecord(status, 100L, 150L)))

                viewModel(fake, collecting = collecting).uiState.value.finishedAtMillis shouldBe null
            }
            collecting.cancel()
        }

        it("TC-43: the clock is read when the state is mapped") {
            val fake = TestReadStatusRepository()
            val collecting = CollectingScope()
            var now = 1_000L
            val vm = viewModel(fake, now = { now }, collecting = collecting)
            fake.seed(uuid, ReadStatusState.Known(reading))
            vm.uiState.value.nowMillis shouldBe 1_000L

            now = 2_000L
            fake.seed(uuid, ReadStatusState.Known(finished))
            vm.uiState.value.nowMillis shouldBe 2_000L
            collecting.cancel()
        }
    }

    describe("reload") {

        it("TC-44: the first reload refreshes once") {
            val fake = TestReadStatusRepository()
            val collecting = CollectingScope()
            viewModel(fake, collecting = collecting).reload(0)

            fake.refreshCount shouldBe 1
            collecting.cancel()
        }

        it("TC-45: the same reload key twice refreshes once") {
            val fake = TestReadStatusRepository()
            val collecting = CollectingScope()
            val vm = viewModel(fake, collecting = collecting)

            vm.reload(0)
            vm.reload(0)

            fake.refreshCount shouldBe 1
            collecting.cancel()
        }

        it("TC-46: each new reload key refreshes, and a repeat of the last key does not") {
            val fake = TestReadStatusRepository()
            val collecting = CollectingScope()
            val vm = viewModel(fake, collecting = collecting)

            vm.reload(0)
            vm.reload(1)
            vm.reload(1)
            vm.reload(2)

            fake.refreshCount shouldBe 3
            collecting.cancel()
        }
    }

    describe("selecting a status") {

        it("TC-47: choosing the current status makes no request and no failure") {
            val fake = TestReadStatusRepository()
            val collecting = CollectingScope()
            fake.seed(uuid, ReadStatusState.Known(reading))
            val vm = viewModel(fake, collecting = collecting)

            vm.selectStatus(ReadStatus.Reading)

            fake.setStatusCalls.isEmpty() shouldBe true
            vm.uiState.value.saveFailure shouldBe null
            collecting.cancel()
        }

        it("TC-48: choosing a different status writes it, and shows it before the write returns") {
            val fake = TestReadStatusRepository()
            val collecting = CollectingScope()
            fake.seed(uuid, ReadStatusState.Known(reading))
            val vm = viewModel(fake, collecting = collecting)
            val gate = CompletableDeferred<Unit>()
            fake.writeGates.addLast(gate)

            vm.selectStatus(ReadStatus.Finished)

            fake.setStatusCalls shouldBe listOf(uuid to ReadStatus.Finished)
            vm.uiState.value.status shouldBe ReadStatus.Finished
            gate.complete(Unit)
            vm.uiState.value.status shouldBe ReadStatus.Finished
            collecting.cancel()
        }

        it("TC-49: choosing a status while unknown or failed still writes it") {
            val collecting = CollectingScope()
            listOf<ReadStatusState>(ReadStatusState.Unknown, ReadStatusState.Failed).forEach { state ->
                val fake = TestReadStatusRepository()
                fake.seed(uuid, state)
                val vm = viewModel(fake, collecting = collecting)

                vm.selectStatus(ReadStatus.Unread)

                fake.setStatusCalls.last() shouldBe (uuid to ReadStatus.Unread)
            }
            collecting.cancel()
        }

        it("TC-50: an offline failure maps to Offline and restores the previous status") {
            val collecting = CollectingScope()
            listOf(ReadStatusError.NoServer, ReadStatusError.Unreachable).forEach { error ->
                val fake = TestReadStatusRepository()
                fake.seed(uuid, ReadStatusState.Known(reading))
                fake.writeResults.addLast(ReadStatusWriteResult.Failed(error))
                val vm = viewModel(fake, collecting = collecting)

                vm.selectStatus(ReadStatus.Finished)

                vm.uiState.value.saveFailure shouldBe ReadStatusSaveFailure.Offline
                vm.uiState.value.status shouldBe ReadStatus.Reading
            }
            collecting.cancel()
        }

        it("TC-51: any other failure maps to Other") {
            val collecting = CollectingScope()
            listOf(
                ReadStatusError.Http(500),
                ReadStatusError.SessionExpired,
                ReadStatusError.BookNotFound,
                ReadStatusError.UnexpectedResponse,
            ).forEach { error ->
                val fake = TestReadStatusRepository()
                fake.seed(uuid, ReadStatusState.Known(reading))
                fake.writeResults.addLast(ReadStatusWriteResult.Failed(error))
                val vm = viewModel(fake, collecting = collecting)

                vm.selectStatus(ReadStatus.Finished)

                vm.uiState.value.saveFailure shouldBe ReadStatusSaveFailure.Other
            }
            collecting.cancel()
        }

        it("TC-52: saveFailureShown clears the failure, and the next failure sets it again") {
            val fake = TestReadStatusRepository()
            val collecting = CollectingScope()
            fake.seed(uuid, ReadStatusState.Known(reading))
            val vm = viewModel(fake, collecting = collecting)

            fake.writeResults.addLast(ReadStatusWriteResult.Failed(ReadStatusError.Http(500)))
            vm.selectStatus(ReadStatus.Finished)
            vm.uiState.value.saveFailure shouldBe ReadStatusSaveFailure.Other

            vm.saveFailureShown()
            vm.uiState.value.saveFailure shouldBe null

            fake.writeResults.addLast(ReadStatusWriteResult.Failed(ReadStatusError.Http(500)))
            vm.selectStatus(ReadStatus.Unread)
            vm.uiState.value.saveFailure shouldBe ReadStatusSaveFailure.Other
            collecting.cancel()
        }

        it("TC-53: a saved or unchanged write sets no failure") {
            val fake = TestReadStatusRepository()
            val collecting = CollectingScope()
            fake.seed(uuid, ReadStatusState.Known(reading))
            val vm = viewModel(fake, collecting = collecting)

            vm.selectStatus(ReadStatus.Finished)
            vm.uiState.value.saveFailure shouldBe null

            fake.writeResults.addLast(ReadStatusWriteResult.Unchanged)
            vm.selectStatus(ReadStatus.Unread)
            vm.uiState.value.saveFailure shouldBe null
            collecting.cancel()
        }

        it("TC-54: a write made elsewhere on the shared repository updates this state without a reload") {
            val fake = TestReadStatusRepository()
            val collecting = CollectingScope()
            fake.seed(uuid, ReadStatusState.Known(reading))
            val vm = viewModel(fake, collecting = collecting)

            fake.setStatus(uuid, ReadStatus.Finished)

            vm.uiState.value.status shouldBe ReadStatus.Finished
            fake.refreshCount shouldBe 0
            collecting.cancel()
        }
    }
})
