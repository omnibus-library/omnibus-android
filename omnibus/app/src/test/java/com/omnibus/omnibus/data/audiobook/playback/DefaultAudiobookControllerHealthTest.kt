package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.audiobook.AudiobookManifestResult
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

private fun ControllerRig.callsAfter(block: () -> Unit): List<String> {
    val mark = engine.calls.size
    block()
    return engine.calls.drop(mark)
}

/** Sends a stream failure, then checks that the rebuild comes after [waitMillis], at 120 s, and resumes. */
private fun ControllerRig.failAndExpectRebuildAfter(waitMillis: Long) {
    engine.emit(EngineEvent.Failed(FailureKind.Stream))
    settle()
    callsAfter { advance(waitMillis - 1) }.contains("setSource") shouldBe false
    callsAfter { advance(1) }.shouldBeInOrder("setSource", "seekTo(120.0)", "play")
}

/** Health: the rebuild budget, buffering, load failure mapping and the rebuild's quiet transitions. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAudiobookControllerHealthTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    fun sut(
        resolver: ResumeStartResolver? = null,
        listeners: Set<PlaybackListener> = emptySet(),
    ) = ControllerRig(TestCoroutineScheduler(), resolver = resolver, listeners = listeners)

    describe("rebuilding the stream") {
        context("after a stream failure") {
            context("when playing") {
                // TC-37: the rebuilt item does not report playing (reflect is off), so the failures stay consecutive.
                it("rebuilds at the position, waiting a second longer each time (I-HLTH-2)") {
                    val rig = sut()
                    rig.loadAndSettle()
                    rig.controller.seekTo(120.0)
                    rig.engine.reflect = false

                    rig.failAndExpectRebuildAfter(1_000)
                    rig.failAndExpectRebuildAfter(2_000)
                    rig.failAndExpectRebuildAfter(3_000)
                }

                // TC-42
                it("reports no pause or second play (R10)") {
                    val listener = CallRecorder()
                    val rig = sut(listeners = setOf(listener))
                    rig.loadAndSettle()

                    rig.engine.fail(FailureKind.Stream)
                    rig.settle()
                    rig.advance(1_000)

                    listener.count("pause") shouldBe 0
                    listener.count("play") shouldBe 1
                }
            }

            context("when paused") {
                // TC-38
                it("rebuilds without resuming (I-HLTH-2)") {
                    val rig = sut()
                    rig.loadAndSettle(autoplay = false)
                    rig.engine.emit(EngineEvent.Failed(FailureKind.Stream))
                    rig.settle()

                    val calls = rig.callsAfter { rig.advance(1_000) }

                    calls.contains("setSource") shouldBe true
                    calls.contains("play") shouldBe false
                }
            }

            context("when a load still owes a start") {
                // TC-38
                it("resumes with the rebuild (I-HLTH-2)") {
                    val rig = sut(resolver = ScriptedResolver(settleAnswer = CompletableDeferred()))
                    rig.loadAndSettle()
                    rig.engine.emit(EngineEvent.Failed(FailureKind.Stream))
                    rig.settle()

                    rig.callsAfter { rig.advance(1_000) }.shouldBeInOrder("setSource", "play")
                }
            }
        }
    }

    describe("giving up") {
        context("when the fourth failure in a row arrives") {
            // TC-39
            it("gives up with StoppedAfterRetries (I-HLTH-3)") {
                val rig = sut()
                rig.loadAndSettle()

                repeat(4) { rig.engine.fail(FailureKind.Stream) }
                rig.settle()

                rig.controller.state.value.error shouldBe PlaybackError.StoppedAfterRetries
                rig.controller.state.value.isPlaying shouldBe false
                rig.callsAfter { rig.advance(10_000) }.contains("setSource") shouldBe false
            }

            context("when play is pressed") {
                // TC-39
                it("rebuilds, playing, with the error cleared (I-HLTH-3)") {
                    val rig = sut()
                    rig.loadAndSettle()
                    repeat(4) { rig.engine.fail(FailureKind.Stream) }
                    rig.settle()

                    rig.controller.play()
                    rig.settle()
                    rig.callsAfter { rig.advance(1_000) }.shouldBeInOrder("setSource", "play")

                    rig.controller.state.value.error shouldBe null
                    rig.controller.state.value.isPlaying shouldBe true
                }
            }
        }

        context("when real playback comes between failures") {
            // TC-39
            it("starts the count again (I-HLTH-2)") {
                val rig = sut()
                rig.loadAndSettle()
                rig.engine.fail(FailureKind.Stream)
                rig.engine.fail(FailureKind.Stream)

                rig.engine.push(EngineSnapshot(hasMedia = true, isPlaying = true, isAudible = true))
                repeat(3) { rig.engine.fail(FailureKind.Stream) }
                rig.settle()

                rig.controller.state.value.error shouldBe null
            }
        }
    }

    describe("buffering") {
        context("when playing") {
            // TC-40
            it("shows as buffering (I-HLTH-1)") {
                val rig = sut()
                rig.loadAndSettle()

                rig.engine.push(EngineSnapshot(hasMedia = true, isPlaying = true, isBuffering = true))

                rig.controller.state.value.isBuffering shouldBe true
            }

            // TC-40
            it("never rebuilds (I-HLTH-1)") {
                val rig = sut()
                rig.loadAndSettle()
                rig.engine.push(EngineSnapshot(hasMedia = true, isPlaying = true, isBuffering = true))

                rig.callsAfter { rig.advance(10_000) }.contains("setSource") shouldBe false
            }
        }

        context("when paused") {
            // TC-40
            it("does not show as buffering (I-HLTH-1)") {
                val rig = sut()
                rig.loadAndSettle(autoplay = false)

                rig.engine.push(EngineSnapshot(hasMedia = true, isPlaying = false, isBuffering = true))

                rig.controller.state.value.isBuffering shouldBe false
            }
        }
    }

    describe("failing a load") {
        context("when a part has an unusable duration") {
            // TC-41
            it("reports TimelineFailed (P-PLAY-10)") {
                val rig = sut()
                rig.repository.result = AudiobookManifestResult.Success(manifestWithFirstPart(0.0))

                rig.loadAndSettle()

                rig.controller.state.value.error shouldBe PlaybackError.TimelineFailed
                rig.controller.state.value.isLoading shouldBe false
            }
        }

        context("when the media source cannot be built") {
            // TC-41
            it("reports TimelineFailed (P-PLAY-10)") {
                val rig = sut()
                rig.engine.setSourceFailure = IllegalStateException("no source")

                rig.loadAndSettle()

                rig.controller.state.value.error shouldBe PlaybackError.TimelineFailed
                rig.controller.state.value.isLoading shouldBe false
            }
        }

        context("when the item has no audio track") {
            // TC-41
            it("reports PartUnreadable (I-LOAD-4)") {
                val rig = sut()
                rig.loadAndSettle()

                rig.engine.emit(EngineEvent.NoAudioTrack)
                rig.settle()

                rig.controller.state.value.error shouldBe PlaybackError.PartUnreadable
                rig.controller.state.value.isLoading shouldBe false
            }

            // TC-41
            it("never rebuilds (I-LOAD-4)") {
                val rig = sut()
                rig.loadAndSettle()
                rig.engine.emit(EngineEvent.NoAudioTrack)
                rig.settle()

                rig.callsAfter { rig.advance(10_000) }.contains("setSource") shouldBe false
            }
        }

        context("when an unreadable error arrives before the first ready state") {
            // TC-41
            it("reports PartUnreadable (P-PLAY-10)") {
                val rig = sut()
                rig.loadAndSettle(autoplay = false)

                rig.engine.emit(EngineEvent.Failed(FailureKind.Unreadable))
                rig.settle()

                rig.controller.state.value.error shouldBe PlaybackError.PartUnreadable
            }
        }

        context("when an unreadable error arrives after the first ready state") {
            // TC-41
            it("rebuilds with no error shown (P-PLAY-10)") {
                val rig = sut()
                rig.loadAndSettle(autoplay = false)
                rig.engine.emit(EngineEvent.Ready)

                rig.engine.emit(EngineEvent.Failed(FailureKind.Unreadable))
                rig.settle()

                rig.controller.state.value.error shouldBe null
                rig.callsAfter { rig.advance(1_000) }.contains("setSource") shouldBe true
            }
        }
    }
})
