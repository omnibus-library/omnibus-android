package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.audiobook.AudiobookManifestError
import com.omnibus.omnibus.data.audiobook.AudiobookManifestResult
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

/** The optional seams: resume start, rate store and sleep timer, plus the controller with no resolver (C5). */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAudiobookControllerSeamsTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    fun sut(
        resolver: ResumeStartResolver? = null,
        rateStore: PlaybackRateStore? = null,
        sleepTimer: SleepTimer? = null,
    ) = ControllerRig(TestCoroutineScheduler(), resolver = resolver, rateStore = rateStore, sleepTimer = sleepTimer)

    describe("asking the resolver where to start") {
        // TC-72
        it("begins with the book, file and position the caller gave (I-RES-2)") {
            val resolver = ScriptedResolver()
            val rig = sut(resolver = resolver)
            rig.repository.pending = CompletableDeferred()

            rig.stageBook()
            rig.controller.load("book-1", 33L, 120.0, true)
            rig.settle()

            resolver.begins shouldBe listOf(Triple("book-1", 33L, 120.0))
        }

        // TC-73
        it("settles with the manifest's file and duration (I-RES-3)") {
            val resolver = ScriptedResolver()
            val rig = sut(resolver = resolver)

            rig.loadAndSettle()

            resolver.pendings.single().settleCalls shouldBe listOf(11L to 3_000.0)
        }

        context("when the answer is positive") {
            // TC-73
            it("seeks there before starting (I-RES-3)") {
                val rig = sut(resolver = ScriptedResolver(settleAnswer = answering(300.0)))

                rig.loadAndSettle()

                rig.controller.state.value.positionSeconds shouldBe 300.0
                rig.engine.calls.shouldBeInOrder("seekTo(300.0)", "play")
            }

            context("when the load is paused") {
                // TC-73
                it("still seeks there (I-RES-5)") {
                    val rig = sut(resolver = ScriptedResolver(settleAnswer = answering(300.0)))

                    rig.loadAndSettle(autoplay = false)

                    rig.controller.state.value.positionSeconds shouldBe 300.0
                }
            }
        }

        // TC-73
        it("starts at 0 with no seek for a null, zero or negative answer (I-RES-3)") {
            checkAll(Exhaustive.of<Double?>(null, 0.0, -5.0)) { answer ->
                val rig = sut(resolver = ScriptedResolver(settleAnswer = answering(answer)))

                rig.loadAndSettle()

                rig.controller.state.value.positionSeconds shouldBe 0.0
                rig.engine.calls.none { it.startsWith("seekTo") } shouldBe true
            }
        }

        context("when the book is hls") {
            // TC-74
            it("settles once the player is ready, with the duration it reports (I-RES-3)") {
                val resolver = ScriptedResolver(settleAnswer = answering(300.0))
                val rig = sut(resolver = resolver)
                rig.repository.result = AudiobookManifestResult.Success(hlsManifest())

                rig.loadAndSettle()
                resolver.pendings.single().settleCalls.shouldBeEmpty()

                rig.engine.snapshot = rig.engine.snapshot.copy(durationSeconds = 1_800.0)
                rig.engine.emit(EngineEvent.Ready)
                rig.settle()

                resolver.pendings.single().settleCalls shouldBe listOf(11L to 1_800.0)
                rig.controller.state.value.durationSeconds shouldBe 1_800.0
            }
        }
    }

    describe("cancelling the resolver's answer") {
        context("when paused during the load") {
            // TC-75
            it("does not cancel (C5)") {
                val resolver = ScriptedResolver()
                val rig = sut(resolver = resolver)
                val held = CompletableDeferred<AudiobookManifestResult>()
                rig.repository.pending = held
                rig.loadAndSettle()

                rig.controller.pause()
                rig.settle()
                held.complete(AudiobookManifestResult.Success(multiPartManifest()))
                rig.settle()

                resolver.pendings.single().cancelCount shouldBe 0
            }
        }

        context("when stopped during the load") {
            // TC-75
            it("cancels (C5)") {
                val resolver = ScriptedResolver()
                val rig = sut(resolver = resolver)
                rig.repository.pending = CompletableDeferred()
                rig.loadAndSettle()

                rig.controller.stop()
                rig.settle()

                resolver.pendings.single().cancelCount shouldBe 1
            }
        }

        context("when another load replaces it") {
            // TC-75
            it("cancels only the replaced load (C5)") {
                val resolver = ScriptedResolver()
                val rig = sut(resolver = resolver)
                rig.repository.pending = CompletableDeferred()
                rig.loadAndSettle()

                rig.repository.pending = null
                rig.controller.load("book-2", null, null, true)
                rig.settle()

                resolver.pendings.map { it.cancelCount } shouldBe listOf(1, 0)
            }
        }

        context("when the load fails") {
            // TC-75
            it("cancels (C5)") {
                val resolver = ScriptedResolver()
                val rig = sut(resolver = resolver)
                rig.repository.result = AudiobookManifestResult.Failure(AudiobookManifestError.Unreachable)

                rig.loadAndSettle()

                resolver.pendings.single().cancelCount shouldBe 1
            }
        }
    }

    describe("starting with no resolver") {
        context("when a position is given") {
            // TC-76
            it("starts there (C5)") {
                val rig = sut()

                rig.loadAndSettle(positionSeconds = 120.0)

                rig.controller.state.value.positionSeconds shouldBe 120.0
            }
        }
    }

    describe("restoring the saved rate") {
        context("when the read has not answered") {
            // TC-77
            it("starts playing anyway (C6)") {
                val store = TestPlaybackRateStore()
                store.answer = CompletableDeferred()
                val rig = sut(rateStore = store)

                rig.loadAndSettle()

                rig.controller.state.value.isPlaying shouldBe true
            }
        }

        // TC-77
        it("applies the answer, limited to the range, or 1.0 for a missing one (C6)") {
            checkAll(Exhaustive.of<Pair<Double?, Double>>(1.75 to 1.75, 9.0 to 3.0, null to 1.0)) { (answer, expected) ->
                val store = TestPlaybackRateStore()
                store.answer = answering(answer)
                val rig = sut(rateStore = store)

                rig.loadAndSettle()

                rig.controller.state.value.rate shouldBe expected
            }
        }

        context("when the rate is set before the read answers") {
            // TC-77
            it("keeps the rate that was set (C6)") {
                val store = TestPlaybackRateStore()
                val late = CompletableDeferred<Double?>()
                store.answer = late
                val rig = sut(rateStore = store)
                rig.loadAndSettle()

                rig.controller.setRate(2.0, persist = false)
                late.complete(1.75)
                rig.settle()

                rig.controller.state.value.rate shouldBe 2.0
            }
        }
    }

    describe("setting the rate") {
        // TC-78
        it("applies at once (I-SPD-1)") {
            val rig = sut()
            rig.loadAndSettle()

            rig.controller.setRate(2.0, persist = false)

            rig.engine.calls.last() shouldBe "setSpeed(2.0)"
            rig.controller.state.value.rate shouldBe 2.0
        }

        context("when asked to persist") {
            context("when the rate is out of range") {
                // TC-78
                it("saves the limited rate (I-SPD-1)") {
                    val store = TestPlaybackRateStore()
                    val rig = sut(rateStore = store)
                    rig.loadAndSettle()

                    rig.controller.setRate(9.0, persist = true)
                    rig.settle()

                    store.saves shouldBe listOf("book-1" to 3.0)
                }
            }
        }

        context("when not asked to persist") {
            // TC-78
            it("saves nothing (I-SPD-1)") {
                val store = TestPlaybackRateStore()
                val rig = sut(rateStore = store)
                rig.loadAndSettle()

                rig.controller.setRate(2.0, persist = false)
                rig.settle()

                store.saves.shouldBeEmpty()
            }
        }

        // TC-78
        it("changes nothing for a NaN or infinite rate (I-SPD-1)") {
            checkAll(Exhaustive.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) { rate ->
                val store = TestPlaybackRateStore()
                val rig = sut(rateStore = store)
                rig.loadAndSettle()
                val mark = rig.engine.calls.size

                rig.controller.setRate(rate, persist = true)
                rig.settle()

                rig.controller.state.value.rate shouldBe 1.0
                store.saves.shouldBeEmpty()
                rig.engine.calls.drop(mark).shouldBeEmpty()
            }
        }
    }

    describe("the sleep timer") {
        // TC-79
        it("shows the timer's state (C7)") {
            val timer = TestSleepTimer()
            val rig = sut(sleepTimer = timer)
            val running = randomSleepTimerState()

            timer.state.value = running
            rig.settle()

            rig.controller.state.value.sleepTimer shouldBe running
        }

        // TC-79
        it("passes a new setting to the timer (C7)") {
            val timer = TestSleepTimer()
            val option = randomSleepTimerOption()

            sut(sleepTimer = timer).controller.setSleepTimer(option)

            timer.sets shouldBe listOf(option)
        }

        context("when a book is loaded") {
            // TC-79
            it("is cleared by a stop (I-SLP-3)") {
                val timer = TestSleepTimer()
                val rig = sut(sleepTimer = timer)
                rig.loadAndSettle()

                rig.controller.stop()
                rig.settle()

                timer.sets shouldBe listOf(null)
            }

            // TC-79
            it("is cleared by switching books (I-SLP-3)") {
                val timer = TestSleepTimer()
                val rig = sut(sleepTimer = timer)
                rig.loadAndSettle()

                rig.controller.load("book-2", null, null, true)
                rig.settle()

                timer.sets shouldBe listOf(null)
            }

            // TC-79
            it("survives loading the same book (I-SLP-3)") {
                val timer = TestSleepTimer()
                val rig = sut(sleepTimer = timer)
                rig.loadAndSettle()

                rig.controller.load("book-1", null, null, true)
                rig.settle()

                timer.sets.shouldBeEmpty()
            }
        }
    }
})
