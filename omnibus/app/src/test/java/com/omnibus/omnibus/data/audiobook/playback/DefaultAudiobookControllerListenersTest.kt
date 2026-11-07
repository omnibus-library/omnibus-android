package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.audiobook.AudiobookManifestError
import com.omnibus.omnibus.data.audiobook.AudiobookManifestResult
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

/** Listener dispatch: the end, settle, ticks, play and pause, and background. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAudiobookControllerListenersTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    fun sut(
        resolver: ResumeStartResolver? = null,
        listeners: Set<PlaybackListener> = emptySet(),
    ) = ControllerRig(TestCoroutineScheduler(), resolver = resolver, listeners = listeners)

    describe("reporting the end of the book") {
        // TC-64
        it("reports once, stopped, with the position at the duration (I-SAVE-2)") {
            val listener = CallRecorder()
            val rig = sut(listeners = setOf(listener))
            rig.loadAndSettle()

            rig.engine.emit(EngineEvent.Ended)
            rig.settle()

            listener.ended.single().positionSeconds shouldBe 3_000.0
            listener.ended.single().isPlaying shouldBe false
        }
    }

    describe("reporting that a load has settled") {
        context("when the load auto-starts") {
            // TC-67
            it("reports the start position before the first play (I-RES-5)") {
                val listener = CallRecorder()
                val rig = sut(resolver = ScriptedResolver(settleAnswer = answering(90.0)), listeners = setOf(listener))

                rig.loadAndSettle()

                listener.calls shouldBe listOf("settled:90.0", "play")
            }
        }

        context("when the load is paused") {
            // TC-67
            it("reports without playing (I-RES-5)") {
                val listener = CallRecorder()
                val rig = sut(listeners = setOf(listener))

                rig.loadAndSettle(autoplay = false)

                listener.calls shouldBe listOf("settled:0.0")
            }
        }

        context("when the load fails") {
            // TC-67
            it("reports nothing (I-RES-5)") {
                val listener = CallRecorder()
                val rig = sut(listeners = setOf(listener))
                rig.repository.result = AudiobookManifestResult.Failure(AudiobookManifestError.Unreachable)

                rig.loadAndSettle()

                listener.count("settled") shouldBe 0
            }
        }
    }

    describe("ticking") {
        context("when playing") {
            // TC-68
            it("ticks every half second (I-SAVE-2)") {
                val listener = CallRecorder()
                val rig = sut(listeners = setOf(listener))
                rig.loadAndSettle()

                rig.advance(2_000)

                listener.count("tick") shouldBe 4
            }

            // TC-68
            it("reports the engine's position with the chapter at it (I-SAVE-2)") {
                val listener = CallRecorder()
                val rig = sut(listeners = setOf(listener))
                rig.loadAndSettle()

                rig.engine.snapshot = rig.engine.snapshot.copy(positionSeconds = 1_000.0)
                rig.advance(500)

                listener.ticks.last().positionSeconds shouldBe 1_000.0
                listener.ticks.last().currentChapterIndex shouldBe 1
            }
        }

        context("when paused") {
            // TC-68
            it("does not tick (I-SAVE-2)") {
                val listener = CallRecorder()
                val rig = sut(listeners = setOf(listener))
                rig.loadAndSettle()
                rig.controller.pause()
                rig.settle()

                rig.advance(2_000)

                listener.count("tick") shouldBe 0
            }
        }

        context("when stopped") {
            // TC-68
            it("does not tick") {
                val listener = CallRecorder()
                val rig = sut(listeners = setOf(listener))
                rig.loadAndSettle()
                rig.controller.stop()
                rig.settle()

                rig.advance(2_000)

                listener.count("tick") shouldBe 0
            }
        }
    }

    describe("reporting play and pause") {
        // TC-69
        it("reports play each time playback starts (R10)") {
            val listener = CallRecorder()
            val rig = sut(listeners = setOf(listener))
            rig.loadAndSettle()
            rig.controller.pause()
            rig.settle()

            rig.controller.play()
            rig.settle()

            listener.count("play") shouldBe 2
        }

        context("when the user pauses") {
            // TC-69
            it("reports pause (R10)") {
                val listener = CallRecorder()
                val rig = sut(listeners = setOf(listener))
                rig.loadAndSettle()

                rig.controller.pause()
                rig.settle()

                listener.count("pause") shouldBe 1
            }
        }

        context("when something outside the app pauses") {
            // TC-69
            it("reports pause (R10)") {
                val listener = CallRecorder()
                val rig = sut(listeners = setOf(listener))
                rig.loadAndSettle()

                rig.engine.emit(EngineEvent.PausedExternally)
                rig.engine.push(rig.engine.snapshot().copy(isPlaying = false, isAudible = false))
                rig.settle()

                listener.count("pause") shouldBe 1
            }
        }

        context("when the book ends") {
            // TC-69
            it("reports no pause (R10)") {
                val listener = CallRecorder()
                val rig = sut(listeners = setOf(listener))
                rig.loadAndSettle()

                rig.engine.emit(EngineEvent.Ended)
                rig.engine.push(rig.engine.snapshot().copy(isPlaying = false, isAudible = false))
                rig.settle()

                listener.count("pause") shouldBe 0
            }
        }

        context("when stopped") {
            // TC-69
            it("reports no pause (R10)") {
                val listener = CallRecorder()
                val rig = sut(listeners = setOf(listener))
                rig.loadAndSettle()

                rig.controller.stop()
                rig.settle()

                listener.count("pause") shouldBe 0
            }
        }
    }

    describe("reporting that the app went to the background") {
        context("when a book is loaded") {
            // TC-70
            it("reports the loaded book (R10)") {
                val listener = CallRecorder()
                val rig = sut(listeners = setOf(listener))
                rig.loadAndSettle()

                rig.background.emit()
                rig.settle()

                listener.backgrounds.single().book?.bookUuid shouldBe "book-1"
            }
        }

        context("when nothing is loaded") {
            // TC-70
            it("reports nothing (R10)") {
                val listener = CallRecorder()
                val rig = sut(listeners = setOf(listener))

                rig.background.emit()
                rig.settle()

                listener.count("background") shouldBe 0
            }
        }
    }
})
