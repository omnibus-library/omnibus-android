package com.omnibus.omnibus.feature.audiobook.bookmarks

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.noChapterState
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.playingBook
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.data.bookmarks.BookmarkDraft
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarkPlacement
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.bookmarkName
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.bookmarkRows
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.listeningSeconds
import com.omnibus.omnibus.feature.audiobook.player.PlayerToolButton
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private const val UUID = "book-1"

/** A bookmark of [UUID] at [position], dated 2025-10-28. */
private fun bookmark(
    id: Long?,
    position: String,
    title: String? = null,
    clientId: String? = null,
): Bookmark = Bookmark(
    id = id,
    clientId = clientId,
    bookUuid = UUID,
    position = position,
    title = title,
    chapterTitle = null,
    fraction = 0.47f,
    createdAtSeconds = 1_761_609_600L,
)

class AudioBookmarkRulesTest : DescribeSpec({

    describe("the position string") {

        // TC1, I-BMK-1: a plain decimal, at most three places, rounded down, never an exponent
        it("TC1: formats book time as a plain decimal rounded down to three places") {
            audioBookmarkPosition(0.0) shouldBe "0"
            audioBookmarkPosition(61.0) shouldBe "61"
            audioBookmarkPosition(61.5) shouldBe "61.5"
            audioBookmarkPosition(61.1234) shouldBe "61.123"
            audioBookmarkPosition(61.9999) shouldBe "61.999"
            audioBookmarkPosition(0.0004) shouldBe "0"
            audioBookmarkPosition(3600.0005) shouldBe "3600"
            audioBookmarkPosition(1.0E7) shouldBe "10000000"
            audioBookmarkPosition(1.0E7)!!.any { it == 'e' || it == 'E' } shouldBe false
        }

        // TC2, I-BMK-1: every output matches the plain-decimal shape with at most three decimals
        it("TC2: every output is a plain decimal with at most three places") {
            val shape = Regex("^[0-9]+(\\.[0-9]{1,3})?$")
            val samples = listOf(0.0, 0.5, 61.0, 61.1234, 999.9999, 3600.0005, 12.3456789, 1.0E-9, 7199.9999, 1.0E7, 123456.789)

            samples.forEach { sample ->
                val text = audioBookmarkPosition(sample)!!
                shape.matches(text) shouldBe true
            }
        }

        // TC3, AC5: the position reads back as the listening seconds it was made from, floored to three places
        it("TC3: reads back as the floored listening seconds") {
            listeningSeconds(audioBookmarkPosition(0.0)!!) shouldBe 0.0
            listeningSeconds(audioBookmarkPosition(12.3456)!!) shouldBe 12.345
            listeningSeconds(audioBookmarkPosition(7199.9999)!!) shouldBe 7199.999
        }

        // TC4: non-finite values have no position, negatives clamp to zero, and large values clamp to the listening maximum
        it("TC4: rejects non-finite values and clamps the range") {
            audioBookmarkPosition(Double.NaN).shouldBeNull()
            audioBookmarkPosition(Double.POSITIVE_INFINITY).shouldBeNull()
            audioBookmarkPosition(Double.NEGATIVE_INFINITY).shouldBeNull()
            audioBookmarkPosition(-5.0) shouldBe "0"
            audioBookmarkPosition(2.0E9) shouldBe "1000000000"
        }
    }

    describe("the draft") {

        // TC5, R2, I-BMK-2: a loaded book at 125.4 seconds in chapter 3 names the draft after that chapter
        it("TC5: drafts a bookmark at the position, titled by the current chapter") {
            val state = playbackState(chapters = chapters(50.0, 50.0, 500.0), positionSeconds = 125.4)

            audioBookmarkDraft(UUID, state) shouldBe BookmarkDraft.create(UUID, "125.4", "Chapter 3")
        }

        // TC6, C15: a book without chapters has no chapter title
        it("TC6: a book with no chapters is drafted without a title") {
            audioBookmarkDraft(UUID, noChapterState(positionSeconds = 90.0)) shouldBe
                BookmarkDraft.create(UUID, "90", null)
        }

        // TC7: the chapter title is trimmed by BookmarkDraft, and a blank one becomes no title
        it("TC7: trims the chapter title and drops a blank one") {
            val spaced = playbackState(chapters = chapters(600.0, titles = listOf("  Chapter Three  ")))
            val blank = playbackState(chapters = chapters(600.0, titles = listOf("   ")))

            audioBookmarkDraft(UUID, spaced)?.title shouldBe "Chapter Three"
            audioBookmarkDraft(UUID, blank)?.title.shouldBeNull()
        }

        // TC8, R1, R2: no draft for another book, no book, a loading book, or a non-finite position
        it("TC8: no draft for another book, no book, a loading book or a NaN position") {
            audioBookmarkDraft("other-book", playbackState()).shouldBeNull()
            audioBookmarkDraft(UUID, PlaybackState()).shouldBeNull()
            audioBookmarkDraft(UUID, playbackState(isLoading = true)).shouldBeNull()
            audioBookmarkDraft(UUID, PlaybackState(book = playingBook(), positionSeconds = Double.NaN)).shouldBeNull()
        }
    }

    describe("the sheet's rows") {

        // TC9, R4, P-BMK-2: every row is date-only, and the model keeps the repository's keys, order and dates
        it("TC9: every row has no placement and keeps the book detail row model") {
            val bookmarks = listOf(
                bookmark(1, "3725"),
                bookmark(2, "epubcfi(/6/4!/4/2)"),
                bookmark(3, "page 12"),
                bookmark(4, "epubcfi(/6/8!/4/2)", title = "A note"),
                bookmark(5, ""),
            )

            val rows = playerBookmarkRows(UUID, bookmarks)
            val expected = bookmarkRows(UUID, bookmarks, chapters = null)

            rows.all { it.placement == BookmarkPlacement(null, null) } shouldBe true
            rows[0].listeningSeconds shouldBe 3725.0
            rows.drop(1).all { it.listeningSeconds == null } shouldBe true
            rows.map { it.key } shouldBe expected.map { it.key }
            rows.map { it.createdAtMillis } shouldBe expected.map { it.createdAtMillis }
        }

        // TC10, R4, AC5: the name is the trimmed title, else the listening time, else "Saved position"
        it("TC10: names each row by its title, its listening time, or Saved position") {
            val rows = playerBookmarkRows(
                UUID,
                listOf(
                    bookmark(1, "3725", title = "  Fav  "),
                    bookmark(2, "3725"),
                    bookmark(3, "epubcfi(/6/4!/4/2)"),
                ),
            )

            bookmarkName(rows[0].bookmark) shouldBe "Fav"
            bookmarkName(rows[1].bookmark) shouldBe "Listening, 1 h 02 m in"
            bookmarkName(rows[2].bookmark) shouldBe "Saved position"
        }

        // TC11, R4: rows keep the repository's order, optimistic rows included
        it("TC11: keeps the repository order, including an optimistic row") {
            val bookmarks = listOf(bookmark(1, "10"), bookmark(null, "20", clientId = "client-1"), bookmark(2, "30"))

            val rows = playerBookmarkRows(UUID, bookmarks)

            rows.map { it.key } shouldBe listOf("id:1", "client-1", "id:2")
            rows.map { it.bookmark } shouldBe bookmarks
        }
    }

    describe("a tap on a row") {

        // TC12, R5, AC2: audio seeks; a CFI opens the reader at it (trimmed); anything else opens the saved place
        it("TC12: routes audio to a seek and text to the reader at its CFI or saved place") {
            val rows = playerBookmarkRows(
                UUID,
                listOf(
                    bookmark(1, "3725"),
                    bookmark(2, "  epubcfi(/6/4!/4/2)  "),
                    bookmark(3, "page 12"),
                    bookmark(4, ""),
                ),
            )

            playerBookmarkTap(rows[0]) shouldBe PlayerBookmarkTap.Seek(3725.0)
            playerBookmarkTap(rows[1]) shouldBe PlayerBookmarkTap.OpenReader("epubcfi(/6/4!/4/2)")
            playerBookmarkTap(rows[2]) shouldBe PlayerBookmarkTap.OpenReader(null)
            playerBookmarkTap(rows[3]) shouldBe PlayerBookmarkTap.OpenReader(null)
        }
    }

    describe("the Sync here cell") {

        // TC13, R7, R8, R9, R10, AC3, AC5: the cell is hidden unless visible, reads synced, and is disabled with a reason offline
        it("TC13: follows the visible, online and synced flags") {
            fun cell(state: SyncHereUIState): PlayerToolButton? = syncHereButton(
                state = state,
                syncHereLabel = "Sync here",
                syncedLabel = "Synced ✓",
                needsConnection = "Needs a connection",
                onClick = {},
            )

            cell(SyncHereUIState(visible = false, online = true, synced = false)).shouldBeNull()
            cell(SyncHereUIState(visible = false, online = false, synced = true)).shouldBeNull()

            val ready = cell(SyncHereUIState(visible = true, online = true, synced = false))!!
            ready.label shouldBe "Sync here"
            ready.enabled shouldBe true
            ready.disabledDescription shouldBe null

            val offline = cell(SyncHereUIState(visible = true, online = false, synced = false))!!
            offline.label shouldBe "Sync here"
            offline.enabled shouldBe false
            offline.disabledDescription shouldBe "Needs a connection"

            val synced = cell(SyncHereUIState(visible = true, online = true, synced = true))!!
            synced.label shouldBe "Synced ✓"
            synced.enabled shouldBe true

            val syncedOffline = cell(SyncHereUIState(visible = true, online = false, synced = true))!!
            syncedOffline.label shouldBe "Synced ✓"
            syncedOffline.enabled shouldBe false
            syncedOffline.disabledDescription shouldBe "Needs a connection"
        }

        // TC14, R8: the cell's click is the lambda it was given, called once per click
        it("TC14: the click invokes the given lambda once") {
            var clicks = 0
            val cell = syncHereButton(
                state = SyncHereUIState(visible = true),
                syncHereLabel = "Sync here",
                syncedLabel = "Synced ✓",
                needsConnection = "Needs a connection",
                onClick = { clicks++ },
            )!!

            cell.onClick()

            clicks shouldBe 1
        }
    }
})
