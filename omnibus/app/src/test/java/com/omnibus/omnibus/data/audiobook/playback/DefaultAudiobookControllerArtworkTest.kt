package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.ByteString.Companion.encodeUtf8

/** Artwork: the cover push, the plate, the retry schedule on the controller's clock, and reconnect wiring (I-SYS-4). */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAudiobookControllerArtworkTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    lateinit var scheduler: TestCoroutineScheduler
    val rigs = mutableListOf<ControllerRig>()

    fun newRig(online: Boolean = true) = ControllerRig(scheduler, online = online).also { rigs += it }

    beforeEach {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    afterEach {
        rigs.forEach { it.close() }
        rigs.clear()
        Dispatchers.resetMain()
    }

    describe("reconnect") {
        // TC-44(b): a returning connection retries after one failure at once. After two failures it keeps the 30 s wait.
        it("a returning connection retries after one failure only (I-SYS-4)") {
            // One failure: the connection coming back refetches at once.
            val once = newRig()
            once.artwork.answer = CoverArtwork.Failed
            once.loadAndSettle()
            once.artwork.requests.size shouldBe 1
            once.connectivity.isOnline.value = false
            once.settle()
            once.connectivity.isOnline.value = true
            once.settle()
            once.artwork.requests.size shouldBe 2

            // Two failures: the connection coming back does not refetch, and a value that stays true triggers nothing.
            val twice = newRig()
            twice.artwork.answer = CoverArtwork.Failed
            twice.loadAndSettle()
            twice.advance(15_000)
            twice.artwork.requests.size shouldBe 2
            twice.connectivity.isOnline.value = false
            twice.settle()
            twice.connectivity.isOnline.value = true
            twice.settle()
            twice.connectivity.isOnline.value = true
            twice.settle()
            twice.artwork.requests.size shouldBe 2
            twice.advance(29_999)
            twice.artwork.requests.size shouldBe 2
            twice.advance(1)
            twice.artwork.requests.size shouldBe 3
        }

        // TC-44(b): with no book loaded, a connection change fetches nothing.
        it("fetches nothing when no book is loaded (I-SYS-4)") {
            val rig = newRig()
            rig.connectivity.isOnline.value = false
            rig.settle()
            rig.connectivity.isOnline.value = true
            rig.settle()
            rig.artwork.requests.shouldBeEmpty()
        }
    }

    describe("artwork") {
        val bytes = "cover-bytes".encodeUtf8()

        // TC-45: a found cover reaches the session's now-playing image (AND-STK-8).
        it("pushes the cover bytes (AND-STK-8)") {
            val rig = newRig()
            rig.artwork.answer = CoverArtwork.Found(bytes)
            rig.loadAndSettle()
            rig.engine.nowPlaying.last()?.image shouldBe NowPlayingImage.Cover(bytes)
        }

        // TC-45: no cover asks for the generated plate, and is not retried (I-SYS-4).
        it("no cover asks for the plate (I-SYS-4)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.engine.nowPlaying.last()?.image shouldBe NowPlayingImage.Plate
            rig.advance(60_000)
            rig.artwork.requests.size shouldBe 1
        }

        // TC-45: a failure is refetched after 15 s, then after 30 s (I-SYS-4).
        it("retries a failure after 15 s doubling (I-SYS-4)") {
            val rig = newRig()
            rig.artwork.answer = CoverArtwork.Failed
            rig.loadAndSettle()
            rig.artwork.requests.size shouldBe 1
            rig.advance(14_999)
            rig.artwork.requests.size shouldBe 1
            rig.advance(1)
            rig.artwork.requests.size shouldBe 2
            rig.advance(29_999)
            rig.artwork.requests.size shouldBe 2
            rig.advance(1)
            rig.artwork.requests.size shouldBe 3
        }

        // TC-45: play lifts the wait, so the cover is fetched at once (I-SYS-4).
        it("play clears the wait (I-SYS-4)") {
            val rig = newRig()
            rig.artwork.answer = CoverArtwork.Failed
            rig.loadAndSettle()
            rig.artwork.requests.size shouldBe 1
            rig.controller.play()
            rig.settle()
            rig.artwork.requests.size shouldBe 2
        }

        // TC-45: a new book starts clean, so its cover is fetched at once (I-SYS-4).
        it("switching book resets the wait (I-SYS-4)") {
            val rig = newRig()
            rig.artwork.answer = CoverArtwork.Failed
            rig.loadAndSettle()
            rig.artwork.requests shouldBe listOf("book-1")
            rig.artwork.answer = CoverArtwork.NoCover
            rig.controller.load("book-2", null, null, true)
            rig.settle()
            rig.artwork.requests shouldBe listOf("book-1", "book-2")
        }
    }
})
