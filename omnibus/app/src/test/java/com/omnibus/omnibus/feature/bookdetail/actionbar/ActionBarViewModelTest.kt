package com.omnibus.omnibus.feature.bookdetail.actionbar

import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.BookProgressError
import com.omnibus.omnibus.data.bookprogress.BookProgressResult
import com.omnibus.omnibus.data.bookprogress.TestBookProgressRepository
import com.omnibus.omnibus.data.bookprogress.chapters
import com.omnibus.omnibus.data.bookprogress.readingRecord
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.TestBookAvailability
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.readstatus.TestReadStatusRepository
import com.omnibus.omnibus.data.readstatus.randomReadStatusRecord
import com.omnibus.omnibus.feature.bookdetail.progress.bookReadingProgress
import com.omnibus.omnibus.testing.CollectingScope
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * A ViewModel on fresh fakes, with its state kept active by [collecting] until the test ends. [online] sets the monitor's
 * first answer, and [downloaded] whether the book opens offline.
 */
private class Harness(online: Boolean, downloaded: Boolean) {
    val uuid = randomString()
    val progress = TestBookProgressRepository()
    val status = TestReadStatusRepository()
    val connectivity = TestConnectivityMonitor(online)
    val availability = TestBookAvailability(downloaded)
    val controller = TestAudiobookController()
    val collecting = CollectingScope()
    val vm = ActionBarViewModel(uuid, progress, status, connectivity, availability, controller)
        .also { collecting.collect(it.uiState) }

    /** Makes the progress repository answer with one reading record at [percent]. */
    fun seedPlace(percent: Double) {
        progress.progressFor(uuid).value = BookProgressResult.Success(listOf(readingRecord(percent, 100)), fromServer = false)
    }
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
class ActionBarViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("loading") {
        it("TC-37, TC-38: reads the progress once per new key") {
            withHarness {
                vm.reload(1)
                vm.reload(1)
                vm.reload(2)

                progress.progressCollections shouldBe listOf(uuid, uuid)
            }
        }

        context("when the chapters failed to load") {
            it("TC-40: tries again on the next key") {
                withHarness {
                    seedPlace(40.0)
                    progress.chaptersResults.addLast(BookChaptersResult.Failed(BookProgressError.Unreachable))
                    progress.chaptersResults.addLast(BookChaptersResult.Success(chapters(0f, 0.3f), emptyList(), null, null))
                    vm.reload(1)

                    vm.reload(2)

                    vm.uiState.value.progress?.chapterNumber shouldBe 2
                }
            }
        }
    }

    describe("the state") {
        it("TC-35: maps the records with the chapters") {
            withHarness {
                val records = listOf(readingRecord(40.0, 100))
                val loaded = BookChaptersResult.Success(chapters(0f, 0.3f), emptyList(), null, null)
                progress.progressFor(uuid).value = BookProgressResult.Success(records, fromServer = false)
                progress.chaptersResults.addLast(loaded)

                vm.reload(1)

                vm.uiState.value.progress shouldBe bookReadingProgress(records, loaded)
            }
        }

        context("when the progress has not arrived") {
            it("TC-34, TC-36: carries the status") {
                withHarness {
                    val reading = ReadStatusState.Known(randomReadStatusRecord().copy(status = ReadStatus.Reading))
                    vm.reload(1)

                    status.seed(uuid, reading)

                    vm.uiState.value.status shouldBe reading
                }
            }
        }

        context("when the progress read failed") {
            it("TC-41: still uses the records it kept") {
                withHarness {
                    val records = listOf(readingRecord(40.0, 100))
                    progress.progressFor(uuid).value = BookProgressResult.Failed(BookProgressError.Unreachable, records)

                    vm.reload(1)

                    vm.uiState.value.progress shouldBe bookReadingProgress(records, null)
                }
            }
        }
    }

    describe("whether Listen is available") {
        context("when online") {
            it("#99 TC-15: is true for a book not downloaded") {
                withHarness(online = true, downloaded = false) {
                    vm.uiState.value.listenAvailable shouldBe true
                }
            }
        }

        context("when offline") {
            context("when the audiobook is not downloaded") {
                it("#99 TC-16: is false") {
                    withHarness(online = false, downloaded = false) {
                        vm.reload(1)

                        vm.uiState.value.listenAvailable shouldBe false
                    }
                }

                it("#99 TC-16: asks about the audiobook") {
                    withHarness(online = false, downloaded = false) {
                        vm.reload(1)

                        availability.calls shouldContain (uuid to ProgressFormat.Listening)
                    }
                }

                context("when a reload finds a download made meanwhile") {
                    it("#99 TC-17, TC-20: is true") {
                        withHarness(online = false, downloaded = false) {
                            vm.reload(1)
                            availability.answer = true

                            vm.reload(2)

                            vm.uiState.value.listenAvailable shouldBe true
                        }
                    }
                }
            }
        }

        it("#99 TC-18: follows connectivity in both directions") {
            withHarness(online = true, downloaded = false) {
                connectivity.isOnline.value = false
                val offline = vm.uiState.value.listenAvailable
                connectivity.isOnline.value = true

                listOf(offline, vm.uiState.value.listenAvailable) shouldBe listOf(false, true)
            }
        }
    }

    describe("the immersive read") {
        it("#99 TC-47: starts the given file playing") {
            withHarness {
                val file = randomLong()

                vm.onImmersiveRead(file)

                controller.commands shouldBe listOf(TestAudiobookController.Command.Load(uuid, file, null, true))
            }
        }
    }
})
