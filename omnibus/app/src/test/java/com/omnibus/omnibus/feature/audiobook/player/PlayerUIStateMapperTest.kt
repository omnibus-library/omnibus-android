package com.omnibus.omnibus.feature.audiobook.player

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.PlayingBook
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlaybackError
import com.omnibus.omnibus.data.audiobook.playback.randomPlaybackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import com.omnibus.omnibus.data.audiobook.playback.randomSleepTimerState
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.library.randomCreator
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomOf
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.ui.components.CoverIdentity
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class PlayerUIStateMapperTest : DescribeSpec({

    fun gate(): PlayerGate = randomOf(listOf(PlayerGate.Checking, PlayerGate.Open, PlayerGate.Offline))

    /** [book] loaded with three chapters, at [positionSeconds]. */
    fun loaded(book: PlayingBook, positionSeconds: Double = 0.0) =
        playbackState(book = book, chapters = chapters(600.0, 1200.0, 900.0), positionSeconds = positionSeconds)

    fun sut(playback: PlaybackState, book: PlayingBook) =
        playerUIState(playback, book.bookUuid, null, gate(), leave = randomBoolean())

    describe("preparing") {
        context("when the controller doesn't hold this book") {
            // P-SCR-12, I-HLTH-1
            it("#165 TC-25: shows none of the controller's playback") {
                val uuid = randomString()
                val gate = gate()
                val other = randomPlaybackState().copy(book = randomPlayingBook().copy(bookUuid = "$uuid-other"))

                checkAll(Exhaustive.of(other, PlaybackState())) { playback ->
                    val state = playerUIState(playback, uuid, null, gate, leave = false)

                    state.copy(cover = PlayerUIState().cover) shouldBe PlayerUIState(gate = gate)
                }
            }
        }

        // I-HLTH-1
        it("#165 TC-26: shows buffering only for a settled load") {
            val book = randomPlayingBook()

            checkAll(Exhaustive.of(true to false, false to true)) { (isLoading, expected) ->
                sut(playbackState(book = book, isLoading = isLoading, isBuffering = true), book).isBuffering shouldBe expected
            }
        }
    }

    describe("the title") {
        context("when the controller doesn't know it yet") {
            // P-SCR-6
            it("#165 TC-27: falls back to the library's title with its first creator") {
                val book = randomPlayingBook().copy(title = "")
                val library = randomBook().copy(creators = listOf(randomCreator()))

                val state = playerUIState(playbackState(book = book), book.bookUuid, library, gate(), leave = false)

                state.title shouldBe library.displayTitle
                state.author shouldBe library.creators.first().name
            }
        }

        context("when the controller knows it") {
            // P-SCR-6
            it("#165 TC-27: is the controller's, over the library's") {
                val book = randomPlayingBook()

                val state = playerUIState(playbackState(book = book), book.bookUuid, randomBook(), gate(), leave = false)

                state.title shouldBe book.title
                state.author shouldBe book.author
            }
        }
    }

    describe("the cover") {
        context("when the library knows the book") {
            // P-SCR-6, AND-RUSE-4
            it("#165 TC-28: is the library book's") {
                val book = randomPlayingBook()
                val library = randomBook()

                val state = playerUIState(playbackState(book = book), book.bookUuid, library, gate(), leave = false)

                state.cover shouldBe library.coverIdentity()
            }
        }

        context("when the library doesn't know the book") {
            // P-SCR-6, AND-RUSE-4
            it("#165 TC-28: falls back to a uuid-keyed identity") {
                val book = randomPlayingBook()

                sut(playbackState(book = book), book).cover shouldBe CoverIdentity(
                    uuid = book.bookUuid,
                    title = book.title,
                    creator = book.author,
                    hasCover = true,
                )
            }
        }
    }

    describe("the span") {
        // AC3, AC5, I-TL-4
        it("#165 TC-29: is the current chapter") {
            val book = randomPlayingBook()

            val state = sut(loaded(book, positionSeconds = 700.0), book)

            state.spanStartSeconds shouldBe 600.0
            state.spanLengthSeconds shouldBe 1200.0
            state.spanPositionSeconds shouldBe 100.0
        }

    }

    describe("the chapter buttons") {
        // P-SCR-AC4
        it("#165 TC-30: are enabled only with chapters to move through") {
            val book = randomPlayingBook()

            checkAll(
                Exhaustive.of(loaded(book, positionSeconds = 700.0) to true, playbackState(book = book, chapters = emptyList()) to false),
            ) { (playback, expected) ->
                val state = sut(playback, book)

                state.canPreviousChapter shouldBe expected
                state.canNextChapter shouldBe expected
            }
        }
    }

    describe("the rest of the playback state") {
        // R6, R14
        it("#165 TC-31: passes through") {
            val book = randomPlayingBook()
            val playback = randomPlaybackState().copy(
                book = book,
                error = randomPlaybackError(),
                sleepTimer = randomSleepTimerState(),
            )
            val gate = gate()
            val leave = randomBoolean()

            val state = playerUIState(playback, book.bookUuid, null, gate, leave)

            state.hasEbook shouldBe book.hasEbook
            state.isPlaying shouldBe playback.isPlaying
            state.rate shouldBe playback.rate
            state.error shouldBe playback.error
            state.sleepTimer shouldBe playback.sleepTimer
            state.durationSeconds shouldBe playback.durationSeconds
            state.gate shouldBe gate
            state.leave shouldBe leave
        }
    }
})
