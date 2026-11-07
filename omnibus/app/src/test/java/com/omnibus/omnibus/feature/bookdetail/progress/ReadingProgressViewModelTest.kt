package com.omnibus.omnibus.feature.bookdetail.progress

import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.BookProgressError
import com.omnibus.omnibus.data.bookprogress.BookProgressResult
import com.omnibus.omnibus.data.bookprogress.ReadingSessionsResult
import com.omnibus.omnibus.data.bookprogress.TestBookProgressRepository
import com.omnibus.omnibus.data.bookprogress.chapters
import com.omnibus.omnibus.data.bookprogress.readingRecord
import com.omnibus.omnibus.data.library.TestBookAvailability
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.readstatus.TestReadStatusRepository
import com.omnibus.omnibus.data.readstatus.randomReadStatusRecord
import com.omnibus.omnibus.testing.CollectingScope
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private fun known(status: ReadStatus) = ReadStatusState.Known(randomReadStatusRecord().copy(status = status))

/** A ViewModel on fresh fakes, with its state kept active by [collecting] until the test ends. */
private class Harness(online: Boolean, downloaded: Boolean) {
    val uuid = randomString()
    val progress = TestBookProgressRepository()
    val status = TestReadStatusRepository()
    val connectivity = TestConnectivityMonitor(online)
    val availability = TestBookAvailability(downloaded)
    val collecting = CollectingScope()
    val vm = ReadingProgressViewModel(uuid, randomBoolean(), progress, status, connectivity, availability)
        .also { collecting.collect(it.uiState) }

    /** Makes the status Reading and the repository answer with one reading record at [percent]. */
    fun seedReadingAt(percent: Double) {
        status.seed(uuid, known(ReadStatus.Reading))
        progress.progressFor(uuid).value = BookProgressResult.Success(listOf(readingRecord(percent, 100)), fromServer = false)
    }

    fun bar() = vm.uiState.value.display.shouldBeInstanceOf<ProgressDisplay.Bar>()
}

/** Runs [block] on a [Harness], and cancels its collection afterwards. */
private inline fun withHarness(
    online: Boolean = randomBoolean(),
    downloaded: Boolean = randomBoolean(),
    block: Harness.() -> Unit,
) {
    val harness = Harness(online, downloaded)
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
        it("TC-67: reads the progress once per new key") {
            withHarness {
                vm.reload(0)
                vm.reload(0)
                vm.reload(1)

                progress.progressCollections shouldBe listOf(uuid, uuid)
            }
        }

        context("when the chapters failed to load") {
            it("TC-71: tries again on the next key") {
                withHarness {
                    seedReadingAt(40.0)
                    progress.chaptersResults.addLast(BookChaptersResult.Failed(BookProgressError.Unreachable))
                    progress.chaptersResults.addLast(BookChaptersResult.Success(chapters(0f, .3f), emptyList(), null, null))
                    vm.reload(0)

                    vm.reload(1)

                    bar().flag shouldBe FlagLabel.Chapter(2)
                }
            }
        }

        context("when the sessions fail on a later key") {
            it("TC-71: keeps the time left from the last read") {
                withHarness {
                    seedReadingAt(50.0)
                    progress.sessionsResults.addLast(ReadingSessionsResult.Success(listOf(ReadingSession(0L, 60L, null, null))))
                    progress.sessionsResults.addLast(ReadingSessionsResult.Failed(BookProgressError.Unreachable))
                    vm.reload(0)

                    vm.reload(1)

                    bar().tail shouldBe LineTail.LeftAtPace(60)
                }
            }
        }
    }

    describe("the progress") {
        context("when the status becomes Reading") {
            it("TC-70: shows the bar without a reload") {
                withHarness {
                    status.seed(uuid, known(ReadStatus.Unread))
                    progress.progressFor(uuid).value = BookProgressResult.Success(listOf(readingRecord(62.0, 100)), fromServer = false)
                    vm.reload(0)

                    status.seed(uuid, known(ReadStatus.Reading))

                    bar().percent shouldBe 62
                }
            }
        }

        context("when the read failed") {
            it("TC-73: shows the known place") {
                withHarness {
                    status.seed(uuid, known(ReadStatus.Reading))
                    vm.reload(0)

                    progress.progressFor(uuid).value = BookProgressResult.Failed(
                        BookProgressError.Unreachable,
                        listOf(readingRecord(40.0, 100)),
                    )

                    bar().percent shouldBe 40
                }
            }
        }
    }

    describe("the offline note") {
        context("when offline") {
            it("TC-75: shows only for a book not downloaded, for each download state") {
                checkAll(Exhaustive.of(false to true, true to false)) { (downloaded, note) ->
                    withHarness(online = false, downloaded = downloaded) {
                        seedReadingAt(50.0)

                        vm.reload(0)

                        vm.uiState.value.showOfflineNote shouldBe note
                    }
                }
            }

            context("when a reload finds a download made meanwhile") {
                it("TC-75: goes away") {
                    withHarness(online = false, downloaded = false) {
                        seedReadingAt(50.0)
                        vm.reload(0)
                        availability.answer = true

                        vm.reload(1)

                        vm.uiState.value.showOfflineNote shouldBe false
                    }
                }
            }
        }

        it("TC-75: follows connectivity in both directions") {
            withHarness(online = true, downloaded = false) {
                seedReadingAt(50.0)
                vm.reload(0)
                connectivity.isOnline.value = false
                val offline = vm.uiState.value.showOfflineNote
                connectivity.isOnline.value = true

                listOf(offline, vm.uiState.value.showOfflineNote) shouldBe listOf(true, false)
            }
        }
    }
})
