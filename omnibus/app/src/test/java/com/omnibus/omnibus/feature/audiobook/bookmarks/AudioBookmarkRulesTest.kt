package com.omnibus.omnibus.feature.audiobook.bookmarks

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlaybackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import com.omnibus.omnibus.data.bookmarks.BookmarkDraft
import com.omnibus.omnibus.data.bookmarks.randomBookmark
import com.omnibus.omnibus.feature.audiobook.player.PlayerToolButton
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarkPlacement
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class AudioBookmarkRulesTest : DescribeSpec({

    fun syncCell(
        state: SyncHereUIState,
        syncHere: String = randomString(),
        synced: String = randomString(),
        needsConnection: String = randomString(),
    ): PlayerToolButton? = syncHereButton(state, syncHere, synced, needsConnection, onClick = {})

    fun row(position: String) = playerBookmarkRows(
        randomString(),
        listOf(randomBookmark().copy(position = position)),
    ).single()

    describe("the position string") {
        // TC1, TC2, TC4, I-BMK-1: a plain decimal, rounded down to at most three places, clamped to the listening range
        it("TC1 TC2 TC4: is a plain decimal rounded down to three places, clamped to the listening range") {
            checkAll(
                Exhaustive.of(
                    61.0 to "61",
                    61.5 to "61.5",
                    61.9999 to "61.999",
                    0.0004 to "0",
                    1.0E7 to "10000000",
                    -5.0 to "0",
                    2.0E9 to "1000000000",
                ),
            ) { (seconds, expected) ->
                audioBookmarkPosition(seconds) shouldBe expected
            }
        }

        // TC4: non-finite values have no position
        it("TC4: is null for NaN or an infinity") {
            checkAll(Exhaustive.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) { seconds ->
                audioBookmarkPosition(seconds).shouldBeNull()
            }
        }
    }

    describe("the draft") {
        // TC5, R2, I-BMK-2: a loaded book names the draft after the current chapter
        it("TC5: is at the position, titled by the current chapter") {
            val book = randomPlayingBook()
            val title = randomString()
            val state = playbackState(
                book = book,
                chapters = chapters(50.0, 500.0, titles = listOf(randomString(), title)),
                positionSeconds = 125.4,
            )

            audioBookmarkDraft(book.bookUuid, state) shouldBe BookmarkDraft.create(book.bookUuid, "125.4", title)
        }

        context("when the book has no chapters") {
            // TC6, C15: a book without chapters has no chapter title
            it("TC6: has no title") {
                val book = randomPlayingBook()
                val state = playbackState(book = book, chapters = emptyList(), positionSeconds = 90.0)

                audioBookmarkDraft(book.bookUuid, state) shouldBe BookmarkDraft.create(book.bookUuid, "90", null)
            }
        }

        // TC8, R1, R2: no draft for another book, no book, a loading book, or a non-finite position
        it("TC8: is null for another book, no book, a load in flight or a NaN position") {
            val book = randomPlayingBook()
            val loaded = randomPlaybackState().copy(book = book, isLoading = false)

            checkAll(
                Exhaustive.of(
                    loaded.copy(book = book.copy(bookUuid = "${book.bookUuid}-other")),
                    PlaybackState(),
                    loaded.copy(isLoading = true),
                    loaded.copy(positionSeconds = Double.NaN),
                ),
            ) { state ->
                audioBookmarkDraft(book.bookUuid, state).shouldBeNull()
            }
        }
    }

    describe("the sheet's rows") {
        // TC9, R4, P-BMK-2: every row is date-only
        it("TC9: have no placement") {
            val rows = playerBookmarkRows(randomString(), listOf(randomBookmark(), randomBookmark()))

            rows.map { it.placement } shouldBe List(2) { BookmarkPlacement(null, null) }
        }
    }

    describe("a tap on a row") {
        // TC12, R5, AC2: audio seeks, and a CFI opens the reader at it
        it("TC12: seeks for an audio position, opening the reader for a text one") {
            val cfi = "epubcfi(/6/4!/4/2)"

            playerBookmarkTap(row("3725")) shouldBe PlayerBookmarkTap.Seek(3725.0)
            playerBookmarkTap(row(cfi)) shouldBe PlayerBookmarkTap.OpenReader(cfi)
        }
    }

    describe("the Sync here cell") {
        context("when it is not visible") {
            // TC13, R7, AC3: the cell is hidden unless visible
            it("TC13: is hidden") {
                syncCell(SyncHereUIState(visible = false, online = randomBoolean(), synced = randomBoolean())).shouldBeNull()
            }
        }

        // TC13, R8, R10, AC4: enabled online, disabled with a reason offline
        it("TC13: is enabled online, disabled with a reason offline") {
            val reason = randomString()
            val state = SyncHereUIState(visible = true, synced = randomBoolean())

            val online = syncCell(state.copy(online = true), needsConnection = reason).shouldNotBeNull()
            val offline = syncCell(state.copy(online = false), needsConnection = reason).shouldNotBeNull()

            online.enabled shouldBe true
            online.disabledDescription.shouldBeNull()
            offline.enabled shouldBe false
            offline.disabledDescription shouldBe reason
        }

        // TC13, R9, AC3, AC5: the label reads synced after a declaration
        it("TC13: reads synced once synced, else Sync here") {
            val syncHere = randomString()
            val synced = "$syncHere-synced"

            checkAll(Exhaustive.of(true to synced, false to syncHere)) { (isSynced, expected) ->
                val cell = syncCell(SyncHereUIState(visible = true, online = randomBoolean(), synced = isSynced), syncHere, synced)

                cell.shouldNotBeNull().label shouldBe expected
            }
        }
    }
})
