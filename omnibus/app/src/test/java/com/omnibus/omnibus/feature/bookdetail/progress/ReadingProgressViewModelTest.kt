package com.omnibus.omnibus.feature.bookdetail.progress

import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.BookProgressError
import com.omnibus.omnibus.data.bookprogress.BookProgressResult
import com.omnibus.omnibus.data.bookprogress.TestBookProgressRepository
import com.omnibus.omnibus.data.bookprogress.ReadingSessionsResult
import com.omnibus.omnibus.data.bookprogress.readingRecord
import com.omnibus.omnibus.data.library.TestBookAvailability
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.data.readstatus.TestReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusRecord
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private const val UUID = "uuid-1"

private fun known(status: ReadStatus) = ReadStatusState.Known(ReadStatusRecord(status, 1L, null))

private val reading = known(ReadStatus.Reading)

/** A ViewModel with its fakes, and its state kept active by [collecting] until the test ends. */
private class Harness(online: Boolean = true, answer: Boolean = false) {
    val progress = TestBookProgressRepository()
    val status = TestReadStatusRepository()
    val connectivity = TestConnectivityMonitor(online)
    val availability = TestBookAvailability(answer)
    val collecting = CollectingScope()
    val vm = ReadingProgressViewModel(UUID, true, progress, status, connectivity, availability)
        .also { collecting.collect(it.uiState) }
}

/** Runs [block] on a [Harness], and cancels its collection afterwards. */
private inline fun withHarness(online: Boolean = true, answer: Boolean = false, block: Harness.() -> Unit) {
    val harness = Harness(online, answer)
    try {
        harness.block()
    } finally {
        harness.collecting.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingProgressViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("loading") {

        it("TC-67: reload loads once per key") {
            withHarness {
                vm.reload(0)
                progress.progressCollections.size shouldBe 1
                progress.chaptersCalls.size shouldBe 1
                progress.sessionsCalls.size shouldBe 1

                vm.reload(0)
                progress.progressCollections.size shouldBe 1
                progress.chaptersCalls.size shouldBe 1
                progress.sessionsCalls.size shouldBe 1

                vm.reload(1)
                progress.progressCollections.size shouldBe 2
                progress.chaptersCalls.size shouldBe 1
                progress.sessionsCalls.size shouldBe 2
            }
        }

        it("TC-71: chapters load once, sessions refresh and survive a failure") {
            withHarness {
                progress.chaptersResults.addLast(BookChaptersResult.Success(emptyList(), emptyList(), null, null))
                vm.reload(0)
                vm.reload(1)
                progress.chaptersCalls.size shouldBe 1
            }

            withHarness {
                progress.chaptersResults.addLast(BookChaptersResult.Failed(BookProgressError.Unreachable))
                progress.chaptersResults.addLast(BookChaptersResult.Success(emptyList(), emptyList(), null, null))
                vm.reload(0)
                vm.reload(1)
                progress.chaptersCalls.size shouldBe 2
            }

            withHarness {
                status.seed(UUID, reading)
                progress.progressFor(UUID).value = BookProgressResult.Success(listOf(readingRecord(50.0, 100)), fromServer = false)
                progress.sessionsResults.addLast(ReadingSessionsResult.Success(listOf(ReadingSession(0L, 60L, null, null))))
                progress.sessionsResults.addLast(ReadingSessionsResult.Failed(BookProgressError.Unreachable))
                vm.reload(0)
                vm.uiState.value.display.shouldBeInstanceOf<ProgressDisplay.Bar>().tail shouldBe LineTail.LeftAtPace(60)

                vm.reload(1)
                progress.sessionsCalls.size shouldBe 2
                vm.uiState.value.display.shouldBeInstanceOf<ProgressDisplay.Bar>().tail shouldBe LineTail.LeftAtPace(60)
            }
        }
    }

    describe("the progress") {

        it("TC-69: the local position paints first, then the server, without a reload") {
            withHarness {
                status.seed(UUID, reading)
                vm.reload(0)

                progress.progressFor(UUID).value = BookProgressResult.Success(listOf(readingRecord(62.0, 100)), fromServer = false)
                vm.uiState.value.display.shouldBeInstanceOf<ProgressDisplay.Bar>().percent shouldBe 62

                progress.progressFor(UUID).value = BookProgressResult.Success(listOf(readingRecord(70.0, 200)), fromServer = true)
                vm.uiState.value.display.shouldBeInstanceOf<ProgressDisplay.Bar>().percent shouldBe 70
                progress.progressCollections.size shouldBe 1
            }
        }

        it("TC-70: status changes show and hide the bar live") {
            withHarness {
                status.seed(UUID, known(ReadStatus.Unread))
                progress.progressFor(UUID).value = BookProgressResult.Success(listOf(readingRecord(62.0, 100)), fromServer = false)
                vm.reload(0)
                vm.uiState.value.display shouldBe ProgressDisplay.Hidden

                status.seed(UUID, reading)
                vm.uiState.value.display.shouldBeInstanceOf<ProgressDisplay.Bar>()

                status.seed(UUID, known(ReadStatus.Finished))
                vm.uiState.value.display shouldBe ProgressDisplay.Hidden

                progress.progressCollections.size shouldBe 1
                progress.chaptersCalls.size shouldBe 1
                progress.sessionsCalls.size shouldBe 1
                status.fetchCalls.size shouldBe 0
            }
        }

        it("TC-73: a failed read still shows the known place") {
            withHarness {
                status.seed(UUID, reading)
                vm.reload(0)
                progress.progressFor(UUID).value = BookProgressResult.Failed(
                    BookProgressError.Unreachable,
                    listOf(readingRecord(40.0, 100)),
                )
                vm.uiState.value.display.shouldBeInstanceOf<ProgressDisplay.Bar>().percent shouldBe 40
            }
        }
    }

    describe("the offline note") {

        it("TC-75: the offline note follows status, connectivity and downloads") {
            withHarness(online = true, answer = false) {
                status.seed(UUID, reading)
                progress.progressFor(UUID).value = BookProgressResult.Success(emptyList(), fromServer = false)
                vm.reload(0)
                vm.uiState.value shouldBe ReadingProgressUIState(ProgressDisplay.Hidden, showOfflineNote = false)
                availability.calls.size shouldBe 0

                connectivity.isOnline.value = false
                vm.uiState.value shouldBe ReadingProgressUIState(ProgressDisplay.Hidden, showOfflineNote = true)
                availability.calls.size shouldBe 1

                connectivity.isOnline.value = true
                vm.uiState.value.showOfflineNote shouldBe false
                availability.calls.size shouldBe 1

                connectivity.isOnline.value = false
                vm.uiState.value.showOfflineNote shouldBe true
                availability.calls.size shouldBe 2

                status.seed(UUID, known(ReadStatus.Finished))
                vm.uiState.value.showOfflineNote shouldBe false
                availability.calls.size shouldBe 2

                availability.answer = true
                status.seed(UUID, reading)
                vm.reload(1)
                vm.uiState.value.showOfflineNote shouldBe false
                availability.calls.size shouldBe 3
            }
        }
    }
})
