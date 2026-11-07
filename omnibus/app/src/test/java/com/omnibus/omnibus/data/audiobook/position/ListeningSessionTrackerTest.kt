package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.UnloadReason
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.playingBook
import com.omnibus.omnibus.di.ClientIdSource
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.util.TimeZone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** The checkpoints that queue a session, for the tables that check each one. */
private enum class Checkpoint { Pause, Background, Ended, SwitchBook, Stop }

/** Sends [checkpoint] to [tracker] for [state]. */
private suspend fun fire(tracker: ListeningSessionTracker, checkpoint: Checkpoint, state: PlaybackState) {
    when (checkpoint) {
        Checkpoint.Pause -> tracker.onPause(state)
        Checkpoint.Background -> tracker.onAppBackground(state)
        Checkpoint.Ended -> tracker.onEnded(state)
        Checkpoint.SwitchBook -> tracker.onUnload(state, UnloadReason.SwitchBook)
        Checkpoint.Stop -> tracker.onUnload(state, UnloadReason.Stop)
    }
}

/** A playing state of book-1 at position 0. Ticks never read the position. */
private fun playing(rate: Double = 1.0) = playbackState(isPlaying = true, rate = rate)

/** A playing state of book-2. */
private fun playingB() = playbackState(book = playingBook(bookUuid = "book-2"), isPlaying = true)

