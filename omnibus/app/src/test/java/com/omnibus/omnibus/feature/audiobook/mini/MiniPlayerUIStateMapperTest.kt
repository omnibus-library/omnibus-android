package com.omnibus.omnibus.feature.audiobook.mini

import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.noChapterState
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.playingBook
import com.omnibus.omnibus.ui.components.CoverIdentity
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class MiniPlayerUIStateMapperTest : DescribeSpec({

    describe("visibility") {

        // P-MINI-1
        it("#172 TC-1: no book is not visible, even with a stale position and duration") {
            val state = playbackState(book = null, positionSeconds = 900.0, durationSeconds = 2700.0)

            miniPlayerUIState(state, stopping = null).visible shouldBe false
        }

        // P-MINI-1, P-MINI-4, P-MINI-6
        it("#172 TC-2: a loaded book is visible whatever its playback status") {
            val states = listOf(
                playbackState(isPlaying = false),
                playbackState(isPlaying = true),
                playbackState(book = playingBook(title = ""), chapters = emptyList(), isLoading = true),
                playbackState(isPlaying = false, error = PlaybackError.PartUnreadable),
            )

            states.forEach { state ->
                val result = miniPlayerUIState(state, stopping = null)
                result.visible shouldBe true
                result.bookUuid shouldBe state.book!!.bookUuid
                result.isPlaying shouldBe state.isPlaying
            }
        }

        // P-MINI-3
        it("#172 TC-3: the book being stopped is hidden") {
            val state = playbackState()

            miniPlayerUIState(state, stopping = playingBook()).visible shouldBe false
        }

        // P-MINI-3
        it("#172 TC-4: a different book, or another file of the same book, is visible") {
            val state = playbackState()

            miniPlayerUIState(state, stopping = playingBook(bookUuid = "other")).visible shouldBe true
            miniPlayerUIState(state, stopping = playingBook(bookFileId = 12L)).visible shouldBe true
        }
    }

    describe("subtitle") {

        // P-MINI-1
        it("#172 TC-5: the current chapter's title is the subtitle when the author is set too") {
            val state = playbackState(positionSeconds = 0.0)

            miniPlayerUIState(state, stopping = null).subtitle shouldBe "Chapter 1"
        }

        // P-MINI-1
        it("#172 TC-6: the author is the subtitle without chapters, or with a blank chapter title") {
            miniPlayerUIState(noChapterState(), stopping = null).subtitle shouldBe "An Author"
            val blankChapter = playbackState(chapters = chapters(600.0, titles = listOf("  ")))
            miniPlayerUIState(blankChapter, stopping = null).subtitle shouldBe "An Author"
        }

        // P-MINI-1
        it("#172 TC-7: the subtitle is null without a chapter title or an author") {
            val noAuthor = playbackState(book = playingBook(author = null), chapters = emptyList())
            miniPlayerUIState(noAuthor, stopping = null).subtitle shouldBe null

            val blankAuthor = playbackState(book = playingBook(author = "  "), chapters = emptyList())
            miniPlayerUIState(blankAuthor, stopping = null).subtitle shouldBe null
        }
    }

    describe("progress") {

        // P-MINI-1 (whole-book line)
        it("#172 TC-8: progress is the whole-book fraction, not the chapter's") {
            val state = playbackState(positionSeconds = 900.0)

            miniPlayerUIState(state, stopping = null).progress shouldBe (900.0 / 2700.0).toFloat()
        }

        // P-MINI-1
        it("#172 TC-9: progress is clamped to zero and one") {
            miniPlayerUIState(playbackState(positionSeconds = 3000.0), stopping = null).progress shouldBe 1f
            miniPlayerUIState(playbackState(positionSeconds = -5.0), stopping = null).progress shouldBe 0f
        }

        // P-MINI-1
        it("#172 TC-10: an unknown or non-finite duration or position gives zero progress") {
            val cases = listOf(
                noChapterState(positionSeconds = 100.0, durationSeconds = 0.0),
                noChapterState(positionSeconds = 100.0, durationSeconds = -1.0),
                noChapterState(positionSeconds = 100.0, durationSeconds = Double.NaN),
                noChapterState(positionSeconds = 100.0, durationSeconds = Double.POSITIVE_INFINITY),
                noChapterState(positionSeconds = Double.NaN, durationSeconds = 3600.0),
            )

            cases.forEach { state ->
                val progress = miniPlayerUIState(state, stopping = null).progress
                progress.isNaN() shouldBe false
                progress shouldBe 0f
            }
        }
    }

    describe("title and cover") {

        // P-MINI-1
        it("#172 TC-11: the title and cover come from the playing book; a loading book has no title yet") {
            val result = miniPlayerUIState(playbackState(), stopping = null)

            result.title shouldBe "The Book"
            result.cover shouldBe CoverIdentity(uuid = "book-1", title = "The Book", creator = "An Author", hasCover = true)

            val loading = playbackState(book = playingBook(title = ""), chapters = emptyList(), isLoading = true)
            miniPlayerUIState(loading, stopping = null).title shouldBe ""
        }
    }

    describe("hidden state") {

        // P-MINI-1, P-MINI-3
        it("#172 TC-12: every field is at its default while hidden, so no stale title shows") {
            val idle = PlaybackState(positionSeconds = 900.0, durationSeconds = 2700.0)
            miniPlayerUIState(idle, stopping = null) shouldBe MiniPlayerUIState()

            val stale = playbackState(positionSeconds = 900.0, isPlaying = true)
            miniPlayerUIState(stale, stopping = stale.book) shouldBe MiniPlayerUIState()
        }
    }
})
