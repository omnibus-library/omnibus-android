package com.omnibus.omnibus.feature.audiobook.mini

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlaybackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.ui.components.CoverIdentity
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

private fun loadedState(): PlaybackState = randomPlaybackState().copy(book = randomPlayingBook())

class MiniPlayerUIStateMapperTest : DescribeSpec({

    describe("showing the bar") {
        context("when no book is loaded") {
            // P-MINI-1
            it("#172 TC-1: stays at its defaults, whatever position is left over") {
                miniPlayerUIState(randomPlaybackState().copy(book = null), stopping = null) shouldBe MiniPlayerUIState()
            }
        }

        context("when the loaded book is the one being stopped") {
            // P-MINI-3
            it("#172 TC-3: stays at its defaults") {
                val state = loadedState()

                miniPlayerUIState(state, stopping = state.book) shouldBe MiniPlayerUIState()
            }
        }

        context("when another book is being stopped") {
            // P-MINI-3
            it("#172 TC-4: shows the loaded book, for a different book or another file of it") {
                val state = loadedState()
                val book = state.book!!

                checkAll(
                    Exhaustive.of(book.copy(bookUuid = "${book.bookUuid}-other"), book.copy(bookFileId = book.bookFileId + 1)),
                ) { stopping ->
                    miniPlayerUIState(state, stopping).visible shouldBe true
                }
            }
        }
    }

    describe("the subtitle") {
        // P-MINI-1
        it("#172 TC-5: is the current chapter's title, over the author") {
            val title = randomString()
            val book = randomPlayingBook().copy(author = randomString())
            val state = playbackState(book = book, chapters = chapters(600.0, titles = listOf(title)))

            miniPlayerUIState(state, stopping = null).subtitle shouldBe title
        }

        context("without a chapter title") {
            // P-MINI-1
            it("#172 TC-6: is the author, for no chapters or a blank chapter title") {
                val author = randomString()
                val book = randomPlayingBook().copy(author = author)

                checkAll(Exhaustive.of(emptyList(), chapters(600.0, titles = listOf("  ")))) { chapters ->
                    val state = playbackState(book = book, chapters = chapters)

                    miniPlayerUIState(state, stopping = null).subtitle shouldBe author
                }
            }

            // P-MINI-1
            it("#172 TC-7: is null for a null or blank author") {
                checkAll(Exhaustive.of(null, "  ")) { author ->
                    val state = playbackState(book = randomPlayingBook().copy(author = author), chapters = emptyList())

                    miniPlayerUIState(state, stopping = null).subtitle.shouldBeNull()
                }
            }
        }
    }

    describe("the progress") {
        // P-MINI-1 (whole-book line)
        it("#172 TC-8: is the whole book's fraction, not the chapter's") {
            val state = playbackState(book = randomPlayingBook(), chapters = chapters(600.0, 2100.0), positionSeconds = 900.0)

            miniPlayerUIState(state, stopping = null).progress shouldBe (900.0 / 2700.0).toFloat()
        }

        // P-MINI-1
        it("#172 TC-9 TC-10: is clamped to the range 0..1, zero for a duration or position that is unknown or not finite") {
            checkAll(
                Exhaustive.of(
                    Triple(3000.0, 2700.0, 1f),
                    Triple(-5.0, 2700.0, 0f),
                    Triple(100.0, 0.0, 0f),
                    Triple(100.0, -1.0, 0f),
                    Triple(100.0, Double.NaN, 0f),
                    Triple(100.0, Double.POSITIVE_INFINITY, 0f),
                    Triple(Double.NaN, 3600.0, 0f),
                ),
            ) { (position, duration, expected) ->
                val state = loadedState().copy(positionSeconds = position, durationSeconds = duration)

                miniPlayerUIState(state, stopping = null).progress shouldBe expected
            }
        }
    }

    describe("the book's identity") {
        // P-MINI-1, P-MINI-4
        it("#172 TC-2 TC-11: comes from the playing book") {
            val book = randomPlayingBook()
            val state = loadedState().copy(book = book)

            val result = miniPlayerUIState(state, stopping = null)

            result.visible shouldBe true
            result.bookUuid shouldBe book.bookUuid
            result.title shouldBe book.title
            result.cover shouldBe CoverIdentity(uuid = book.bookUuid, title = book.title, creator = book.author, hasCover = true)
            result.isPlaying shouldBe state.isPlaying
        }
    }
})
