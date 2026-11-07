package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.PlayingBook
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.UnloadReason
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Every write path the sync offers, for the invariants over all of them. */
private enum class WritePath { Tick, Pause, Background, Ended, UnloadStop, ForceSave }

/** Sends [state] down [path]. [WritePath.ForceSave] reads the controller, so [state] is set on it first. */
private suspend fun DefaultListeningPositionSync.fire(
    path: WritePath,
    state: PlaybackState,
    controller: TestAudiobookController,
) {
    when (path) {
        WritePath.Tick -> onTick(state)
        WritePath.Pause -> onPause(state)
        WritePath.Background -> onAppBackground(state)
        WritePath.Ended -> onEnded(state)
        WritePath.UnloadStop -> onUnload(state, UnloadReason.Stop)
        WritePath.ForceSave -> {
            controller.state.value = state
            forceSave()
        }
    }
}

private fun at(book: PlayingBook, seconds: Double = randomDouble(0.01, 3_000.0)) =
    playbackState(book, positionSeconds = seconds)

/**
 * The position sync (C8, I-SAVE-1 to I-SAVE-3, I-RES-5, I-RES-6): the guards on what is written, the push throttle and
 * the forced writes. Callbacks are called directly, and the scope runs on the test's virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultListeningPositionSyncTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    fun TestScope.sut(
        repository: ListeningProgressRepository = TestListeningProgressRepository(),
        time: TimeSource = TestTimeSource(),
        controller: TestAudiobookController = TestAudiobookController(),
    ) = DefaultListeningPositionSync(repository, dagger.Lazy<AudiobookController> { controller }, time, backgroundScope)

    /** Sends one state down a tick at each of [millis], recording the push flag of each save. */
    fun TestScope.tickAt(sync: DefaultListeningPositionSync, time: TestTimeSource, state: PlaybackState, vararg millis: Long) {
        millis.forEach {
            time.set(it)
            sync.onTick(state)
            runCurrent()
        }
    }

    describe("what may be written") {

        context("when no load has settled") {
            // TC-51
            it("TC-51: writes nothing on any path (I-RES-5)") {
                checkAll(Exhaustive.enum<WritePath>()) { path ->
                    runTest {
                        val repository = TestListeningProgressRepository()
                        val controller = TestAudiobookController()
                        val sync = sut(repository, controller = controller)

                        sync.fire(path, at(randomPlayingBook()), controller)
                        runCurrent()

                        repository.saves.shouldBeEmpty()
                    }
                }
            }
        }

        context("when a load has settled") {
            // TC-52
            it("TC-52: ignores another book or file on any path (I-RES-5)") {
                val book = randomPlayingBook()
                checkAll(
                    Exhaustive.enum<WritePath>(),
                    Exhaustive.of(book.copy(bookUuid = "${book.bookUuid}-other"), book.copy(bookFileId = book.bookFileId + 1)),
                ) { path, other ->
                    runTest {
                        val repository = TestListeningProgressRepository()
                        val controller = TestAudiobookController()
                        val sync = sut(repository, controller = controller)
                        sync.onSettled(at(book), randomDouble())

                        sync.fire(path, at(other), controller)
                        runCurrent()

                        repository.saves.shouldBeEmpty()
                    }
                }
            }

            // TC-53
            it("TC-53: ignores position 0 on any path (I-RES-5, I-SAVE-3)") {
                checkAll(Exhaustive.enum<WritePath>()) { path ->
                    runTest {
                        val book = randomPlayingBook()
                        val repository = TestListeningProgressRepository()
                        val controller = TestAudiobookController()
                        val sync = sut(repository, controller = controller)
                        sync.onSettled(at(book), randomDouble())

                        sync.fire(path, at(book, seconds = 0.0), controller)
                        runCurrent()

                        repository.saves.shouldBeEmpty()
                    }
                }
            }
        }

        context("when the book was unloaded") {
            // TC-54
            it("TC-54: ignores a tick (I-RES-5)") {
                runTest {
                    val book = randomPlayingBook()
                    val repository = TestListeningProgressRepository()
                    val sync = sut(repository)
                    sync.onSettled(at(book), randomDouble())
                    sync.onUnload(at(book, seconds = 0.0), randomEnum<UnloadReason>())

                    sync.onTick(at(book))
                    runCurrent()

                    repository.saves.shouldBeEmpty()
                }
            }
        }

        context("when a new load has begun") {
            // TC-54
            it("TC-54: ignores a tick before its settle (I-RES-5)") {
                runTest {
                    val book = randomPlayingBook()
                    val repository = TestListeningProgressRepository()
                    val sync = sut(repository)
                    sync.onSettled(at(book), randomDouble())
                    sync.loadBegan()

                    sync.onTick(at(book))
                    runCurrent()

                    repository.saves.shouldBeEmpty()
                }
            }
        }
    }

    describe("saving the position") {
        context("on a tick") {
            // TC-55
            it("TC-55: saves the position at the current unix second (I-SAVE-1, I-SAVE-2)") {
                runTest {
                    val book = randomPlayingBook()
                    val seconds = randomDouble(0.01, 3_000.0)
                    val repository = TestListeningProgressRepository()
                    val time = TestTimeSource(7_999L)
                    val sync = sut(repository, time)
                    sync.onSettled(at(book), randomDouble())

                    sync.onTick(at(book, seconds))
                    runCurrent()

                    repository.saves.single().first shouldBe AudioPosition(book.bookUuid, seconds, book.bookFileId, 7L)
                }
            }

            // TC-56
            it("TC-56: skips the first tick's push, then pushes at most every 5 s (I-SAVE-2)") {
                runTest {
                    val book = randomPlayingBook()
                    val repository = TestListeningProgressRepository()
                    val time = TestTimeSource()
                    val sync = sut(repository, time)
                    sync.onSettled(at(book), randomDouble())

                    tickAt(sync, time, at(book), 0L, 500L, 5_499L, 5_500L, 5_501L)

                    repository.saves.map { it.second } shouldBe listOf(false, true, false, false, true)
                }
            }
        }
    }

    describe("forcing a write") {

        // TC-58, TC-60
        it("TC-58, TC-60: pushes the position on pause, background, end or a forced save (I-SAVE-3)") {
            checkAll(
                Exhaustive.of(WritePath.Pause, WritePath.Background, WritePath.Ended, WritePath.ForceSave),
            ) { path ->
                runTest {
                    val book = randomPlayingBook()
                    val seconds = randomDouble(0.01, 3_000.0)
                    val repository = TestListeningProgressRepository()
                    val controller = TestAudiobookController()
                    val sync = sut(repository, TestTimeSource(1_000L), controller)
                    sync.onSettled(at(book), randomDouble())

                    sync.fire(path, at(book, seconds), controller)
                    runCurrent()

                    repository.saves shouldBe listOf(AudioPosition(book.bookUuid, seconds, book.bookFileId, 1L) to true)
                }
            }
        }

        // TC-59
        it("TC-59: pushes the book left on each kind of unload (I-SAVE-3)") {
            checkAll(Exhaustive.enum<UnloadReason>()) { reason ->
                runTest {
                    val book = randomPlayingBook()
                    val seconds = randomDouble(0.01, 3_000.0)
                    val repository = TestListeningProgressRepository()
                    val sync = sut(repository, TestTimeSource(7_000L))
                    sync.onSettled(at(book), randomDouble())

                    sync.onUnload(at(book, seconds), reason)

                    repository.saves shouldBe listOf(AudioPosition(book.bookUuid, seconds, book.bookFileId, 7L) to true)
                }
            }
        }

        // TC-62
        it("TC-62: holds the next ticks back for 5 s (I-SAVE-2)") {
            runTest {
                val book = randomPlayingBook()
                val repository = TestListeningProgressRepository()
                val time = TestTimeSource()
                val sync = sut(repository, time)
                sync.onSettled(at(book), randomDouble())
                tickAt(sync, time, at(book), 0L, 500L)

                time.set(1_000L)
                sync.onPause(at(book))
                tickAt(sync, time, at(book), 5_999L, 6_001L)

                repository.saves.map { it.second } shouldBe listOf(false, true, true, false, true)
            }
        }

        context("when the save fails") {
            // TC-61
            it("TC-61: throws nothing on any path (I-SAVE-3)") {
                checkAll(Exhaustive.enum<WritePath>()) { path ->
                    runTest {
                        val book = randomPlayingBook()
                        val repository = TestListeningProgressRepository()
                        repository.saveFailure = IllegalStateException("the store is gone")
                        val controller = TestAudiobookController()
                        val sync = sut(repository, controller = controller)
                        sync.onSettled(at(book), randomDouble())

                        shouldNotThrowAny {
                            sync.fire(path, at(book), controller)
                            runCurrent()
                        }
                    }
                }
            }
        }
    }
})
