package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.audiobook.AudiobookManifestError
import com.omnibus.omnibus.data.audiobook.AudiobookManifestResult
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

/** The load pipeline: file choice, the loaded file id, manifest failures, the interim and loaded book, and stale loads. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAudiobookControllerLoadTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    fun sut(resolver: ResumeStartResolver? = null) = ControllerRig(TestCoroutineScheduler(), resolver = resolver)

    describe("choosing the file to request") {
        context("when the caller asks for no file") {
            // TC-2
            it("requests the saved file (I-LOAD-1)") {
                val rig = sut(resolver = ScriptedResolver(savedFileId = 22L))

                rig.loadAndSettle(bookFileId = null)

                rig.repository.requests shouldBe listOf("book-1" to 22L)
            }
        }

        context("when the caller asks for a file") {
            // TC-2
            it("requests that file over the saved one (I-LOAD-1)") {
                val rig = sut(resolver = ScriptedResolver(savedFileId = 22L))

                rig.loadAndSettle(bookFileId = 33L)

                rig.repository.requests shouldBe listOf("book-1" to 33L)
            }
        }
    }

    describe("the file that was loaded") {
        context("when the manifest names a file") {
            // TC-3
            it("is the manifest's file (I-LOAD-1)") {
                val rig = sut()
                rig.repository.result = AudiobookManifestResult.Success(multiPartManifest(bookFileId = 44L))

                rig.loadAndSettle()

                rig.controller.state.value.book?.bookFileId shouldBe 44L
            }
        }

        context("when the manifest names no file") {
            context("when a file was requested") {
                // TC-3
                it("is the requested file (I-LOAD-1)") {
                    val rig = sut()
                    rig.repository.result = AudiobookManifestResult.Success(multiPartManifest(bookFileId = 0L))

                    rig.loadAndSettle(bookFileId = 33L)

                    rig.controller.state.value.book?.bookFileId shouldBe 33L
                }
            }

            context("when no file was requested") {
                context("when the book has audio files") {
                    // TC-3
                    it("is the first audio file by ordinal (I-LOAD-1)") {
                        val rig = sut()
                        rig.library.bookResult = BookResult.Success(
                            testBook(
                                id = 1,
                                bookFiles = listOf(bookFile(id = 9L, format = "m4b", ordinal = 2), bookFile(id = 8L, format = "mp3", ordinal = 1)),
                            ),
                        )
                        rig.repository.result = AudiobookManifestResult.Success(multiPartManifest(bookFileId = 0L))

                        rig.loadAndSettle()

                        rig.controller.state.value.book?.bookFileId shouldBe 8L
                    }
                }

                context("when the book has no audio files") {
                    // TC-3
                    it("is 0 (I-LOAD-1)") {
                        val rig = sut()
                        rig.library.bookResult = BookResult.Success(testBook(id = 1, bookFiles = emptyList()))
                        rig.repository.result = AudiobookManifestResult.Success(multiPartManifest(bookFileId = 0L))

                        rig.loadAndSettle()

                        rig.controller.state.value.book?.bookFileId shouldBe 0L
                    }
                }
            }
        }
    }

    describe("a manifest failure") {
        // TC-18
        it("shows the playback error for each failure (P-PLAY-10)") {
            checkAll(
                Exhaustive.of(
                    AudiobookManifestError.Http(500, "x") to PlaybackError.Server("x"),
                    AudiobookManifestError.Http(404, null) to PlaybackError.Server(null),
                    AudiobookManifestError.SessionExpired to PlaybackError.Server(null),
                    AudiobookManifestError.NoServer to PlaybackError.Server(null),
                    AudiobookManifestError.Unreachable to PlaybackError.Server(null),
                    AudiobookManifestError.UnexpectedResponse to PlaybackError.Server(null),
                    AudiobookManifestError.NoAudioParts to PlaybackError.NoAudioParts,
                    AudiobookManifestError.InvalidManifest to PlaybackError.TimelineFailed,
                ),
            ) { (failure, expected) ->
                val rig = sut()
                rig.repository.result = AudiobookManifestResult.Failure(failure)

                rig.loadAndSettle()

                rig.controller.state.value.error shouldBe expected
            }
        }
    }

    describe("whether the loaded book has an ebook") {
        context("when the book has an epub file") {
            // TC-22
            it("says it does (C1)") {
                val rig = sut()
                rig.library.bookResult = BookResult.Success(
                    testBook(id = 1, formats = listOf("m4b"), bookFiles = listOf(bookFile(id = 5L, format = "epub", ordinal = 1))),
                )

                rig.loadAndSettle()

                rig.controller.state.value.book?.hasEbook shouldBe true
            }
        }

        context("when the book's only ebook format is pdf") {
            // TC-22: the flag gates the epub reader, so a pdf does not count.
            it("says it does not (C1)") {
                val rig = sut()
                rig.library.bookResult = BookResult.Success(testBook(id = 1, formats = listOf("pdf")))

                rig.loadAndSettle()

                rig.controller.state.value.book?.hasEbook shouldBe false
            }
        }
    }

    describe("the state during a load") {
        context("when the manifest has not answered") {
            // TC-50
            it("shows the book's interim fields at once (C1d)") {
                val rig = sut()
                rig.repository.pending = CompletableDeferred()

                rig.controller.load("book-1", 33L, null, true)

                rig.controller.state.value.book shouldBe PlayingBook("book-1", 33L, "", null, false)
                rig.controller.state.value.isLoading shouldBe true
            }
        }

        context("when the manifest has answered") {
            // TC-50
            it("shows the book's details, chapters and duration (C1)") {
                val rig = sut()

                rig.loadAndSettle()

                val state = rig.controller.state.value
                state.book shouldBe PlayingBook("book-1", 11L, "The Book", "An Author", false)
                state.chapters shouldBe multiPartManifest().chapters
                state.durationSeconds shouldBe 3_000.0
                state.isLoading shouldBe false
            }
        }

        context("when the book cannot be read") {
            // TC-51
            it("plays on with an empty title (R9a)") {
                val rig = sut()
                rig.library.bookResult = BookResult.NotFound

                rig.loadAndSettle()

                val state = rig.controller.state.value
                state.book?.title shouldBe ""
                state.error shouldBe null
                state.isPlaying shouldBe true
            }
        }
    }

    describe("a load replaced by another") {
        context("when its manifest answers late") {
            // TC-62
            it("is ignored (C2)") {
                val rig = sut()
                val late = CompletableDeferred<AudiobookManifestResult>()
                rig.repository.pending = late
                rig.controller.load("book-A", null, null, true)
                rig.settle()
                rig.repository.pending = null
                rig.controller.load("book-B", null, null, true)
                rig.settle()

                late.complete(AudiobookManifestResult.Success(multiPartManifest(bookFileId = 77L)))
                rig.settle()

                rig.controller.state.value.book?.bookUuid shouldBe "book-B"
                rig.engine.sources.map { it.mediaId } shouldBe listOf("book-B")
            }
        }
    }
})
