package com.omnibus.omnibus.data.audiobook.playback.media3

import android.content.Intent
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.audiobook.playback.RemotePressFilter
import com.omnibus.omnibus.data.audiobook.playback.RemoteTransport
import com.omnibus.omnibus.di.TimeSource
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The media-button route of the session callback (P-PLAY-6), driven through a real in-process session that carries the
 * callback under test. The controller is connected with the app's hint, as the app's own controller is.
 */
@OptIn(UnstableApi::class)
class AudiobookSessionCallbackTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val factory by lazy { entryPoint(context).audiobookPlayerFactory() }
    private var nowMillis = 0L
    private var player: ChapterForwardingPlayer? = null
    private var session: MediaSession? = null
    private var controller: MediaController? = null

    @After
    fun tearDown() {
        onMain {
            controller?.release()
            session?.release()
            player?.release()
        }
    }

    @Test
    fun `the six keys skip and play-pause is left to Media3 (P-PLAY-6)`() {
        // TC-100: the skip keys move the book, play-pause returns false for Media3, and only a key down acts.
        val exo: ExoPlayer = onMain { factory.createPlayer() }
        val forwarding = onMain {
            ChapterForwardingPlayer(exo, PlaybackServiceBridge(), RemotePressFilter(TimeSource { nowMillis })) {}
        }
        player = forwarding
        prepareLocalBook(context, factory, exo)

        val callback = AudiobookSessionCallback(forwarding)
        val testSession = onMain {
            MediaSession.Builder(context, forwarding)
                .setId("callback-test-${UUID.randomUUID()}")
                .setCallback(callback)
                .build()
        }
        session = testSession
        controller = MediaController.Builder(context, testSession.token).buildAsync().get(WAIT_MILLIS, TimeUnit.MILLISECONDS)
        awaitUntil("the test controller to connect") { testSession.connectedControllers.isNotEmpty() }
        val info = onMain { testSession.connectedControllers.first() }

        seekTo(exo, 10_000)

        fun press(action: Int, keyCode: Int): Boolean = onMain {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, keyCode))
            callback.onMediaButtonEvent(testSession, info, intent)
        }

        assertTrue(press(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
        awaitUntil("next to skip forward to 40 s") { near(40_000, exo.currentPosition) }

        nowMillis += 2_000
        assertTrue(press(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        awaitUntil("previous to skip back to 25 s") { near(25_000, exo.currentPosition) }

        nowMillis += 2_000
        assertTrue(press(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT))
        Thread.sleep(300)
        assertTrue("a key up moved the book", near(25_000, onMain { exo.currentPosition }))

        assertFalse(press(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertTrue("play-pause moved the book", near(25_000, onMain { exo.currentPosition }))

        assertEquals(KeyEvent.KEYCODE_MEDIA_NEXT, RemoteTransport.KEYCODE_MEDIA_NEXT)
        assertEquals(KeyEvent.KEYCODE_MEDIA_PREVIOUS, RemoteTransport.KEYCODE_MEDIA_PREVIOUS)
        assertEquals(KeyEvent.KEYCODE_MEDIA_REWIND, RemoteTransport.KEYCODE_MEDIA_REWIND)
        assertEquals(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, RemoteTransport.KEYCODE_MEDIA_FAST_FORWARD)
        assertEquals(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, RemoteTransport.KEYCODE_MEDIA_SKIP_FORWARD)
        assertEquals(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, RemoteTransport.KEYCODE_MEDIA_SKIP_BACKWARD)
        assertEquals(KeyEvent.ACTION_DOWN, RemoteTransport.ACTION_DOWN)
    }

    private fun seekTo(exo: ExoPlayer, bookMs: Long) {
        onMain { exo.seekTo(bookMs) }
        awaitUntil("the position to reach $bookMs") { near(bookMs, exo.currentPosition) }
    }
}
