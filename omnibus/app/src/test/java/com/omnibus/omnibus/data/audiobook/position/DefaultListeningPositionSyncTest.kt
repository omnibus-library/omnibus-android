package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.FakeAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.PlayingBook
import com.omnibus.omnibus.data.audiobook.playback.UnloadReason
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.playingBook
import com.omnibus.omnibus.testing.FakeTimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Every write path the sync offers, for the tables that check each one. */
private enum class WritePath { Tick, Pause, Background, Ended, UnloadStop, ForceSave }

/** Sends [state] down [path]. [ForceSave] reads the controller, so [state] is set on it first. */
private suspend fun DefaultListeningPositionSync.fire(
    path: WritePath,
    state: PlaybackState,
    controller: FakeAudiobookController,
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

/** A playing state of book-1 (file 11) at [seconds]. */
private fun playing(seconds: Double, book: PlayingBook = playingBook()) =
    playbackState(book = book, positionSeconds = seconds, isPlaying = true)

/**
 * The position sync (C8, I-SAVE-1 to I-SAVE-3, I-RES-5, I-RES-6): the guards on what is written, the push throttle, the
 * forced writes, and the late offer. Callbacks are called directly, and the scope runs on one virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultListeningPositionSyncTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    lateinit var scheduler: TestCoroutineScheduler
    lateinit var scope: CoroutineScope
    lateinit var repo: FakeListeningProgressRepository
    lateinit var time: FakeTimeSource
    lateinit var controller: FakeAudiobookController
    lateinit var sync: DefaultListeningPositionSync

    /** A sync on the current repository, clock and controller. */
    fun newSync(): DefaultListeningPositionSync = DefaultListeningPositionSync(
        repo,
        dagger.Lazy<AudiobookController> { controller },
        time,
        scope,
    )

    beforeEach {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        repo = FakeListeningProgressRepository()
        time = FakeTimeSource()
        controller = FakeAudiobookController()
        sync = newSync()
    }

    afterEach {
        scope.cancel()
        Dispatchers.resetMain()
    }

    describe("guards") {
        // TC-51: nothing is written before or at settle (I-RES-5)
        it("TC-51: nothing is written before or at settle (I-RES-5)") {
            val state = playing(12.5)
            sync.onTick(state)
            sync.onPause(state)
            controller.state.value = state
            sync.forceSave()
            scheduler.runCurrent()
            repo.saves.shouldBeEmpty()

            sync.onSettled(state, 0.0)
            scheduler.runCurrent()
            repo.saves.shouldBeEmpty()
        }

        // TC-52: a write for another book or file is ignored (I-RES-5)
        it("TC-52: a write for another book or file is ignored (I-RES-5)") {
            val other = playingBook(bookUuid = "book-2")
            val otherFile = playingBook(bookFileId = 12L)
            listOf(
                WritePath.Tick to playing(12.5, other),
                WritePath.Pause to playing(12.5, other),
                WritePath.Background to playing(12.5, other),
                WritePath.Ended to playing(12.5, other),
                WritePath.UnloadStop to playing(12.5, other),
                WritePath.Tick to playing(12.5, otherFile),
                WritePath.Pause to playing(12.5, otherFile),
                WritePath.Background to playing(12.5, otherFile),
                WritePath.Ended to playing(12.5, otherFile),
                WritePath.UnloadStop to playing(12.5, otherFile),
            ).forEach { (path, state) ->
                val fresh = newSync()
                fresh.onSettled(playing(12.5), 0.0)
                fresh.fire(path, state, controller)
                scheduler.runCurrent()
                withClue("$path for ${state.book}") {
                    repo.saves.shouldBeEmpty()
                }
            }
        }

        // TC-53: only a positive position is written (I-RES-5, I-SAVE-3)
        it("TC-53: only a positive position is written (I-RES-5, I-SAVE-3)") {
            WritePath.entries.forEach { path ->
                listOf(0.0 to 0, 0.01 to 1).forEach { (position, expectedSaves) ->
                    val fresh = newSync()
                    repo.saves.clear()
                    fresh.onSettled(playing(12.5), 0.0)
                    fresh.fire(path, playing(position), controller)
                    scheduler.runCurrent()
                    withClue("$path at $position") {
                        repo.saves.size shouldBe expectedSaves
                    }
                }
            }
        }

        // TC-54: unload and a new load unsettle (I-RES-5)
        it("TC-54: unload and a new load unsettle (I-RES-5)") {
            val state = playing(40.0)

            sync.onSettled(state, 0.0)
            sync.onUnload(state, UnloadReason.Stop)
            val afterUnload = repo.saves.size
            sync.onTick(state)
            scheduler.runCurrent()
            withClue("a tick after unload") {
                repo.saves.size shouldBe afterUnload
            }

            sync.onSettled(state, 0.0)
            sync.loadBegan()
            val afterNewLoad = repo.saves.size
            sync.onTick(state)
            scheduler.runCurrent()
            withClue("a tick after a new load, before its settle") {
                repo.saves.size shouldBe afterNewLoad
            }

            sync.onSettled(state, 0.0)
            sync.onTick(playing(41.0))
            scheduler.runCurrent()
            withClue("a tick after the new settle") {
                repo.saves.size shouldBe afterNewLoad + 1
            }
        }
    }

    describe("ticks") {
        // TC-55: every tick saves locally with unix-second time (I-SAVE-1, I-SAVE-2)
        it("TC-55: every tick saves locally with unix-second time (I-SAVE-1, I-SAVE-2)") {
            sync.onSettled(playing(12.5), 0.0)
            time.set(7_999)
            sync.onTick(playing(12.5))
            time.set(8_000)
            sync.onTick(playing(12.5))
            repeat(8) { sync.onTick(playing(12.5)) }
            scheduler.runCurrent()

            repo.saves.size shouldBe 10
            repo.saves[0].first shouldBe AudioPosition("book-1", 12.5, 11, 7)
            repo.saves[1].first.clientUpdatedAt shouldBe 8L
        }

        // TC-56: first tick suppressed, then pushes more than 5 s apart (I-SAVE-2)
        it("TC-56: first tick suppressed, then pushes more than 5 s apart (I-SAVE-2)") {
            sync.onSettled(playing(12.5), 0.0)
            listOf(0L to "first", 500L to "second", 5_499L to "third", 5_500L to "fourth", 5_501L to "fifth").forEach { (ms, _) ->
                time.set(ms)
                sync.onTick(playing(12.5))
                scheduler.runCurrent()
            }
            repo.saves.map { it.second } shouldBe listOf(false, true, false, false, true)
        }

        // TC-57: each settle makes a new throttle (I-SAVE-2)
        it("TC-57: each settle makes a new throttle (I-SAVE-2)") {
            sync.onSettled(playing(12.5), 0.0)
            listOf(0L, 500L).forEach { ms ->
                time.set(ms)
                sync.onTick(playing(12.5))
                scheduler.runCurrent()
            }
            time.set(1_000)
            sync.onSettled(playing(12.5), 0.0)
            listOf(1_000L, 1_500L).forEach { ms ->
                time.set(ms)
                sync.onTick(playing(12.5))
                scheduler.runCurrent()
            }
            repo.saves.map { it.second } shouldBe listOf(false, true, false, true)
        }
    }

    describe("forced writes") {
        // TC-58: pause, background and end force a write (I-SAVE-3)
        it("TC-58: pause, background and end force a write (I-SAVE-3)") {
            listOf(WritePath.Pause to 40.0, WritePath.Background to 40.0, WritePath.Ended to 2_700.0).forEach { (path, position) ->
                val fresh = newSync()
                fresh.onSettled(playing(12.5), 0.0)
                time.set(0)
                fresh.onTick(playing(12.5))
                time.set(500)
                fresh.onTick(playing(12.5))
                scheduler.runCurrent()
                val before = repo.saves.size

                time.set(1_000)
                fresh.fire(path, playbackState(positionSeconds = position, isPlaying = false), controller)
                scheduler.runCurrent()
                withClue("$path at $position") {
                    repo.saves.drop(before) shouldBe listOf(AudioPosition("book-1", position, 11, 1) to true)
                }
            }
        }

        // TC-59: close and switching force a write for the book left (I-SAVE-3)
        it("TC-59: close and switching force a write for the book left (I-SAVE-3)") {
            listOf(UnloadReason.Stop, UnloadReason.SwitchBook).forEach { reason ->
                val fresh = newSync()
                fresh.onSettled(playing(12.5), 0.0)
                time.set(7_000)
                fresh.onUnload(playing(40.0), reason)
                withClue("$reason saves on unload") {
                    repo.saves.last() shouldBe (AudioPosition("book-1", 40.0, 11, 7) to true)
                }
                val before = repo.saves.size
                fresh.onTick(playing(41.0))
                scheduler.runCurrent()
                withClue("$reason, a tick afterwards") {
                    repo.saves.size shouldBe before
                }
            }
        }

        // TC-60: forceSave writes now and obeys the guards (I-SAVE-3)
        it("TC-60: forceSave writes now and obeys the guards (I-SAVE-3)") {
            listOf(
                Triple("settled book at 40.0", playing(40.0), 1),
                Triple("no book", PlaybackState(), 0),
                Triple("unsettled book", playing(40.0, playingBook(bookUuid = "book-2")), 0),
                Triple("position 0", playing(0.0), 0),
            ).forEach { (name, state, expectedSaves) ->
                val fresh = newSync()
                repo.saves.clear()
                fresh.onSettled(playing(12.5), 0.0)
                controller.state.value = state
                fresh.forceSave()
                withClue(name) {
                    repo.saves.size shouldBe expectedSaves
                    if (expectedSaves == 1) repo.saves.single().second shouldBe true
                }
            }
        }

        // TC-61: a failing save never throws out of the sync (I-SAVE-3)
        it("TC-61: a failing save never throws out of the sync (I-SAVE-3)") {
            repo.saveFailure = IllegalStateException("the store is gone")
            sync.onSettled(playing(12.5), 0.0)
            // The calls return normally, so any throw fails the test.
            sync.onTick(playing(12.5))
            sync.onPause(playing(12.5))
            scheduler.runCurrent()
            controller.state.value = playing(12.5)
            sync.forceSave()
            sync.onUnload(playing(12.5), UnloadReason.Stop)
            scheduler.runCurrent()
            repo.saves.shouldBeEmpty()
        }

        // TC-62: a forced write re-arms the throttle (I-SAVE-2)
        it("TC-62: a forced write re-arms the throttle (I-SAVE-2)") {
            sync.onSettled(playing(12.5), 0.0)
            listOf(0L to "tick", 500L to "tick", 1_000L to "pause", 5_999L to "tick", 6_001L to "tick").forEach { (ms, kind) ->
                time.set(ms)
                if (kind == "pause") sync.onPause(playing(12.5)) else sync.onTick(playing(12.5))
                scheduler.runCurrent()
            }
            repo.saves.map { it.second } shouldBe listOf(false, true, true, false, true)
        }
    }

    describe("late position") {
        // TC-63: offers for the current load only, cleared by the next load and by unload (I-RES-6)
        it("TC-63: offers for the current load only, cleared by the next load and by unload (I-RES-6)") {
            val offer = LatePosition("book-1", 11L, 60.0)
            val newer = LatePosition("book-1", 11L, 90.0)
            val stale = sync.loadBegan()
            sync.lateServerPosition.value shouldBe null

            val current = sync.loadBegan()
            sync.offerLate(current, offer)
            sync.lateServerPosition.value shouldBe offer

            sync.offerLate(stale, newer)
            sync.lateServerPosition.value shouldBe offer

            val latest = sync.loadBegan()
            sync.lateServerPosition.value shouldBe null

            sync.offerLate(latest, offer)
            sync.onUnload(playbackState(), UnloadReason.Stop)
            sync.lateServerPosition.value shouldBe null
        }
    }
})
