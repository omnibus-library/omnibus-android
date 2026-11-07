package com.omnibus.omnibus.data.audiobook.playback.media3

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.audiobook.playback.Chapter
import com.omnibus.omnibus.data.audiobook.playback.NowPlaying
import com.omnibus.omnibus.data.audiobook.playback.NowPlayingImage
import com.omnibus.omnibus.data.audiobook.playback.RemotePressFilter
import com.omnibus.omnibus.di.TimeSource
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The chapter-relative session player over a real ExoPlayer (R16). The fixture book is 130 seconds in three parts, with
 * chapters at 0, 60 and 110 seconds, so chapter two crosses the part boundary at 40 seconds.
 */
class ChapterForwardingPlayerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val factory by lazy { entryPoint(context).audiobookPlayerFactory() }
    private val bridge = PlaybackServiceBridge()
    private var nowMillis = 0L
    private var playRequests = 0
    private lateinit var exo: ExoPlayer
    private lateinit var player: ChapterForwardingPlayer

    @Before
    fun setUp() {
        onMain {
            exo = factory.createPlayer()
            player = ChapterForwardingPlayer(exo, bridge, RemotePressFilter(TimeSource { nowMillis })) { playRequests++ }
            bridge.nowPlaying = nowPlaying()
        }
    }

    @After
    fun tearDown() {
        onMain { player.release() }
    }

    private fun loadBook() = prepareLocalBook(context, factory, exo)

    /** Seeks the raw player to [bookMs] in book time and waits until it lands there. */
    private fun seekBook(bookMs: Long) {
        onMain { exo.seekTo(bookMs) }
        awaitUntil("the position to reach $bookMs") { near(bookMs, exo.currentPosition) }
    }

    private fun nowPlaying(
        chapters: List<Chapter> = BOOK_CHAPTERS,
        chapterIndex: Int? = 1,
        bookTitle: String = "The Book",
        image: NowPlayingImage = NowPlayingImage.None,
    ) = NowPlaying(
        bookUuid = "book-1",
        bookTitle = bookTitle,
        author = "An Author",
        chapters = chapters,
        durationSeconds = 130.0,
        chapterIndex = chapterIndex,
        image = image,
    )

    // I-SYS-1
    @Test
    fun durationAndPositionAreTheSpanS() {
        // TC-95: chapter two is 50 s long, and the session reports its span, not the whole book.
        loadBook()
        seekBook(70_000)

        val values = onMain {
            listOf(player.duration, player.contentDuration, player.currentPosition, exo.currentPosition, player.bufferedPosition)
        }
        assertEquals(50_000L, values[0])
        assertEquals(50_000L, values[1])
        assertEquals(values[3] - 60_000L, values[2])
        assertTrue("buffered ${values[4]} is outside the span", values[4] in 0L..50_000L)
    }

    // I-TL-4
    @Test
    fun noChaptersSpansTheBook() {
        // TC-95: without chapters the session shows the whole book.
        loadBook()
        onMain { bridge.nowPlaying = nowPlaying(chapters = emptyList(), chapterIndex = null) }
        seekBook(70_000)

        val values = onMain { listOf(player.duration, exo.duration, player.currentPosition, exo.currentPosition) }
        assertEquals(values[1], values[0])
        assertEquals(values[3], values[2])
    }

    // I-SYS-1
    @Test
    fun mapsSpanSeeksBackToBookTime() {
        // TC-96: span seeks land in book time, inside the current chapter's span.
        loadBook()
        seekBook(70_000)

        onMain { player.seekTo(5_000) }
        awaitUntil("a span seek of 5 s to land at 65 s") { near(65_000, exo.currentPosition) }

        onMain { player.seekTo(0, 5_000) }
        awaitUntil("an indexed span seek of 5 s to land at 65 s") { near(65_000, exo.currentPosition) }
    }

    // I-SYS-1
    @Test
    fun clampsToTheSpan() {
        // TC-96: a span seek below zero stops at the chapter's start, and one past its end stops at its end.
        loadBook()
        seekBook(70_000)

        onMain { player.seekTo(-1_000) }
        awaitUntil("a seek before the span to stop at 60 s") { near(60_000, exo.currentPosition) }

        onMain { player.seekTo(99_000) }
        awaitUntil("a seek past the span to stop at 110 s") { near(110_000, exo.currentPosition) }
    }

    // I-SYS-2
    @Test
    fun showsTheChapterTitleElseTheBookTitle() {
        // TC-97: the chapter title, or the book title when the chapter has none or there are no chapters.
        onMain { bridge.nowPlaying = nowPlaying(chapterIndex = 1) }
        assertEquals("Two", onMain { player.mediaMetadata.title?.toString() })

        val blankTitle = BOOK_CHAPTERS.dropLast(1) + Chapter(3L, "", 110.0, 20.0)
        onMain { bridge.nowPlaying = nowPlaying(chapters = blankTitle, chapterIndex = 2) }
        assertEquals("The Book", onMain { player.mediaMetadata.title?.toString() })

        onMain { bridge.nowPlaying = nowPlaying(chapters = emptyList(), chapterIndex = null) }
        assertEquals("The Book", onMain { player.mediaMetadata.title?.toString() })
    }

    // I-SYS-2
    @Test
    fun artistAlbumAndArtwork() {
        // TC-97: the artist is the author, the album the book title, and the artwork the bytes it was given.
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        onMain { bridge.nowPlaying = nowPlaying(image = NowPlayingImage.Cover(bytes.toByteString())) }

        val metadata = onMain { player.mediaMetadata }
        assertEquals("An Author", metadata.artist?.toString())
        assertEquals("The Book", metadata.albumTitle?.toString())
        assertTrue(bytes.contentEquals(metadata.artworkData))
    }

    // P-PLAY-6
    @Test
    fun skipsMoveInBookTimeAndNeverChangeChapter() {
        // TC-99: each skip moves +30 s or -15 s in book time, from mid-book, near the start and near the end.
        loadBook()
        val cases = listOf(
            Skip("seekForward", 65_000, 95_000) { it.seekForward() },
            Skip("seekBack", 65_000, 50_000) { it.seekBack() },
            Skip("seekToNext", 65_000, 95_000) { it.seekToNext() },
            Skip("seekToPrevious", 65_000, 50_000) { it.seekToPrevious() },
            Skip("seekBack near the start", 5_000, 0) { it.seekBack() },
            Skip("seekForward near the end", 120_000, 130_000) { it.seekForward() },
        )
        for (case in cases) {
            seekBook(case.from)
            // Presses two seconds apart, so the press filter treats each as its own.
            nowMillis += 2_000
            onMain { case.act(player) }
            awaitUntil("${case.name} to move to ${case.to}") { near(case.to, exo.currentPosition) }
        }
    }

    private class Skip(val name: String, val from: Long, val to: Long, val act: (ChapterForwardingPlayer) -> Unit)

    // P-PLAY-2
    @Test
    fun hidesChapterAndTrackButtons() {
        // TC-101: the four previous and next commands are gone; play, seek and speed remain.
        loadBook()
        val available = onMain {
            listOf(
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            ).map { player.isCommandAvailable(it) } +
                listOf(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SET_SPEED_AND_PITCH,
                ).map { player.isCommandAvailable(it) }
        }
        assertEquals(listOf(false, false, false, false, true, true, true), available)
    }

    // I-SYS-5
    @Test
    fun withNothingLoadedAsksTheController() {
        // TC-102: play with nothing loaded asks the controller, and does not touch the player.
        onMain { player.play() }
        assertEquals(1, onMain { playRequests })
        assertFalse(onMain { exo.playWhenReady })
    }

    // I-SYS-5
    @Test
    fun withAPlayerErrorAsksTheController() {
        // TC-102: a raw player error sends play to the controller.
        onMain {
            exo.setMediaItem(MediaItem.fromUri("file:///nonexistent/silent-missing.wav"))
            exo.prepare()
        }
        awaitUntil("the player to report an error") { exo.playerError != null }

        onMain { player.play() }
        assertEquals(1, onMain { playRequests })
    }

    // I-SYS-5
    @Test
    fun playsTheLoadedItemWithoutAskingTheController() {
        // TC-102: with an item loaded and no error, play is ExoPlayer's.
        loadBook()
        onMain { player.play() }
        assertTrue(onMain { exo.playWhenReady })
        assertEquals(0, onMain { playRequests })
        onMain { player.pause() }
    }

    private companion object {
        val BOOK_CHAPTERS = listOf(
            Chapter(1L, "One", 0.0, 60.0),
            Chapter(2L, "Two", 60.0, 50.0),
            Chapter(3L, "Three", 110.0, 20.0),
        )
    }
}
