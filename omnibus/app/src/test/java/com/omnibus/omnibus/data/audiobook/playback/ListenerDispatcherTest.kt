package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A listener that throws from every method. */
private class ThrowingListener : PlaybackListener {
    override fun onSettled(state: PlaybackState, startSeconds: Double) = boom()

    override fun onTick(state: PlaybackState) = boom()

    override fun onPlay(state: PlaybackState) = boom()

    override fun onPause(state: PlaybackState) = boom()

    override fun onAppBackground(state: PlaybackState) = boom()

    override fun onEnded(state: PlaybackState) = boom()

    override suspend fun onUnload(state: PlaybackState, reason: UnloadReason) = boom()

    private fun boom(): Nothing = throw IllegalStateException("listener failed")
}

/** A listener whose `onUnload` never finishes. */
private class StuckListener : PlaybackListener {
    override suspend fun onUnload(state: PlaybackState, reason: UnloadReason) = awaitCancellation()
}

class ListenerDispatcherTest : DescribeSpec({

    fun sut(listeners: Set<PlaybackListener> = emptySet()) = ListenerDispatcher(listeners) {}

    describe("telling every listener") {
        context("when a listener throws") {
            // TC-65
            it("still reaches the listeners after it (C4)") {
                val recorder = CallRecorder()
                val dispatcher = sut(setOf(ThrowingListener(), recorder))
                val state = randomPlaybackState()

                dispatcher.settled(state, 0.0)
                dispatcher.tick(state)
                dispatcher.play(state)
                dispatcher.pause(state)
                dispatcher.appBackground(state)
                dispatcher.ended(state)
                dispatcher.unload(state, UnloadReason.Stop)

                recorder.calls shouldBe
                    listOf("settled:0.0", "tick", "play", "pause", "background", "ended", "unload:Stop", "unloaded:Stop")
            }
        }
    }

    describe("unloading") {
        context("when a listener never finishes") {
            // TC-66
            it("still starts the listeners after it (C4)") {
                runTest {
                    val recorder = CallRecorder()
                    val dispatcher = sut(linkedSetOf(StuckListener(), recorder))

                    launch { dispatcher.unload(randomPlaybackState(), UnloadReason.Stop) }
                    runCurrent()

                    recorder.calls shouldBe listOf("unload:Stop", "unloaded:Stop")
                }
            }

            // TC-66
            it("gives up after 2 seconds (C4)") {
                runTest {
                    val dispatcher = sut(setOf(StuckListener()))
                    var returned = false

                    launch {
                        dispatcher.unload(randomPlaybackState(), UnloadReason.Stop)
                        returned = true
                    }
                    advanceTimeBy(1_999)
                    runCurrent()
                    returned shouldBe false

                    advanceTimeBy(1)
                    runCurrent()
                    returned shouldBe true
                }
            }
        }

        context("when every listener finishes") {
            // TC-66
            it("returns without waiting (C4)") {
                runTest {
                    val dispatcher = sut(setOf(CallRecorder()))

                    dispatcher.unload(randomPlaybackState(), UnloadReason.SwitchBook)

                    currentTime shouldBe 0L
                }
            }
        }
    }
})
