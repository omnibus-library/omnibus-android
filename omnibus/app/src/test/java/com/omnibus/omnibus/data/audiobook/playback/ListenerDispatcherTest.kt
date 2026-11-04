package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A listener that records every call it gets, in order. */
private class RecordingListener : PlaybackListener {
    val calls = mutableListOf<String>()

    override fun onSettled(state: PlaybackState, startSeconds: Double) {
        calls += "settled"
    }

    override fun onTick(state: PlaybackState) {
        calls += "tick"
    }

    override fun onPlay(state: PlaybackState) {
        calls += "play"
    }

    override fun onPause(state: PlaybackState) {
        calls += "pause"
    }

    override fun onAppBackground(state: PlaybackState) {
        calls += "background"
    }

    override fun onEnded(state: PlaybackState) {
        calls += "ended"
    }

    override suspend fun onUnload(state: PlaybackState, reason: UnloadReason) {
        calls += "unload"
    }
}

/** A listener that throws from every method. */
private class ThrowingListener : PlaybackListener {
    override fun onSettled(state: PlaybackState, startSeconds: Double) {
        boom()
    }

    override fun onTick(state: PlaybackState) {
        boom()
    }

    override fun onPlay(state: PlaybackState) {
        boom()
    }

    override fun onPause(state: PlaybackState) {
        boom()
    }

    override fun onAppBackground(state: PlaybackState) {
        boom()
    }

    override fun onEnded(state: PlaybackState) {
        boom()
    }

    override suspend fun onUnload(state: PlaybackState, reason: UnloadReason) {
        boom()
    }

    private fun boom(): Nothing = throw IllegalStateException("listener failed")
}

class ListenerDispatcherTest : DescribeSpec({

    describe("error isolation") {

        // TC-65
        it("one listener's exception never stops the others (C4)") {
            val errors = mutableListOf<Throwable>()
            val first = RecordingListener()
            val second = RecordingListener()
            // The throwing listener comes first in the set's iteration order, so the others must still be reached.
            val dispatcher = ListenerDispatcher(setOf(ThrowingListener(), first, second)) { errors += it }
            val state = PlaybackState()

            dispatcher.settled(state, 0.0)
            dispatcher.tick(state)
            dispatcher.play(state)
            dispatcher.pause(state)
            dispatcher.appBackground(state)
            dispatcher.ended(state)
            runTest { dispatcher.unload(state, UnloadReason.Stop) }

            val expected = listOf("settled", "tick", "play", "pause", "background", "ended", "unload")
            first.calls shouldBe expected
            second.calls shouldBe expected
            // One error for each of the seven calls the throwing listener got.
            errors.size shouldBe 7
        }
    }

    describe("onUnload") {

        // TC-66
        it("runs in parallel and waits at most 2 s (C4)") {
            runTest {
                val slow = object : PlaybackListener {
                    override suspend fun onUnload(state: PlaybackState, reason: UnloadReason) = awaitCancellation()
                }
                val fast = RecordingListener()
                val dispatcher = ListenerDispatcher(setOf(slow, fast, ThrowingListener())) {}
                var returned = false

                val job = launch {
                    dispatcher.unload(PlaybackState(), UnloadReason.Stop)
                    returned = true
                }

                runCurrent()
                // The fast listener started at once, so all of them started in parallel.
                fast.calls shouldBe listOf("unload")
                advanceTimeBy(1_999)
                runCurrent()
                returned shouldBe false

                advanceTimeBy(1)
                runCurrent()
                returned shouldBe true
                job.join()
            }
        }

        // TC-66
        it("returns at once when all finish (C4)") {
            runTest {
                val fast = RecordingListener()
                val dispatcher = ListenerDispatcher(setOf(fast)) {}
                var returned = false

                val job = launch {
                    dispatcher.unload(PlaybackState(), UnloadReason.SwitchBook)
                    returned = true
                }

                runCurrent()
                returned shouldBe true
                currentTime shouldBe 0L
                job.join()
            }
        }
    }
})
