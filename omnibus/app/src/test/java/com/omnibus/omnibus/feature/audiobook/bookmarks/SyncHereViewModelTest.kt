package com.omnibus.omnibus.feature.audiobook.bookmarks

import com.omnibus.omnibus.data.audiobook.playback.FakeAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.playingBook
import com.omnibus.omnibus.data.audiobook.syncpoint.FakeSyncPointRepository
import com.omnibus.omnibus.data.audiobook.syncpoint.SyncPointError
import com.omnibus.omnibus.data.audiobook.syncpoint.SyncPointResult
import com.omnibus.omnibus.data.network.FakeConnectivityMonitor
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private const val UUID = "book-1"

/** The Sync here cell's labels, as the player reads them from its strings. */
private const val SYNC_HERE = "Sync here"
private const val SYNCED = "Synced ✓"
private const val NEEDS_CONNECTION = "Needs a connection"

/** The player's controller, connectivity, sync repository and ViewModel. */
private class SyncHarness(initial: PlaybackState = PlaybackState(), online: Boolean = true) {
    val controller = FakeAudiobookController(initial)
    val connectivity = FakeConnectivityMonitor(online)
    val sync = FakeSyncPointRepository()
    val collecting = CollectingScope()
    val vm = SyncHereViewModel(UUID, controller, connectivity, sync)
        .also { collecting.collect(it.uiState) }
}

/** Runs [block] on a [SyncHarness], and cancels its collection afterwards. */
private inline fun withSyncHarness(
    initial: PlaybackState = PlaybackState(),
    online: Boolean = true,
    block: SyncHarness.() -> Unit,
) {
    val harness = SyncHarness(initial, online)
    try {
        harness.block()
    } finally {
        harness.collecting.cancel()
    }
}

/** A loaded book of [UUID] with an ebook, at [positionSeconds]. */
private fun ebookState(positionSeconds: Double = 3725.5, bookFileId: Long = 42L, isLoading: Boolean = false) =
    playbackState(
        book = playingBook(bookUuid = UUID, bookFileId = bookFileId, hasEbook = true),
        positionSeconds = positionSeconds,
        isLoading = isLoading,
    )

/** The cell's label over [state], or null when the cell is hidden. */
private fun labelOf(state: SyncHereUIState): String? = syncHereButton(
    state = state,
    syncHereLabel = SYNC_HERE,
    syncedLabel = SYNCED,
    needsConnection = NEEDS_CONNECTION,
    onClick = {},
)?.label

class SyncHereViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("the cell's state") {

        // TC28, R7, AC3: the cell shows only for this book, loaded, with an ebook
        it("TC28: visible only for this book, loaded with an ebook") {
            withSyncHarness {
                controller.state.value = ebookState()
                vm.uiState.value.visible shouldBe true

                controller.state.value = playbackState(book = playingBook(bookUuid = UUID, hasEbook = false))
                vm.uiState.value.visible shouldBe false

                controller.state.value = playbackState(book = playingBook(bookUuid = "other-book", hasEbook = true))
                vm.uiState.value.visible shouldBe false

                controller.state.value = PlaybackState()
                vm.uiState.value.visible shouldBe false
            }
        }

