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
import io.kotest.property.exhaustive.collection
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

private val allSyncPointErrors = listOf(
    SyncPointError.NoServer,
    SyncPointError.Unreachable,
    SyncPointError.SessionExpired,
    SyncPointError.BookNotFound,
    SyncPointError.Conflict,
    SyncPointError.Rejected,
    SyncPointError.Http(500),
    SyncPointError.UnexpectedResponse,
)

private fun randomFailure(): SyncPointResult = SyncPointResult.Failed(randomOf(allSyncPointErrors))

private fun online() = TestConnectivityMonitor(online = true)

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
        // TC-1, R1, AC1: a fresh player starts Idle
        it("TC-1: starts idle") {
            sut().uiState.value.status shouldBe SyncHereStatus.Idle
        }

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

        // TC-8, R7, R8: a settled status survives going offline and back, and a playback change
        it("TC-8: keeps synced and failed across connectivity and playback changes") {
            checkAll(
                Exhaustive.of(
                    SyncPointResult.Declared to SyncHereStatus.Synced,
                    randomFailure() to SyncHereStatus.Failed,
                ),
            ) { (answer, expected) ->
                val uuid = randomString()
                val state = ebookState(uuid)
                val controller = TestAudiobookController(state)
                val connectivity = online()
                val sync = TestSyncPointRepository()
                sync.onDeclare = { _, _, _ -> answer }
                val vm = sut(uuid, controller, connectivity, sync)
                vm.onSyncHere()

                connectivity.isOnline.value = false
                vm.uiState.value.status shouldBe expected
                connectivity.isOnline.value = true
                vm.uiState.value.status shouldBe expected
                controller.state.value = state.copy(positionSeconds = state.positionSeconds + 1)
                vm.uiState.value.status shouldBe expected
            }
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
            val vm = sut(uuid, controller, connectivity = online(), sync = sync)

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
            // TC-2, R1, R2, AC1: the cell reads Syncing as soon as the tap is accepted
            it("TC-2: reads as syncing before the server answers") {
                val uuid = randomString()
                val sync = TestSyncPointRepository()
                val gate = CompletableDeferred<SyncPointResult>()
                sync.onDeclare = { _, _, _ -> gate.await() }
                val vm = sut(uuid, TestAudiobookController(ebookState(uuid)), connectivity = online(), sync = sync)

                vm.onSyncHere()

                vm.uiState.value.status shouldBe SyncHereStatus.Syncing
            }

            // TC-3, R3: a tap during a request is ignored
            it("TC-3: ignores further taps") {
                val uuid = randomString()
                val sync = TestSyncPointRepository()
                val gate = CompletableDeferred<SyncPointResult>()
                sync.onDeclare = { _, _, _ -> gate.await() }
                val vm = sut(uuid, TestAudiobookController(ebookState(uuid)), connectivity = online(), sync = sync)

                vm.onSyncHere()
                vm.onSyncHere()

                sync.calls shouldHaveSize 1
            }

            // TC-9, R4, R8, AC7: an answer that lands while offline still sets the status
            it("TC-9: still takes the answer after going offline") {
                checkAll(
                    Exhaustive.of(
                        SyncPointResult.Declared to SyncHereStatus.Synced,
                        randomFailure() to SyncHereStatus.Failed,
                    ),
                ) { (answer, expected) ->
                    val uuid = randomString()
                    val sync = TestSyncPointRepository()
                    val gate = CompletableDeferred<SyncPointResult>()
                    sync.onDeclare = { _, _, _ -> gate.await() }
                    val connectivity = online()
                    val vm = sut(uuid, TestAudiobookController(ebookState(uuid)), connectivity, sync)
                    vm.onSyncHere()

                    connectivity.isOnline.value = false
                    gate.complete(answer)

                    vm.uiState.value.status shouldBe expected
                    vm.uiState.value.online shouldBe false
                }
            }
        }

        context("when a request has ended") {
            context("when tapped again") {
                // TC-6, R5, AC4: the latest tap's answer is the status, whatever the earlier one was
                it("TC-6: shows the latest tap's result") {
                    checkAll(
                        Exhaustive.of(
                            Triple(SyncPointResult.Declared, SyncPointResult.Declared, SyncHereStatus.Synced),
                            Triple(SyncPointResult.Declared, randomFailure(), SyncHereStatus.Failed),
                            Triple(randomFailure(), SyncPointResult.Declared, SyncHereStatus.Synced),
                            Triple(randomFailure(), randomFailure(), SyncHereStatus.Failed),
                        ),
                    ) { (first, second, expected) ->
                        val uuid = randomString()
                        val sync = TestSyncPointRepository()
                        sync.onDeclare = { _, _, _ -> first }
                        val vm = sut(uuid, TestAudiobookController(ebookState(uuid)), connectivity = online(), sync = sync)
                        vm.onSyncHere()

                        sync.onDeclare = { _, _, _ -> second }
                        vm.onSyncHere()

                        vm.uiState.value.status shouldBe expected
                    }
                }
            }
        }

        context("when the server declares the sync point") {
            // TC-4, R4, P-BMK-4, AC2: a declared sync point turns the status to Synced
            it("TC-4: reads as synced") {
                val uuid = randomString()
                val sync = TestSyncPointRepository()
                sync.onDeclare = { _, _, _ -> SyncPointResult.Declared }
                val vm = sut(uuid, TestAudiobookController(ebookState(uuid)), connectivity = online(), sync = sync)

                vm.onSyncHere()

                vm.uiState.value.status shouldBe SyncHereStatus.Synced
            }
        }

        context("when the request fails") {
            // TC-5, R4, AC2, AC4: every failure reads as Failed, with no exception
            it("TC-5: reads as failed for every error") {
                checkAll(Exhaustive.collection(allSyncPointErrors)) { error ->
                    val uuid = randomString()
                    val sync = TestSyncPointRepository()
                    sync.onDeclare = { _, _, _ -> SyncPointResult.Failed(error) }
                    val vm = sut(uuid, TestAudiobookController(ebookState(uuid)), connectivity = online(), sync = sync)

                    vm.onSyncHere()

                    vm.uiState.value.status shouldBe SyncHereStatus.Failed
                }
            }

            // TC-7, R7, AC3: a tap from Failed, online, sends a new request through Syncing
            context("when tapped again") {
                it("TC-7: re-sends from failed through syncing") {
                    val uuid = randomString()
                    val sync = TestSyncPointRepository()
                    sync.onDeclare = { _, _, _ -> randomFailure() }
                    val vm = sut(uuid, TestAudiobookController(ebookState(uuid)), connectivity = online(), sync = sync)
                    vm.onSyncHere()

                    val gate = CompletableDeferred<SyncPointResult>()
                    sync.onDeclare = { _, _, _ -> gate.await() }
                    vm.onSyncHere()

                    sync.calls shouldHaveSize 2
                    vm.uiState.value.status shouldBe SyncHereStatus.Syncing
                }
            }

            context("when the failure was announced") {
                // TC-11, R9, AC5: the announcement is not asked for again on a connectivity change
                it("TC-11: does not ask again on connectivity change") {
                    val uuid = randomString()
                    val sync = TestSyncPointRepository()
                    sync.onDeclare = { _, _, _ -> randomFailure() }
                    val connectivity = online()
                    val vm = sut(uuid, TestAudiobookController(ebookState(uuid)), connectivity, sync)
                    vm.onSyncHere()

                    vm.onFailureAnnounced()
                    vm.uiState.value.announceFailure shouldBe false
                    connectivity.isOnline.value = false
                    connectivity.isOnline.value = true

                    vm.uiState.value.announceFailure shouldBe false
                }

                // TC-12, R9, AC5: a later failure, after a new tap, is announced again
                it("TC-12: asks again for a later failure") {
                    val uuid = randomString()
                    val sync = TestSyncPointRepository()
                    sync.onDeclare = { _, _, _ -> randomFailure() }
                    val vm = sut(uuid, TestAudiobookController(ebookState(uuid)), connectivity = online(), sync = sync)
                    vm.onSyncHere()
                    vm.onFailureAnnounced()

                    vm.onSyncHere()

                    vm.uiState.value.announceFailure shouldBe true
                }
            }
        }

        // TC-10, R9, AC5: an announcement is asked for on a failure, and never on a success
        it("TC-10: asks for an announcement only for a failure") {
            checkAll(
                Exhaustive.of(
                    SyncPointResult.Declared to false,
                    randomFailure() to true,
                ),
            ) { (answer, expected) ->
                val uuid = randomString()
                val sync = TestSyncPointRepository()
                sync.onDeclare = { _, _, _ -> answer }
                val vm = sut(uuid, TestAudiobookController(ebookState(uuid)), connectivity = online(), sync = sync)

                vm.onSyncHere()

                vm.uiState.value.announceFailure shouldBe expected
            }
        }
    }
})
