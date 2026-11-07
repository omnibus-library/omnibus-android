package com.omnibus.omnibus.feature.audiobook.bookmarks

import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.randomPlaybackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import com.omnibus.omnibus.data.audiobook.syncpoint.SyncPointError
import com.omnibus.omnibus.data.audiobook.syncpoint.SyncPointResult
import com.omnibus.omnibus.data.audiobook.syncpoint.TestSyncPointRepository
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.testing.randomOf
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** A settled state of the book with [bookUuid], with an ebook, and random otherwise. */
private fun ebookState(bookUuid: String): PlaybackState = randomPlaybackState().copy(
    book = randomPlayingBook().copy(bookUuid = bookUuid, hasEbook = true),
    isLoading = false,
)

private fun randomFailure(): SyncPointResult = SyncPointResult.Failed(
    randomOf(
        listOf(
            SyncPointError.NoServer,
            SyncPointError.Unreachable,
            SyncPointError.SessionExpired,
            SyncPointError.BookNotFound,
            SyncPointError.Conflict,
            SyncPointError.Rejected,
            SyncPointError.Http(500),
            SyncPointError.UnexpectedResponse,
        ),
    ),
)

class SyncHereViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        bookUuid: String = randomString(),
        controller: TestAudiobookController = TestAudiobookController(),
        connectivity: TestConnectivityMonitor = TestConnectivityMonitor(),
        sync: TestSyncPointRepository = TestSyncPointRepository(),
    ) = SyncHereViewModel(bookUuid, controller, connectivity, sync).also { vm ->
        vm.viewModelScope.launch { vm.uiState.collect {} }
    }

    describe("the cell's state") {
        // TC28, R7, AC3: the cell shows only for this book, loaded, with an ebook
        it("TC28: is visible only for this book with an ebook, not one with no ebook, another book or no book") {
            val uuid = randomString()
            val loaded = ebookState(uuid)
            val book = loaded.book!!

            checkAll(
                Exhaustive.of(
                    loaded to true,
                    loaded.copy(book = book.copy(hasEbook = false)) to false,
                    loaded.copy(book = book.copy(bookUuid = "$uuid-other")) to false,
                    PlaybackState() to false,
                ),
            ) { (state, expected) ->
                sut(uuid, TestAudiobookController(state)).uiState.value.visible shouldBe expected
            }
        }

        // TC29, R10, AC4: online follows the connectivity monitor both ways
        it("TC29: follows the connectivity monitor") {
            val connectivity = TestConnectivityMonitor(online = true)
            val vm = sut(connectivity = connectivity)

            connectivity.isOnline.value = false
            vm.uiState.value.online shouldBe false

            connectivity.isOnline.value = true
            vm.uiState.value.online shouldBe true
        }
    }

    describe("declaring a sync point") {
        // TC30, R8, I-BMK-3: one call with the book file and the second captured at the tap
        it("TC30: sends the file with the position from the tap") {
            val uuid = randomString()
            val state = ebookState(uuid)
            val controller = TestAudiobookController(state)
            val sync = TestSyncPointRepository()
            val gate = CompletableDeferred<SyncPointResult>()
            sync.onDeclare = { _, _, _ -> gate.await() }
            val vm = sut(uuid, controller, sync = sync)

            vm.onSyncHere()
            controller.state.value = state.copy(positionSeconds = state.positionSeconds + 1)
            gate.complete(SyncPointResult.Declared)

            sync.calls shouldBe listOf(Triple(uuid, state.book!!.bookFileId, state.positionSeconds))
        }

        // TC31, R8: no request while offline, loading, another book, no book, or without an ebook
        it("TC31: ignores a tap for offline, a load in flight, another book, no book or no ebook") {
            val uuid = randomString()
            val loaded = ebookState(uuid)
            val book = loaded.book!!

            checkAll(
                Exhaustive.of(
                    loaded to false,
                    loaded.copy(isLoading = true) to true,
                    loaded.copy(book = book.copy(bookUuid = "$uuid-other")) to true,
                    PlaybackState() to true,
                    loaded.copy(book = book.copy(hasEbook = false)) to true,
                ),
            ) { (state, online) ->
                val sync = TestSyncPointRepository()

                sut(uuid, TestAudiobookController(state), TestConnectivityMonitor(online), sync).onSyncHere()

                sync.calls.shouldBeEmpty()
            }
        }

        context("when a request is in flight") {
            // TC32, R8: a tap during a request is ignored
            it("TC32: ignores further taps") {
                val uuid = randomString()
                val sync = TestSyncPointRepository()
                val gate = CompletableDeferred<SyncPointResult>()
                sync.onDeclare = { _, _, _ -> gate.await() }
                val vm = sut(uuid, TestAudiobookController(ebookState(uuid)), sync = sync)

                vm.onSyncHere()
                vm.onSyncHere()

                sync.calls shouldHaveSize 1
            }
        }

        context("when a request has ended") {
            // TC32, TC35, R8, R9: once it ends, a tap is accepted again, including after a sync
            it("TC32 TC35: sends again on the next tap") {
                val uuid = randomString()
                val sync = TestSyncPointRepository()
                val vm = sut(uuid, TestAudiobookController(ebookState(uuid)), sync = sync)

                vm.onSyncHere()
                vm.onSyncHere()

                sync.calls shouldHaveSize 2
            }
        }

        context("when the server declares the sync point") {
            // TC33, R9, P-BMK-4, AC3: a declared sync point turns the label into Synced
            it("TC33: reads as synced") {
                val uuid = randomString()
                val vm = sut(uuid, TestAudiobookController(ebookState(uuid)))

                vm.onSyncHere()

                vm.uiState.value.synced shouldBe true
            }
        }

        context("when the request fails") {
            // TC34, R9: every failure leaves the label as Sync here, with no exception
            it("TC34: stays unsynced") {
                val uuid = randomString()
                val sync = TestSyncPointRepository()
                sync.onDeclare = { _, _, _ -> randomFailure() }
                val vm = sut(uuid, TestAudiobookController(ebookState(uuid)), sync = sync)

                vm.onSyncHere()

                vm.uiState.value.synced shouldBe false
            }

            context("when the book was already synced") {
                // TC35, R9: once synced, the label stays Synced whatever a later answer is
                it("TC35: stays synced") {
                    val uuid = randomString()
                    val sync = TestSyncPointRepository()
                    val vm = sut(uuid, TestAudiobookController(ebookState(uuid)), sync = sync)
                    vm.onSyncHere()
                    sync.onDeclare = { _, _, _ -> randomFailure() }

                    vm.onSyncHere()

                    vm.uiState.value.synced shouldBe true
                }
            }
        }
    }
})
