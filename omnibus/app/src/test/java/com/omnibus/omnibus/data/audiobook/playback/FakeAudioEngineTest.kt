package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Writes what the engine reports into [log], in the order it arrives. */
private class LogListener(private val log: MutableList<String>) : AudioEngine.Listener {
    override fun onEvent(event: EngineEvent) {
        log += "event:$event"
    }

    override fun onSnapshot(snapshot: EngineSnapshot) {
        log += "snapshot:${snapshot.isPlaying}"
    }
}

class FakeAudioEngineTest : DescribeSpec({

    describe("FakeAudioEngine") {

        // TC-87
        it("records calls and replays scripted events") {
            val engine = FakeAudioEngine()
            val log = mutableListOf<String>()
            engine.setListener(LogListener(log))

            engine.awaitReady()
            engine.setSource(AudioSource.Hls("book-1", "https://server.test/playlist.m3u8", emptyMap()))
            engine.snapshot() shouldBe EngineSnapshot(hasMedia = true)

            engine.play()
            log shouldBe listOf("snapshot:true")

            engine.seekTo(50.0)
            engine.snapshot().positionSeconds shouldBe 50.0
            // A seek changes the position without pushing a snapshot.
            log shouldBe listOf("snapshot:true")

            engine.setSpeed(2.0)
            engine.pause()
            engine.fail(FailureKind.Stream)

            log shouldBe listOf(
                "snapshot:true",
                "snapshot:false",
                "event:Failed(kind=Stream)",
                "snapshot:false",
            )
            engine.calls shouldBe listOf(
                "awaitReady",
                "setSource",
                "play",
                "seekTo(50.0)",
                "setSpeed(2.0)",
                "pause",
            )
            engine.sources.size shouldBe 1
        }

        // TC-87
        it("holds awaitReady until the test completes it") {
            runTest {
                val engine = FakeAudioEngine()
                engine.ready = CompletableDeferred()
                var done = false

                val job = launch {
                    engine.awaitReady()
                    done = true
                }
                runCurrent()
                done shouldBe false

                engine.ready!!.complete(Unit)
                runCurrent()
                done shouldBe true
                job.join()
            }
        }
    }

    describe("FakePendingResume") {

        // TC-87
        it("defers the settle answer until the test completes it (C5)") {
            runTest {
                val pending = FakePendingResume()
                pending.settleAnswer = CompletableDeferred()
                var answer: Double? = null

                val job = launch { answer = pending.settle(22L, 3000.0) }
                runCurrent()
                pending.settleCalls shouldBe listOf(22L to 3000.0)
                answer shouldBe null

                pending.settleAnswer.complete(300.0)
                runCurrent()
                answer shouldBe 300.0
                job.join()
            }
        }
    }
})
