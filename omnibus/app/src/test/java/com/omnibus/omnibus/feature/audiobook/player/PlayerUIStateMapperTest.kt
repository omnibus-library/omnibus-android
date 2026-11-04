package com.omnibus.omnibus.feature.audiobook.player

import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.noChapterState
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.playingBook
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.components.CoverIdentity
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private const val BOOK = "uuid-1"

class PlayerUIStateMapperTest : DescribeSpec({

    describe("preparing") {

        // P-SCR-12, I-HLTH-1
        it("#165 TC-25: preparing until the controller holds this book") {
            val other = playbackState(book = playingBook("other-book"), isPlaying = true, positionSeconds = 300.0)
            val state = playerUIState(other, BOOK, null, PlayerGate.Open, leave = false)

            state.isPreparing shouldBe true
            state.isBuffering shouldBe false
            state.isPlaying shouldBe false
            state.hasEbook shouldBe false
            state.durationSeconds shouldBe 0.0
            state.spanLengthSeconds shouldBe 0.0
            state.spanPositionSeconds shouldBe 0.0
            state.hasChapters shouldBe false
            state.gate shouldBe PlayerGate.Open

            val none = playerUIState(PlaybackState(), BOOK, null, PlayerGate.Checking, leave = false)
            none.isPreparing shouldBe true
            none.canNextChapter shouldBe false
        }
    }

    describe("preparing and buffering") {

        // I-HLTH-1
        it("#165 TC-26: buffering is suppressed while preparing (I-HLTH-1)") {
            val loading = playerUIState(
                playbackState(book = playingBook(BOOK), isLoading = true),
                BOOK, null, PlayerGate.Open, leave = false,
            )
            loading.isPreparing shouldBe true
            loading.isBuffering shouldBe false

            val stalled = playerUIState(
                playbackState(book = playingBook(BOOK), isBuffering = true),
                BOOK, null, PlayerGate.Open, leave = false,
            )
            stalled.isPreparing shouldBe false
            stalled.isBuffering shouldBe true

            val both = playerUIState(
                playbackState(book = playingBook(BOOK), isLoading = true, isBuffering = true),
                BOOK, null, PlayerGate.Open, leave = false,
            )
            both.isPreparing shouldBe true
            both.isBuffering shouldBe false
        }
    }

    describe("titles") {

        val library = testBook(1, title = "Library Title", creators = listOf(Creator("Library Author", null, null, null)))

        // P-SCR-6
        it("#165 TC-27: falls back to the library title while loading (P-SCR-6)") {
            val loading = playerUIState(
                playbackState(book = playingBook(BOOK, title = "", author = null)),
                BOOK, library, PlayerGate.Open, leave = false,
            )
            loading.title shouldBe "Library Title"
            loading.author shouldBe "Library Author"

            val loaded = playerUIState(
                playbackState(book = playingBook(BOOK, title = "Controller Title", author = "Controller Author")),
                BOOK, library, PlayerGate.Open, leave = false,
            )
            loaded.title shouldBe "Controller Title"
            loaded.author shouldBe "Controller Author"

            val neither = playerUIState(
                playbackState(book = playingBook(BOOK, title = "", author = null)),
                BOOK, null, PlayerGate.Open, leave = false,
            )
            neither.title shouldBe ""
            neither.author.shouldBeNull()
        }
    }

    describe("cover identity") {

        // P-SCR-6, AND-RUSE-4
        it("#165 TC-28: falls back to a uuid-keyed identity") {
            val withLibrary = playerUIState(
                playbackState(book = playingBook(BOOK, title = "The Book")),
                BOOK, testBook(1, title = "Library Title", accent = "#ff8800"), PlayerGate.Open, leave = false,
            )
            withLibrary.cover shouldBe testBook(1, title = "Library Title", accent = "#ff8800").coverIdentity()

            val withoutLibrary = playerUIState(
                playbackState(book = playingBook(BOOK, title = "The Book", author = "An Author")),
                BOOK, null, PlayerGate.Open, leave = false,
            )
            withoutLibrary.cover shouldBe CoverIdentity(
                uuid = BOOK,
                title = "The Book",
                creator = "An Author",
                hasCover = true,
            )
        }
    }

    describe("span") {

        // AC3, AC5, I-TL-4
        it("#165 TC-29: scopes to the chapter, or the whole book without chapters") {
            val withChapters = playerUIState(
                playbackState(book = playingBook(BOOK), chapters = chapters(600.0, 1200.0, 900.0), positionSeconds = 700.0),
                BOOK, null, PlayerGate.Open, leave = false,
            )
            withChapters.hasChapters shouldBe true
            withChapters.chapterTitle shouldBe "Chapter 2"
            withChapters.spanStartSeconds shouldBe 600.0
            withChapters.spanLengthSeconds shouldBe 1200.0
            withChapters.spanPositionSeconds shouldBe 100.0

            val withoutChapters = playerUIState(
                noChapterState(positionSeconds = 100.0, durationSeconds = 3600.0).copy(book = playingBook(BOOK)),
                BOOK, null, PlayerGate.Open, leave = false,
            )
            withoutChapters.hasChapters shouldBe false
            withoutChapters.chapterTitle.shouldBeNull()
            withoutChapters.spanStartSeconds shouldBe 0.0
            withoutChapters.spanLengthSeconds shouldBe 3600.0
            withoutChapters.spanPositionSeconds shouldBe 100.0
        }
    }

    describe("transport enablement") {

        // P-SCR-AC4
        it("#165 TC-30: next is disabled on the last chapter and both without chapters") {
            val lengths = doubleArrayOf(600.0, 1200.0, 900.0)
            val middle = playerUIState(
                playbackState(book = playingBook(BOOK), chapters = chapters(*lengths), positionSeconds = 700.0),
                BOOK, null, PlayerGate.Open, leave = false,
            )
            middle.canPreviousChapter shouldBe true
            middle.canNextChapter shouldBe true

            val first = playerUIState(
                playbackState(book = playingBook(BOOK), chapters = chapters(*lengths), positionSeconds = 2.0),
                BOOK, null, PlayerGate.Open, leave = false,
            )
            first.canPreviousChapter shouldBe true
            first.canNextChapter shouldBe true

            val last = playerUIState(
                playbackState(book = playingBook(BOOK), chapters = chapters(*lengths), positionSeconds = 2000.0),
                BOOK, null, PlayerGate.Open, leave = false,
            )
            last.canNextChapter shouldBe false
            last.canPreviousChapter shouldBe true

            val none = playerUIState(
                noChapterState(positionSeconds = 100.0).copy(book = playingBook(BOOK)),
                BOOK, null, PlayerGate.Open, leave = false,
            )
            none.canPreviousChapter shouldBe false
            none.canNextChapter shouldBe false
        }
    }

    describe("pass-through") {

        // R6, R14
        it("#165 TC-31: carries rate, ebook flag, error and sleep timer") {
            val timer = SleepTimerState(SleepTimerOption.After(5), remainingSeconds = 120.0)
            val state = playerUIState(
                playbackState(
                    book = playingBook(BOOK, hasEbook = true),
                    isPlaying = true,
                    rate = 1.5,
                    error = PlaybackError.Server("Boom"),
                    sleepTimer = timer,
                    durationSeconds = 5400.0,
                ),
                BOOK, null, PlayerGate.Open, leave = true,
            )
            state.hasEbook shouldBe true
            state.isPlaying shouldBe true
            state.rate shouldBe 1.5
            state.error shouldBe PlaybackError.Server("Boom")
            state.sleepTimer shouldBe timer
            state.durationSeconds shouldBe 5400.0
            state.gate shouldBe PlayerGate.Open
            state.leave shouldBe true
        }
    }
})
