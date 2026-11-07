package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.PlayingBook
import com.omnibus.omnibus.data.audiobook.playback.UnloadReason
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import com.omnibus.omnibus.di.ClientIdSource
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of
import java.util.TimeZone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** The checkpoints that queue a session, for the invariants over all of them. */
private enum class Checkpoint { Pause, Background, Ended, SwitchBook, Stop }

private suspend fun ListeningSessionTracker.fire(checkpoint: Checkpoint, state: PlaybackState) {
    when (checkpoint) {
        Checkpoint.Pause -> onPause(state)
        Checkpoint.Background -> onAppBackground(state)
        Checkpoint.Ended -> onEnded(state)
        Checkpoint.SwitchBook -> onUnload(state, UnloadReason.SwitchBook)
        Checkpoint.Stop -> onUnload(state, UnloadReason.Stop)
    }
}

/** Client ids that never repeat. */
private fun distinctClientIds(): ClientIdSource {
    var minted = 0
    return ClientIdSource { "c-${++minted}" }
}

private fun playing(book: PlayingBook, rate: Double = 1.0) = playbackState(book, isPlaying = true, rate = rate)

/** Session accumulation and checkpoints (I-SESS-1 to I-SESS-3, P-SYNC-4). Each tick is 0.5 s, whatever the rate. */
@OptIn(ExperimentalCoroutinesApi::class)
class ListeningSessionTrackerTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    fun TestScope.sut(
        repository: ListeningProgressRepository = TestListeningProgressRepository(),
        time: TimeSource = TestTimeSource(),
        zone: DeviceTimeZone = TestDeviceTimeZone(),
    ) = ListeningSessionTracker(repository, time, distinctClientIds(), zone, backgroundScope)

    fun ListeningSessionTracker.ticks(count: Int, state: PlaybackState) = repeat(count) { onTick(state) }

    describe("adding up listening time") {

        // TC-64
        it("TC-64: counts wall time, for each rate (I-SESS-2, P-SYNC-4)") {
            checkAll(Exhaustive.of(0.5, 1.0, 2.0, 3.0)) { rate ->
                runTest {
                    val book = randomPlayingBook()
                    val repository = TestListeningProgressRepository()
                    val tracker = sut(repository)

                    tracker.ticks(60, playing(book, rate))
                    tracker.onPause(playing(book, rate))
                    runCurrent()

                    repository.reports.single().progressUnits shouldBe 30L
                }
            }
        }

        context("when a tick is for another book") {
            // TC-70
            it("TC-70: restarts the total (I-SESS-3)") {
                runTest {
                    val book = randomPlayingBook()
                    val other = book.copy(bookUuid = "${book.bookUuid}-other")
                    val repository = TestListeningProgressRepository()
                    val tracker = sut(repository)
                    tracker.ticks(9, playing(book))

                    tracker.onTick(playing(other))
                    tracker.onPause(playing(other))
                    runCurrent()

                    repository.reports.shouldBeEmpty()
                }
            }
        }
    }

    describe("reporting at a checkpoint") {

        context("when under 5 s was listened") {

            // TC-67
            it("TC-67: carries the remainder to the next checkpoint (I-SESS-3)") {
                runTest {
                    val book = randomPlayingBook()
                    val repository = TestListeningProgressRepository()
                    val tracker = sut(repository)
                    tracker.ticks(9, playing(book))
                    tracker.onPause(playing(book))

                    tracker.ticks(9, playing(book))
                    tracker.onPause(playing(book))
                    runCurrent()

                    repository.reports.single().progressUnits shouldBe 9L
                }
            }

            // TC-69
            it("TC-69: loses the remainder on each kind of unload (I-SESS-3)") {
                checkAll(Exhaustive.enum<UnloadReason>()) { reason ->
                    runTest {
                        val book = randomPlayingBook()
                        val repository = TestListeningProgressRepository()
                        val tracker = sut(repository)
                        tracker.ticks(9, playing(book))
                        tracker.onUnload(playing(book), reason)

                        tracker.ticks(9, playing(book))
                        tracker.onPause(playing(book))
                        runCurrent()

                        repository.reports.shouldBeEmpty()
                    }
                }
            }
        }

        context("when 5 s was listened") {
            // TC-66: a report zeroes the total, so the second pause adds nothing
            it("TC-66: reports it once (I-SESS-3)") {
                runTest {
                    val book = randomPlayingBook()
                    val repository = TestListeningProgressRepository()
                    val tracker = sut(repository)
                    tracker.ticks(10, playing(book))

                    tracker.onPause(playing(book))
                    tracker.onPause(playing(book))
                    runCurrent()

                    repository.reports.map { it.progressUnits } shouldBe listOf(5L)
                }
            }

            // TC-68
            it("TC-68: reports on each kind of checkpoint (I-SESS-3)") {
                checkAll(Exhaustive.enum<Checkpoint>()) { checkpoint ->
                    runTest {
                        val book = randomPlayingBook()
                        val repository = TestListeningProgressRepository()
                        val tracker = sut(repository)
                        tracker.ticks(12, playing(book))

                        tracker.fire(checkpoint, playing(book))
                        runCurrent()

                        repository.reports.map { it.progressUnits } shouldBe listOf(6L)
                    }
                }
            }
        }

        context("when the checkpoint is an unload") {
            // TC-71: the report is in local storage when the unload returns
            it("TC-71: waits for the report to be queued (I-SESS-3)") {
                runTest {
                    val book = randomPlayingBook()
                    val repository = TestListeningProgressRepository()
                    val tracker = sut(repository)
                    tracker.ticks(12, playing(book))
                    repository.queueGate = CompletableDeferred()

                    val unload = backgroundScope.launch { tracker.onUnload(playing(book), UnloadReason.Stop) }
                    runCurrent()

                    unload.isCompleted shouldBe false
                }
            }
        }
    }

    describe("the report") {

        // TC-72
        it("TC-72: spans the whole seconds listened up to now (I-SESS-1)") {
            runTest {
                val book = randomPlayingBook()
                val repository = TestListeningProgressRepository()
                val tracker = sut(repository, TestTimeSource(1_000_000L), TestDeviceTimeZone(TimeZone.getTimeZone("UTC")))
                tracker.ticks(25, playing(book))

                tracker.onPause(playing(book))
                runCurrent()

                repository.reports.single() shouldBe ListeningSessionReport(
                    bookUuid = book.bookUuid,
                    startedAt = 988L,
                    endedAt = 1_000L,
                    progressUnits = 12L,
                    clientId = "c-1",
                    utcOffsetMinutes = 0,
                    timeZone = "UTC",
                )
            }
        }

        // TC-72: the server drops a repeated client id as a retry
        it("TC-72: has its own client id (I-SESS-1)") {
            runTest {
                val book = randomPlayingBook()
                val repository = TestListeningProgressRepository()
                val tracker = sut(repository)

                repeat(2) {
                    tracker.ticks(10, playing(book))
                    tracker.onPause(playing(book))
                }
                runCurrent()

                repository.reports.map { it.clientId }.distinct().size shouldBe 2
            }
        }

        // TC-73
        it("TC-73: carries the zone's offset at the end instant, over zones in each season (I-SESS-1)") {
            checkAll(
                Exhaustive.of(
                    Triple("Asia/Kolkata", 1_000_000L, 330),
                    Triple("America/Los_Angeles", 1_768_478_400_000L, -480),
                    Triple("America/New_York", 1_768_478_400_000L, -300),
                    Triple("America/New_York", 1_784_116_800_000L, -240),
                ),
            ) { (zone, atMillis, offset) ->
                runTest {
                    val book = randomPlayingBook()
                    val repository = TestListeningProgressRepository()
                    val tracker = sut(repository, TestTimeSource(atMillis), TestDeviceTimeZone(TimeZone.getTimeZone(zone)))
                    tracker.ticks(10, playing(book))

                    tracker.onPause(playing(book))
                    runCurrent()

                    repository.reports.single().utcOffsetMinutes to repository.reports.single().timeZone shouldBe
                        (offset to zone)
                }
            }
        }
    }
})
