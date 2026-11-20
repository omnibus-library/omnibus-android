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
import com.omnibus.omnibus.testing.randomOf
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of

class AudioBookmarkRulesTest : DescribeSpec({

    fun syncCell(
        state: SyncHereUIState,
        syncHere: String = randomString(),
        syncing: String = randomString(),
        synced: String = randomString(),
        failed: String = randomString(),
        needsConnection: String = randomString(),
    ): PlayerToolButton? = syncHereButton(state, syncHere, syncing, synced, failed, needsConnection, onClick = {})

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
                isLoading = false,
            )

            audioBookmarkDraft(book.bookUuid, state) shouldBe BookmarkDraft.create(book.bookUuid, "125.4", title)
        }

        context("when the book has no chapters") {
            // TC6, C15: a book without chapters has no chapter title
            it("TC6: has no title") {
                val book = randomPlayingBook()
                val state = playbackState(book = book, chapters = emptyList(), positionSeconds = 90.0, isLoading = false)

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
            // TC-13, R7, AC3: the cell is hidden unless visible
            it("TC-13: is hidden") {
                syncCell(SyncHereUIState(visible = false, online = randomBoolean(), status = randomOf(SyncHereStatus.entries))).shouldBeNull()
            }
        }

        // TC-13, R1, R6, AC1, AC2: each status reads its own label
        it("TC-13: names the status") {
            val base = randomString()
            val idle = "$base-idle"
            val syncing = "$base-syncing"
            val synced = "$base-synced"
            val failed = "$base-failed"
            val labels = mapOf(
                SyncHereStatus.Idle to idle,
                SyncHereStatus.Syncing to syncing,
                SyncHereStatus.Synced to synced,
                SyncHereStatus.Failed to failed,
            )

            checkAll(Exhaustive.enum<SyncHereStatus>()) { status ->
                val cell = syncCell(
                    SyncHereUIState(visible = true, online = randomBoolean(), status = status),
                    syncHere = idle,
                    syncing = syncing,
                    synced = synced,
                    failed = failed,
                )

                cell.shouldNotBeNull().label shouldBe labels.getValue(status)
            }
        }

        // TC-14, R3, R8, AC1, AC7: enabled only online and not while syncing
        it("TC-14: is enabled only online and not syncing") {
            checkAll(Exhaustive.enum<SyncHereStatus>(), Exhaustive.boolean()) { status, online ->
                val cell = syncCell(SyncHereUIState(visible = true, online = online, status = status)).shouldNotBeNull()

                cell.enabled shouldBe (online && status != SyncHereStatus.Syncing)
            }
        }

        // TC-14, R8, P-A11Y-5, AC7: says why only while offline
        it("TC-14: speaks the reason only while offline") {
            checkAll(Exhaustive.enum<SyncHereStatus>(), Exhaustive.boolean()) { status, online ->
                val reason = randomString()

                val cell = syncCell(SyncHereUIState(visible = true, online = online, status = status), needsConnection = reason)
                    .shouldNotBeNull()

                cell.disabledDescription shouldBe reason.takeIf { !online }
            }
        }

        // TC-15, R6, AC2: only a failed cell is an error
        it("TC-15: is an error only when failed") {
            checkAll(Exhaustive.enum<SyncHereStatus>()) { status ->
                val cell = syncCell(SyncHereUIState(visible = true, online = randomBoolean(), status = status)).shouldNotBeNull()

                cell.isError shouldBe (status == SyncHereStatus.Failed)
            }
        }
    }
})