/**
 * Session accumulation and checkpoints (I-SESS-1 to I-SESS-3, P-SYNC-4). Ticks are 0.5 s each whatever the rate. Each
 * `it` builds a fresh tracker on one virtual scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListeningSessionTrackerTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    lateinit var scheduler: TestCoroutineScheduler
    val scopes = mutableListOf<CoroutineScope>()

    /** A tracker with its fakes. Ids are "c-1", "c-2", and so on. */
    class Harness(scheduler: TestCoroutineScheduler) {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        val repo = TestListeningProgressRepository()
        val time = TestTimeSource()
        val zone = TestDeviceTimeZone()
        private var minted = 0
        val tracker = ListeningSessionTracker(repo, time, ClientIdSource { "c-${++minted}" }, zone, scope)
    }

    fun harness(): Harness = Harness(scheduler).also { scopes += it.scope }

    beforeEach {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    afterEach {
        scopes.forEach { it.cancel() }
        scopes.clear()
        Dispatchers.resetMain()
    }

    describe("accumulation") {
        // TC-64: each tick is 0.5 s of wall time at any rate (I-SESS-2, P-SYNC-4)
        it("TC-64: each tick is 0.5 s of wall time at any rate (I-SESS-2, P-SYNC-4)") {
            val rows = listOf<Pair<String, (Int) -> PlaybackState>>(
                "rate 1.0" to { _ -> playing(1.0) },
                "rate 2.0" to { _ -> playing(2.0) },
                "rate 0.5" to { _ -> playing(0.5) },
                "rates alternating 0.5 and 3.0" to { i -> playing(if (i % 2 == 0) 0.5 else 3.0) },
            )
            rows.forEach { (name, stateAt) ->
                val h = harness()
                repeat(60) { h.tracker.onTick(stateAt(it)) }
                h.tracker.onPause(playing())
                scheduler.runCurrent()
                withClue(name) {
                    h.repo.reports.single().progressUnits shouldBe 30L
                }
            }
        }

        // TC-65: an hour of audio at 2x is 30 minutes (P-SYNC-4)
        it("TC-65: an hour of audio at 2x is 30 minutes (P-SYNC-4)") {
            val h = harness()
            // An hour of audio at 2x takes 30 minutes of wall time: 1800 s, which is 3600 ticks of 0.5 s.
            repeat(3_600) { h.tracker.onTick(playing(2.0)) }
            h.tracker.onPause(playing(2.0))
            scheduler.runCurrent()
            h.repo.reports.single().progressUnits shouldBe 1_800L
        }
    }

    describe("checkpoints") {
        // TC-66: 5 s is the threshold and a report zeroes the total (I-SESS-3)
        it("TC-66: 5 s is the threshold and a report zeroes the total (I-SESS-3)") {
            val nine = harness()
            repeat(9) { nine.tracker.onTick(playing()) }
            nine.tracker.onPause(playing())
            scheduler.runCurrent()
            nine.repo.reports.shouldBeEmpty()

            val ten = harness()
            repeat(10) { ten.tracker.onTick(playing()) }
            ten.tracker.onPause(playing())
            ten.tracker.onPause(playing())
            scheduler.runCurrent()
            ten.repo.reports.map { it.progressUnits } shouldBe listOf(5L)
        }

        // TC-67: a remainder under 5 s carries over (I-SESS-3)
        it("TC-67: a remainder under 5 s carries over (I-SESS-3)") {
            val nineThenNine = harness()
            repeat(9) { nineThenNine.tracker.onTick(playing()) }
            nineThenNine.tracker.onPause(playing())
            repeat(9) { nineThenNine.tracker.onTick(playing()) }
            nineThenNine.tracker.onPause(playing())
            scheduler.runCurrent()
            nineThenNine.repo.reports.map { it.progressUnits } shouldBe listOf(9L)

            val sixteen = harness()
            repeat(16) { sixteen.tracker.onTick(playing()) }
            sixteen.tracker.onPause(playing())
            repeat(16) { sixteen.tracker.onTick(playing()) }
            sixteen.tracker.onPause(playing())
            scheduler.runCurrent()
            sixteen.repo.reports.map { it.progressUnits } shouldBe listOf(8L, 8L)
        }

        // TC-68: every checkpoint kind reports (I-SESS-3)
        it("TC-68: every checkpoint kind reports (I-SESS-3)") {
            listOf(Checkpoint.Background, Checkpoint.Ended, Checkpoint.SwitchBook, Checkpoint.Stop).forEach { kind ->
                val h = harness()
                repeat(12) { h.tracker.onTick(playing()) }
                fire(h.tracker, kind, playing())
                scheduler.runCurrent()
                withClue("$kind") {
                    h.repo.reports.map { it.progressUnits } shouldBe listOf(6L)
                }
            }
            val none = harness()
            none.tracker.onPause(playing())
            scheduler.runCurrent()
            none.repo.reports.shouldBeEmpty()
        }

        // TC-69: unload drops a remainder under 5 s (I-SESS-3)
        it("TC-69: unload drops a remainder under 5 s (I-SESS-3)") {
            val h = harness()
            repeat(6) { h.tracker.onTick(playing()) }
            h.tracker.onUnload(playing(), UnloadReason.SwitchBook)
            repeat(20) { h.tracker.onTick(playingB()) }
            h.tracker.onPause(playingB())
            repeat(8) { h.tracker.onTick(playing()) }
            h.tracker.onUnload(playing(), UnloadReason.Stop)
            repeat(8) { h.tracker.onTick(playing()) }
            h.tracker.onPause(playing())
            scheduler.runCurrent()
            h.repo.reports.map { it.bookUuid to it.progressUnits } shouldBe listOf("book-2" to 10L)
        }

        // TC-70: a tick for another book restarts the total (I-SESS-3)
        it("TC-70: a tick for another book restarts the total (I-SESS-3)") {
            val h = harness()
            repeat(6) { h.tracker.onTick(playing()) }
            h.tracker.onTick(playingB())
            h.tracker.onPause(playingB())
            scheduler.runCurrent()
            withClue("book-2 restarts at 0.5 s") {
                h.repo.reports.shouldBeEmpty()
            }

            repeat(6) { h.tracker.onTick(playing()) }
            h.tracker.onPause(playing())
            repeat(6) { h.tracker.onTick(playingB()) }
            h.tracker.onPause(playingB())
            repeat(6) { h.tracker.onTick(playing()) }
            h.tracker.onPause(playing())
            scheduler.runCurrent()
            withClue("the carry-over is per book") {
                h.repo.reports.shouldBeEmpty()
            }
        }

        // TC-71: unload awaits the queueing (I-SESS-3)
        it("TC-71: unload awaits the queueing (I-SESS-3)") {
            val h = harness()
            repeat(12) { h.tracker.onTick(playing()) }
            h.repo.queueGate = CompletableDeferred()
            val unload = h.scope.launch { h.tracker.onUnload(playing(), UnloadReason.Stop) }
            scheduler.runCurrent()
            unload.isCompleted shouldBe false
            h.repo.queueGate?.complete(Unit)
            scheduler.runCurrent()
            unload.isCompleted shouldBe true
            h.repo.reports.map { it.progressUnits } shouldBe listOf(6L)
        }
    }

    describe("report") {
        // TC-72: started_at is ended_at minus whole seconds, one client id per report (I-SESS-1)
        it("TC-72: started_at is ended_at minus whole seconds, one client id per report (I-SESS-1)") {
            val h = harness()
            h.time.set(1_000_000)
            repeat(25) { h.tracker.onTick(playing()) }
            h.tracker.onPause(playing())
            repeat(12) { h.tracker.onTick(playing()) }
            h.tracker.onPause(playing())
            scheduler.runCurrent()
            h.repo.reports.size shouldBe 2
            h.repo.reports[0] shouldBe ListeningSessionReport(
                bookUuid = "book-1",
                startedAt = 988L,
                endedAt = 1_000L,
                progressUnits = 12L,
                clientId = "c-1",
                utcOffsetMinutes = 0,
                timeZone = "UTC",
            )
            h.repo.reports[1].clientId shouldBe "c-2"
        }

        // TC-73: offset and zone id are read at each report for its end instant (I-SESS-1)
        it("TC-73: offset and zone id are read at each report for its end instant (I-SESS-1)") {
            val h = harness()
            data class Row(val name: String, val zone: String, val atMillis: Long, val offset: Int, val id: String)
            listOf(
                Row("Asia/Kolkata", "Asia/Kolkata", 1_000_000L, 330, "Asia/Kolkata"),
                Row("Los Angeles in January", "America/Los_Angeles", 1_768_478_400_000L, -480, "America/Los_Angeles"),
                Row("New York in January", "America/New_York", 1_768_478_400_000L, -300, "America/New_York"),
                Row("New York in July", "America/New_York", 1_784_116_800_000L, -240, "America/New_York"),
            ).forEach { row ->
                h.zone.zone = TimeZone.getTimeZone(row.zone)
                h.time.set(row.atMillis)
                repeat(10) { h.tracker.onTick(playing()) }
                h.tracker.onPause(playing())
                scheduler.runCurrent()
                val report = h.repo.reports.last()
                withClue(row.name) {
                    report.utcOffsetMinutes shouldBe row.offset
                    report.timeZone shouldBe row.id
                }
            }
            h.repo.reports.size shouldBe 4
        }
    }
})