        // TC29, R10, AC4: online follows the connectivity monitor both ways
        it("TC29: online follows the connectivity monitor") {
            withSyncHarness(online = true) {
                connectivity.isOnline.value = false
                vm.uiState.value.online shouldBe false

                connectivity.isOnline.value = true
                vm.uiState.value.online shouldBe true
            }
        }
    }

    describe("declaring a sync point") {

        // TC30, R8, I-BMK-3: one call with the book file and the second captured at the tap
        it("TC30: sends the file and the position captured at the tap") {
            withSyncHarness {
                val gate = CompletableDeferred<SyncPointResult>()
                sync.onDeclare = { _, _, _ -> gate.await() }
                controller.state.value = ebookState(positionSeconds = 3725.5, bookFileId = 42L)

                vm.onSyncHere()
                controller.state.value = ebookState(positionSeconds = 9999.0, bookFileId = 42L)
                gate.complete(SyncPointResult.Declared)

                sync.calls.single() shouldBe Triple(UUID, 42L, 3725.5)
            }
        }

        // TC31, R8: no request while offline, loading, another book, no book, or without an ebook
        it("TC31: a tap is ignored when offline, loading, another book, no book, or no ebook") {
            withSyncHarness(online = false) {
                controller.state.value = ebookState()
                vm.onSyncHere()

                connectivity.isOnline.value = true
                controller.state.value = ebookState(isLoading = true)
                vm.onSyncHere()

                controller.state.value = playbackState(book = playingBook(bookUuid = "other-book", hasEbook = true))
                vm.onSyncHere()

                controller.state.value = PlaybackState()
                vm.onSyncHere()

                controller.state.value = playbackState(book = playingBook(bookUuid = UUID, hasEbook = false))
                vm.onSyncHere()

                sync.calls.isEmpty() shouldBe true
            }
        }

        // TC32, R8: a tap during a request is ignored; once it ends, a tap is accepted again
        it("TC32: taps during a request are ignored, and a later tap is accepted") {
            withSyncHarness {
                val gate = CompletableDeferred<SyncPointResult>()
                sync.onDeclare = { _, _, _ -> gate.await() }
                controller.state.value = ebookState()

                vm.onSyncHere()
                vm.onSyncHere()
                vm.onSyncHere()
                sync.calls.size shouldBe 1

                sync.onDeclare = { _, _, _ -> SyncPointResult.Declared }
                gate.complete(SyncPointResult.Failed(SyncPointError.Unreachable))
                vm.onSyncHere()

                sync.calls.size shouldBe 2
            }
        }

        // TC33, R9, P-BMK-4, AC3: a declared sync point turns the label into Synced
        it("TC33: a declared sync point reads as synced") {
            withSyncHarness {
                controller.state.value = ebookState()

                vm.onSyncHere()

                vm.uiState.value.synced shouldBe true
                labelOf(vm.uiState.value) shouldBe SYNCED
            }
        }

        // TC34, R9: every failure leaves the label as Sync here, with no exception
        it("TC34: every failure leaves the label as Sync here") {
            withSyncHarness {
                controller.state.value = ebookState()
                val failures = listOf(
                    SyncPointError.NoServer,
                    SyncPointError.Unreachable,
                    SyncPointError.SessionExpired,
                    SyncPointError.BookNotFound,
                    SyncPointError.Conflict,
                    SyncPointError.Rejected,
                    SyncPointError.Http(500),
                    SyncPointError.UnexpectedResponse,
                )

                for (failure in failures) {
                    sync.onDeclare = { _, _, _ -> SyncPointResult.Failed(failure) }
                    vm.onSyncHere()
                    vm.uiState.value.synced shouldBe false
                    labelOf(vm.uiState.value) shouldBe SYNC_HERE
                }
                sync.calls.size shouldBe failures.size
            }
        }

        // TC35, R9: once synced, a later tap sends again, and the label stays Synced whatever that answer is
        it("TC35: a tap after a sync sends again and keeps the label") {
            withSyncHarness {
                controller.state.value = ebookState()
                sync.onDeclare = { _, _, _ -> SyncPointResult.Declared }
                vm.onSyncHere()

                sync.onDeclare = { _, _, _ -> SyncPointResult.Failed(SyncPointError.Unreachable) }
                vm.onSyncHere()
                vm.uiState.value.synced shouldBe true
                labelOf(vm.uiState.value) shouldBe SYNCED

                sync.onDeclare = { _, _, _ -> SyncPointResult.Declared }
                vm.onSyncHere()
                sync.calls.size shouldBe 3
                labelOf(vm.uiState.value) shouldBe SYNCED
            }
        }

        // TC36, R9: a fresh player entry starts unsynced
        it("TC36: a fresh ViewModel for the same book starts unsynced") {
            withSyncHarness {
                controller.state.value = ebookState()
                vm.onSyncHere()
                vm.uiState.value.synced shouldBe true

                val fresh = SyncHereViewModel(UUID, controller, connectivity, sync)
                collecting.collect(fresh.uiState)

                fresh.uiState.value.synced shouldBe false
            }
        }

        // TC37, R9: the synced flag lives in the ViewModel, so a collector restart keeps it
        it("TC37: synced survives a collector restart") {
            withSyncHarness {
                controller.state.value = ebookState()
                val collector = CoroutineScope(Job() + Dispatchers.Main).launch { vm.uiState.collect {} }
                vm.onSyncHere()
                collector.cancelAndJoin()

                vm.uiState.first { it.visible }.synced shouldBe true
            }
        }
    }
})
