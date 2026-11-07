package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.audiobook.AudiobookManifestError
import com.omnibus.omnibus.data.audiobook.AudiobookManifestResult
import com.omnibus.omnibus.data.library.ResumePoint
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

private fun ControllerRig.pressEveryTransportCommand() {
    controller.skipBack()
    controller.skipForward()
    controller.previousChapter()
    controller.nextChapter()
    controller.seekTo(100.0)
    settle()
}

/** Commands: "play with nothing loaded", same-book loads, switching and failed items, pause, stop, and transport. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAudiobookControllerCommandsTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    fun sut(
        resolver: ResumeStartResolver? = null,
        sleepTimer: SleepTimer? = null,
        listeners: Set<PlaybackListener> = emptySet(),
    ) = ControllerRig(TestCoroutineScheduler(), resolver = resolver, sleepTimer = sleepTimer, listeners = listeners)

    describe("playing") {
        context("with nothing loaded") {
            context("when the most recent listening point can open") {
                // TC-49
                it("plays that book from its saved position (I-SYS-5)") {
                    val bookUuid = randomString()
                    val fileId = randomLong(1L..1_000L)
                    val position = randomDouble(1.0, 3_000.0)
                    val rig = sut()
                    rig.availability.answer = true
                    rig.recent.points = listOf(resumePoint(bookUuid, fileId, position))

                    rig.stageBook()
                    rig.controller.play()
                    rig.settle()

                    rig.repository.requests shouldBe listOf(bookUuid to fileId)
                    rig.controller.state.value.book?.bookUuid shouldBe bookUuid
                    rig.controller.state.value.positionSeconds shouldBe position
                    rig.controller.state.value.isPlaying shouldBe true
                }

                context("when a resolver is bound") {
                    // TC-49
                    it("leaves the position to the resolver (I-SYS-5)") {
                        val bookUuid = randomString()
                        val fileId = randomLong(1L..1_000L)
                        val resolver = ScriptedResolver()
                        val rig = sut(resolver = resolver)
                        rig.availability.answer = true
                        rig.recent.points = listOf(resumePoint(bookUuid, fileId, randomDouble(1.0, 3_000.0)))

                        rig.stageBook()
                        rig.controller.play()
                        rig.settle()

                        resolver.begins shouldBe listOf(Triple(bookUuid, fileId, null))
                    }
                }

                context("when paused before the read answers") {
                    // TC-49
                    it("stays idle (I-SYS-5)") {
                        val rig = sut()
                        val held = CompletableDeferred<List<ResumePoint>>()
                        rig.recent.pending = held
                        rig.stageBook()
                        rig.controller.play()
                        rig.settle()

                        rig.controller.pause()
                        rig.settle()
                        held.complete(listOf(resumePoint(randomString(), randomLong(), 120.0)))
                        rig.settle()

                        rig.controller.state.value shouldBe PlaybackState()
                    }
                }
            }

            context("when the most recent listening point cannot open") {
                // TC-49
                it("stays idle (I-SYS-5)") {
                    val rig = sut()
                    rig.availability.answer = false
                    rig.recent.points = listOf(resumePoint(randomString(), randomLong(), 120.0))

                    rig.stageBook()
                    rig.controller.play()
                    rig.settle()

                    rig.controller.state.value shouldBe PlaybackState()
                }
            }
        }

        context("after a failed load") {
            // TC-58
            it("runs the same load again, playing (C2)") {
                val rig = sut()
                rig.repository.result = AudiobookManifestResult.Failure(AudiobookManifestError.Unreachable)
                rig.loadAndSettle(bookFileId = 33L, positionSeconds = 120.0, autoplay = false)

                rig.repository.result = AudiobookManifestResult.Success(multiPartManifest(bookFileId = 33L))
                rig.controller.play()
                rig.settle()

                rig.repository.requests shouldBe listOf("book-1" to 33L, "book-1" to 33L)
                rig.controller.state.value.error shouldBe null
                rig.controller.state.value.isPlaying shouldBe true
                rig.controller.state.value.positionSeconds shouldBe 120.0
            }
        }
    }

    describe("loading") {
        context("when the book is already loaded") {
            context("when it is playing") {
                // TC-53
                it("keeps playing without reloading (C2)") {
                    val rig = sut()
                    rig.loadAndSettle()

                    rig.controller.load("book-1", null, null, true)
                    rig.settle()

                    rig.engine.sources.size shouldBe 1
                    rig.controller.state.value.isPlaying shouldBe true
                }
            }

            context("when it is paused") {
                // TC-53
                it("plays (C2)") {
                    val rig = sut()
                    rig.loadAndSettle()
                    rig.controller.pause()
                    rig.settle()

                    rig.controller.load("book-1", null, null, true)
                    rig.settle()

                    rig.controller.state.value.isPlaying shouldBe true
                }
            }

            context("when a position is given") {
                // TC-54
                it("seeks there without reloading (C2)") {
                    val rig = sut()
                    rig.loadAndSettle()

                    rig.controller.load("book-1", null, 300.0, true)
                    rig.settle()

                    rig.engine.sources.size shouldBe 1
                    rig.controller.state.value.positionSeconds shouldBe 300.0
                }
            }

            context("when a different file is asked for") {
                // TC-54
                it("reloads with that file (C2)") {
                    val rig = sut()
                    rig.loadAndSettle()

                    rig.controller.load("book-1", 33L, null, true)
                    rig.settle()

                    rig.repository.requests.last() shouldBe ("book-1" to 33L)
                    rig.engine.sources.size shouldBe 2
                }
            }

            context("when it has failed") {
                // TC-55
                it("rebuilds it (C2)") {
                    val rig = sut()
                    rig.loadAndSettle()
                    repeat(4) { rig.engine.fail(FailureKind.Stream) }

                    rig.controller.load("book-1", null, null, true)
                    rig.settle()

                    rig.engine.sources.size shouldBe 2
                    rig.controller.state.value.error shouldBe null
                    rig.controller.state.value.isPlaying shouldBe true
                }
            }
        }
    }

    describe("switching books") {
        // TC-56
        it("holds the new book's read until the old one has unloaded (I-SLP-3)") {
            val gate = CompletableDeferred<Unit>()
            val listener = CallRecorder(unloadGate = gate)
            val rig = sut(listeners = setOf(listener))
            rig.loadAndSettle()

            rig.controller.load("book-2", null, null, true)
            rig.settle()
            listener.calls.contains("unload:SwitchBook") shouldBe true
            rig.repository.requests.size shouldBe 1

            gate.complete(Unit)
            rig.settle()
            rig.repository.requests.last().first shouldBe "book-2"
        }
    }

    describe("pausing") {
        context("during a load") {
            // TC-57
            it("cancels the auto-start (I-HLTH-4)") {
                val rig = sut()
                val held = CompletableDeferred<AudiobookManifestResult>()
                rig.repository.pending = held
                rig.loadAndSettle()

                rig.controller.pause()
                rig.settle()
                held.complete(AudiobookManifestResult.Success(multiPartManifest()))
                rig.settle()

                rig.engine.calls.contains("play") shouldBe false
                rig.controller.state.value.isLoading shouldBe false
                rig.controller.state.value.isPlaying shouldBe false
            }
        }

        context("during a rebuild wait") {
            // TC-57: an owed resume counts as playing, so the toggle pauses. The rebuild still happens, paused.
            it("rebuilds without resuming (I-HLTH-4)") {
                val rig = sut()
                rig.loadAndSettle()
                rig.engine.fail(FailureKind.Stream)
                rig.settle()

                rig.controller.togglePlayPause()
                rig.settle()
                val mark = rig.engine.calls.size
                rig.advance(1_000)

                rig.engine.calls.drop(mark).contains("setSource") shouldBe true
                rig.engine.calls.drop(mark).contains("play") shouldBe false
                rig.controller.state.value.isPlaying shouldBe false
            }
        }
    }

    describe("stopping") {
        context("when a book is loaded") {
            // TC-59
            it("returns to the idle state (C2)") {
                val rig = sut()
                rig.loadAndSettle()

                rig.controller.stop()
                rig.settle()

                rig.controller.state.value shouldBe PlaybackState()
            }

            // TC-59
            it("holds the engine's stop until listeners have unloaded (C2)") {
                val gate = CompletableDeferred<Unit>()
                val rig = sut(listeners = setOf(CallRecorder(unloadGate = gate)))
                rig.loadAndSettle()

                rig.controller.stop()
                rig.settle()
                rig.engine.calls.contains("stop") shouldBe false

                gate.complete(Unit)
                rig.settle()
                rig.engine.calls.contains("stop") shouldBe true
            }
        }

        context("when a load is in flight") {
            // TC-59
            it("abandons it for good (C2)") {
                val rig = sut()
                val held = CompletableDeferred<AudiobookManifestResult>()
                rig.repository.pending = held
                rig.loadAndSettle()

                rig.controller.stop()
                rig.settle()
                held.complete(AudiobookManifestResult.Success(multiPartManifest()))
                rig.settle()

                rig.controller.state.value shouldBe PlaybackState()
                rig.engine.sources.shouldBeEmpty()
            }
        }
    }

    describe("sending commands") {
        context("before the player connects") {
            // TC-60
            it("wait, then apply in call order (C2)") {
                val rig = sut()
                rig.loadAndSettle()
                rig.engine.ready = CompletableDeferred()
                rig.engine.emit(EngineEvent.Disconnected)
                val mark = rig.engine.calls.size

                rig.controller.seekTo(50.0)
                rig.controller.setRate(2.0, persist = false)
                rig.controller.pause()
                rig.settle()
                rig.engine.calls.drop(mark) shouldBe listOf("awaitReady")

                rig.engine.ready!!.complete(Unit)
                rig.settle()
                rig.engine.calls.drop(mark).shouldBeInOrder("seekTo(50.0)", "setSpeed(2.0)", "pause")
            }

            context("when a rate is set during the first load") {
                // TC-60
                it("applies after the load's reset (C2)") {
                    val rig = sut()
                    rig.engine.ready = CompletableDeferred()
                    rig.stageBook()
                    rig.controller.load("book-1", null, null, autoplay = false)

                    rig.controller.setRate(2.0, persist = false)
                    rig.engine.ready!!.complete(Unit)
                    rig.settle()

                    rig.engine.calls.shouldBeInOrder("setSpeed(1.0)", "setSpeed(2.0)")
                    rig.controller.state.value.rate shouldBe 2.0
                }
            }
        }

        context("while the engine throws") {
            // TC-61
            it("never throw, so stop still reaches the idle state (C2)") {
                val rig = sut()
                rig.loadAndSettle()
                rig.engine.commandFailure = IllegalStateException("player gone")

                rig.controller.pause()
                rig.controller.play()
                rig.controller.togglePlayPause()
                rig.controller.seekTo(10.0)
                rig.controller.skipBack()
                rig.controller.skipForward()
                rig.controller.setRate(2.0, persist = false)
                rig.controller.stop()
                rig.settle()

                rig.controller.state.value shouldBe PlaybackState()
            }
        }
    }

    describe("moving through the book") {
        // TC-63
        it("skips back 15 seconds (P-PLAY-3)") {
            val rig = sut()
            rig.loadAndSettle()
            rig.controller.seekTo(1_000.0)

            rig.controller.skipBack()

            rig.controller.state.value.positionSeconds shouldBe 985.0
        }

        // TC-63
        it("skips forward 30 seconds (P-PLAY-3)") {
            val rig = sut()
            rig.loadAndSettle()
            rig.controller.seekTo(1_000.0)

            rig.controller.skipForward()

            rig.controller.state.value.positionSeconds shouldBe 1_030.0
        }

        // TC-63
        it("restarts the chapter on previous (P-PLAY-4)") {
            val rig = sut()
            rig.loadAndSettle()
            rig.controller.seekTo(800.0)

            rig.controller.previousChapter()

            rig.controller.state.value.positionSeconds shouldBe 700.0
        }

        // TC-63
        it("goes to the next chapter's start on next (P-PLAY-5)") {
            val rig = sut()
            rig.loadAndSettle()
            rig.controller.seekTo(800.0)

            rig.controller.nextChapter()

            rig.controller.state.value.positionSeconds shouldBe 1_900.0
        }

        // TC-63
        it("keeps a seek within the book (I-TL-6)") {
            val rig = sut()
            rig.loadAndSettle()

            rig.controller.seekTo(5_000.0)
            rig.controller.state.value.positionSeconds shouldBe 3_000.0

            rig.controller.seekTo(-5.0)
            rig.controller.state.value.positionSeconds shouldBe 0.0
        }

        context("when the book has no chapters") {
            // TC-63
            it("ignores the chapter commands (P-PLAY-4, P-PLAY-5)") {
                val rig = sut()
                rig.repository.result = AudiobookManifestResult.Success(multiPartManifest().copy(chapters = emptyList()))
                rig.loadAndSettle()
                val mark = rig.engine.calls.size

                rig.controller.previousChapter()
                rig.controller.nextChapter()

                rig.engine.calls.drop(mark).shouldBeEmpty()
            }
        }

        context("when the engine has not answered yet") {
            // TC-63
            it("moves the state at once (C2)") {
                val rig = sut()
                rig.loadAndSettle()
                rig.engine.emit(EngineEvent.Disconnected)

                rig.controller.seekTo(1_300.0)

                rig.controller.state.value.positionSeconds shouldBe 1_300.0
                rig.controller.state.value.currentChapterIndex shouldBe 1
            }
        }

        context("when a load is running") {
            // TC-63
            it("ignores every transport command (C2)") {
                val rig = sut()
                rig.repository.pending = CompletableDeferred()
                rig.loadAndSettle(autoplay = false)

                rig.pressEveryTransportCommand()

                rig.engine.calls.none { it.startsWith("seekTo") } shouldBe true
                rig.controller.state.value.positionSeconds shouldBe 0.0
            }
        }
    }
})
