package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import okio.ByteString.Companion.encodeUtf8

private fun ControllerRig.dropAndRestoreConnection() {
    connectivity.isOnline.value = false
    settle()
    connectivity.isOnline.value = true
    settle()
}

/** Artwork: the cover push, the plate, the retry schedule on the controller's clock, and reconnects (I-SYS-4). */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAudiobookControllerArtworkTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    fun sut() = ControllerRig(TestCoroutineScheduler())

    describe("showing the cover") {
        context("when the book has a cover") {
            // TC-45
            it("shows its bytes (AND-STK-8)") {
                val bytes = randomString().encodeUtf8()
                val rig = sut()
                rig.artwork.answer = CoverArtwork.Found(bytes)

                rig.loadAndSettle()

                rig.engine.nowPlaying.last()?.image shouldBe NowPlayingImage.Cover(bytes)
            }
        }

        context("when the book has no cover") {
            // TC-45
            it("shows the plate (I-SYS-4)") {
                val rig = sut()
                rig.artwork.answer = CoverArtwork.NoCover

                rig.loadAndSettle()

                rig.engine.nowPlaying.last()?.image shouldBe NowPlayingImage.Plate
            }

            // TC-45
            it("does not fetch again (I-SYS-4)") {
                val rig = sut()
                rig.artwork.answer = CoverArtwork.NoCover
                rig.loadAndSettle()

                rig.advance(60_000)

                rig.artwork.requests.size shouldBe 1
            }
        }

        context("when the cover cannot be fetched") {
            // TC-45
            it("shows the plate (I-SYS-4)") {
                val rig = sut()
                rig.artwork.answer = CoverArtwork.Failed

                rig.loadAndSettle()

                rig.engine.nowPlaying.last()?.image shouldBe NowPlayingImage.Plate
            }

            // TC-45
            it("fetches again after 15 seconds, then after 30 more (I-SYS-4)") {
                val rig = sut()
                rig.artwork.answer = CoverArtwork.Failed
                rig.loadAndSettle()

                rig.advance(14_999)
                rig.artwork.requests.size shouldBe 1
                rig.advance(1)
                rig.artwork.requests.size shouldBe 2

                rig.advance(29_999)
                rig.artwork.requests.size shouldBe 2
                rig.advance(1)
                rig.artwork.requests.size shouldBe 3
            }

            context("when play is pressed") {
                // TC-45
                it("fetches at once (I-SYS-4)") {
                    val rig = sut()
                    rig.artwork.answer = CoverArtwork.Failed
                    rig.loadAndSettle()

                    rig.controller.play()
                    rig.settle()

                    rig.artwork.requests.size shouldBe 2
                }
            }

            context("when the book changes") {
                // TC-45
                it("fetches the new book's cover at once (I-SYS-4)") {
                    val rig = sut()
                    rig.artwork.answer = CoverArtwork.Failed
                    rig.loadAndSettle()

                    rig.controller.load("book-2", null, null, true)
                    rig.settle()

                    rig.artwork.requests shouldBe listOf("book-1", "book-2")
                }
            }
        }
    }

    describe("the connection coming back") {
        context("when the cover has failed once") {
            // TC-44(b)
            it("fetches at once (I-SYS-4)") {
                val rig = sut()
                rig.artwork.answer = CoverArtwork.Failed
                rig.loadAndSettle()

                rig.dropAndRestoreConnection()

                rig.artwork.requests.size shouldBe 2
            }
        }

        context("when the cover has failed twice") {
            // TC-44(b)
            it("keeps the 30 second wait (I-SYS-4)") {
                val rig = sut()
                rig.artwork.answer = CoverArtwork.Failed
                rig.loadAndSettle()
                rig.advance(15_000)

                rig.dropAndRestoreConnection()
                rig.artwork.requests.size shouldBe 2

                rig.advance(29_999)
                rig.artwork.requests.size shouldBe 2
                rig.advance(1)
                rig.artwork.requests.size shouldBe 3
            }
        }
    }
})
