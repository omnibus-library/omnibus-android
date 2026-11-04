package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.audiobook.AudiobookManifestError
import com.omnibus.omnibus.data.audiobook.AudiobookManifestResult
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** The load pipeline: file choice, the loaded file id, manifest failures, the interim and loaded book, and ordering. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAudiobookControllerLoadTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    lateinit var scheduler: TestCoroutineScheduler
    val rigs = mutableListOf<ControllerRig>()

    fun newRig(
        resolver: ResumeStartResolver? = null,
        order: MutableList<String>? = null,
        listeners: Set<PlaybackListener> = emptySet(),
    ) = ControllerRig(scheduler, resolver = resolver, order = order, listeners = listeners).also { rigs += it }

    beforeEach {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    afterEach {
        rigs.forEach { it.close() }
        rigs.clear()
        Dispatchers.resetMain()
    }

    describe("file choice") {
        // TC-2: the saved file id is requested when the caller asked for no file (I-LOAD-1).
        it("passes the saved file id (I-LOAD-1)") {
            val rig = newRig(resolver = ScriptedResolver(savedFileId = 22L))
            rig.loadAndSettle(bookFileId = null)
            rig.repository.requests shouldBe listOf("book-1" to 22L)
        }

        // TC-2: an explicit file id wins over the saved one (I-LOAD-1).
        it("explicit id wins (I-LOAD-1)") {
            val rig = newRig(resolver = ScriptedResolver(savedFileId = 22L))
            rig.loadAndSettle(bookFileId = 33L)
            rig.repository.requests shouldBe listOf("book-1" to 33L)
        }

        // TC-2: with no resolver bound, step 2 is skipped and the server picks the file (I-LOAD-1).
        it("no resolver skips step 2 (I-LOAD-1)") {
            val rig = newRig()
            rig.loadAndSettle(bookFileId = null)
            rig.repository.requests shouldBe listOf("book-1" to null)
        }
    }

    describe("loaded file id") {
        // TC-3: the manifest's book_file_id is the loaded file (I-LOAD-1).
        it("uses the manifest id (I-LOAD-1)") {
            val rig = newRig()
            rig.repository.result = AudiobookManifestResult.Success(multiPartManifest(bookFileId = 44L))
            rig.loadAndSettle()
            rig.controller.state.value.book?.bookFileId shouldBe 44L
        }

        // TC-3: the manifest says 0, so the requested id stands (I-LOAD-1).
        it("falls back to the requested id (I-LOAD-1)") {
            val rig = newRig()
            rig.repository.result = AudiobookManifestResult.Success(multiPartManifest(bookFileId = 0L))
            rig.loadAndSettle(bookFileId = 33L)
            rig.controller.state.value.book?.bookFileId shouldBe 33L
        }

        // TC-3: nothing requested and the manifest says 0, so the book's first audio file by ordinal (I-LOAD-1).
        it("falls back to the first audio file by ordinal (I-LOAD-1)") {
            val rig = newRig()
            rig.library.bookResult = BookResult.Success(
                testBook(
                    id = 1,
                    uniqueIdentifier = "book-1",
                    formats = listOf("m4b"),
                    bookFiles = listOf(bookFile(id = 9L, format = "m4b", ordinal = 2), bookFile(id = 8L, format = "mp3", ordinal = 1)),
                ),
            )
            rig.repository.result = AudiobookManifestResult.Success(multiPartManifest(bookFileId = 0L))
            rig.loadAndSettle()
            rig.controller.state.value.book?.bookFileId shouldBe 8L
        }

        // TC-3: nothing from the manifest, the request or the book's files, so 0 (I-LOAD-1).
        it("falls back to 0 (I-LOAD-1)") {
            val rig = newRig()
            rig.repository.result = AudiobookManifestResult.Success(multiPartManifest(bookFileId = 0L))
            rig.loadAndSettle()
            rig.controller.state.value.book?.bookFileId shouldBe 0L
        }
    }

    describe("manifest failures") {
        // TC-18: each failure maps to its PlaybackError, the book stays set, and the load stops (P-PLAY-10).
        it("maps to PlaybackError (P-PLAY-10)") {
            val cases = listOf(
                AudiobookManifestError.Http(500, "x") to PlaybackError.Server("x"),
                AudiobookManifestError.Http(404, null) to PlaybackError.Server(null),
                AudiobookManifestError.SessionExpired to PlaybackError.Server(null),
                AudiobookManifestError.NoServer to PlaybackError.Server(null),
                AudiobookManifestError.Unreachable to PlaybackError.Server(null),
                AudiobookManifestError.UnexpectedResponse to PlaybackError.Server(null),
                AudiobookManifestError.NoAudioParts to PlaybackError.NoAudioParts,
                AudiobookManifestError.InvalidManifest to PlaybackError.TimelineFailed,
            )
            cases.forEach { (failure, expected) ->
                val rig = newRig()
                rig.repository.result = AudiobookManifestResult.Failure(failure)
                rig.loadAndSettle()
                withClue("failure $failure") {
                    rig.controller.state.value.error shouldBe expected
                    rig.controller.state.value.isLoading shouldBe false
                    rig.controller.state.value.book?.bookUuid shouldBe "book-1"
                }
            }
        }
    }

    describe("PlayingBook") {
        // TC-22: hasEbook follows Book.hasEpub (C1). Here hasEpub is true from a file and hasEbook is false.
        it("hasEbook is Book.hasEpub (C1)") {
            val rig = newRig()
            rig.library.bookResult = BookResult.Success(
                testBook(id = 1, uniqueIdentifier = "book-1", formats = listOf("m4b"), bookFiles = listOf(bookFile(id = 5L, format = "epub", ordinal = 1))),
            )
            rig.loadAndSettle()
            rig.controller.state.value.book?.hasEbook shouldBe true
            rig.controller.state.value.book?.bookFileId shouldBe 11L
        }

        // TC-22: the other way round: hasEbook true from a pdf format, hasEpub false, so hasEbook is false.
        it("hasEbook is false when Book.hasEpub is false (C1)") {
            val rig = newRig()
            rig.library.bookResult = BookResult.Success(testBook(id = 1, uniqueIdentifier = "book-1", formats = listOf("pdf")))
            rig.loadAndSettle()
            rig.controller.state.value.book?.hasEbook shouldBe false
        }

        // TC-22: the resolved chapter lengths reach the state (C1).
        it("chapter lengths are resolved (C1)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.controller.state.value.chapters.map { it.durationSeconds } shouldBe listOf(700.0, 1200.0, 1100.0)
        }
    }

    describe("load") {
        // TC-50: the book is set at once with its interim fields while the manifest is in flight (C1d).
        it("sets the book at once while loading (C1d)") {
            val rig = newRig()
            rig.repository.pending = CompletableDeferred()
            rig.controller.load("book-1", 33L, null, true)
            rig.controller.state.value.book shouldBe PlayingBook("book-1", 33L, "", null, false)
            rig.controller.state.value.isLoading shouldBe true
            rig.settle()
            rig.controller.state.value.isLoading shouldBe true
            rig.controller.state.value.book shouldBe PlayingBook("book-1", 33L, "", null, false)
        }

        // TC-50: once the manifest arrives, the title, author, chapters and duration are filled in (C1).
        it("fills title, author and chapters when loaded (C1)") {
            val rig = newRig()
            val manifest = CompletableDeferred<AudiobookManifestResult>()
            rig.repository.pending = manifest
            rig.loadAndSettle()
            rig.controller.state.value.isLoading shouldBe true
            manifest.complete(AudiobookManifestResult.Success(multiPartManifest()))
            rig.settle()
            val state = rig.controller.state.value
            state.book shouldBe PlayingBook("book-1", 11L, "The Book", "An Author", false)
            state.chapters.size shouldBe 3
            state.durationSeconds shouldBe 3000.0
            state.isLoading shouldBe false
        }

        // TC-51: a failed Book read is not fatal, so playback goes on with an empty title.
        it("a failed Book read keeps going with an empty title (R9a)") {
            val rig = newRig()
            rig.library.bookResult = BookResult.NotFound
            rig.loadAndSettle()
            val state = rig.controller.state.value
            state.book?.title shouldBe ""
            state.error shouldBe null
            state.isPlaying shouldBe true
        }

        // TC-52: the pipeline runs in order. Begin precedes both reads, the source is set before settle, and settle
        // precedes onSettled and play. The connection is started by startLoad, before begin, so awaitReady is only
        // required to come before setSource; the spec's position for it is relaxed here (see the PR notes).
        it("runs the pipeline in order (C2)") {
            val order = mutableListOf<String>()
            val resolver = ScriptedResolver(order = order)
            val listener = object : PlaybackListener {
                override fun onSettled(state: PlaybackState, startSeconds: Double) {
                    order += "settled"
                }
            }
            val rig = newRig(resolver = resolver, order = order, listeners = setOf(listener))
            rig.loadAndSettle()
            order.shouldBeInOrder("begin", "getBook", "getManifest", "setSource", "settle", "settled", "play")
            order.shouldBeInOrder("awaitReady", "setSource")
        }
    }

    describe("stale loads") {
        // TC-62: a superseded load returns silently. Its resume is cancelled, and a late answer is ignored.
        it("a superseded load returns silently (C2)") {
            val resolver = ScriptedResolver()
            val rig = newRig(resolver = resolver)
            val lateA = CompletableDeferred<AudiobookManifestResult>()
            rig.repository.pending = lateA
            rig.controller.load("book-A", null, null, true)
            rig.settle()
            rig.repository.pending = null
            rig.controller.load("book-B", null, null, true)
            rig.settle()
            lateA.complete(AudiobookManifestResult.Success(multiPartManifest(bookFileId = 77L)))
            rig.settle()

            val state = rig.controller.state.value
            state.book?.bookUuid shouldBe "book-B"
            state.isLoading shouldBe false
            resolver.pendings[0].cancelCount shouldBe 1
            resolver.pendings[0].settleCalls shouldBe emptyList()
            resolver.pendings[1].settleCalls.size shouldBe 1
            rig.engine.sources.map { it.mediaId } shouldBe listOf("book-B")
        }

        // TC-62: stop() while a load is in flight abandons it the same way.
        it("stop while loading abandons the load (C2)") {
            val resolver = ScriptedResolver()
            val rig = newRig(resolver = resolver)
            val held = CompletableDeferred<AudiobookManifestResult>()
            rig.repository.pending = held
            rig.controller.load("book-A", null, null, true)
            rig.settle()
            rig.controller.stop()
            rig.settle()
            held.complete(AudiobookManifestResult.Success(multiPartManifest()))
            rig.settle()

            rig.controller.state.value shouldBe PlaybackState()
            resolver.pendings[0].cancelCount shouldBe 1
            rig.engine.sources shouldBe emptyList()
        }
    }
})
