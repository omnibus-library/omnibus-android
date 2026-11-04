package com.omnibus.omnibus.data.audiobook.playback.media3

import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.audiobook.playback.AudioEngine
import com.omnibus.omnibus.data.audiobook.playback.AudioSource
import com.omnibus.omnibus.data.audiobook.playback.EngineEvent
import com.omnibus.omnibus.data.audiobook.playback.EngineSnapshot
import com.omnibus.omnibus.data.audiobook.playback.SourcePart
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The engine over the real service: connection, reconnection and the rules for external pauses (C2, I-HLTH-3). */
class Media3AudioEngineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val bridge by lazy { entryPoint(context).playbackServiceBridge() }
    private val factory by lazy { entryPoint(context).audiobookPlayerFactory() }
    private val recorder = Recorder()
    private val engines = mutableListOf<Media3AudioEngine>()

    @After
    fun tearDown() {
        onMain {
            engines.forEach { engine ->
                engine.setListener(null)
                engine.lost()
            }
        }
    }

    private fun newEngine(): Media3AudioEngine =
        Media3AudioEngine(context, bridge, factory).also { engines += it }

    /** Records what the engine reports. */
    private class Recorder : AudioEngine.Listener {
        val events = CopyOnWriteArrayList<EngineEvent>()

        override fun onEvent(event: EngineEvent) {
            events += event
        }

        override fun onSnapshot(snapshot: EngineSnapshot) = Unit

        fun has(event: EngineEvent): Boolean = events.contains(event)
    }

    @Test
    fun `connects on the first command (C2)`() {
        // TC-106: awaitReady connects the controller and waits for the service's player.
        val engine = newEngine()
        awaitOnMain { engine.awaitReady() }

        assertNotNull(bridge.player)
    }

    @Test
    fun `reconnects after the service dies (I-HLTH-3)`() {
        // TC-106: a lost connection reports Disconnected, and the next awaitReady reconnects to the service's player.
        // The disconnect is simulated by calling the engine's disconnect path, because a bound service can't be stopped
        // from inside the app.
        val engine = newEngine()
        engine.setListener(recorder)
        awaitOnMain { engine.awaitReady() }

        onMain { engine.lost() }
        assertTrue("no Disconnected event", recorder.has(EngineEvent.Disconnected))

        awaitOnMain { engine.awaitReady() }
        assertNotNull(bridge.player)
    }

    @Test
    fun `engine commands are not external pauses, but a pause from outside is (C2)`() {
        // TC-106: the engine's own source change and pause don't report PausedExternally, and a pause from another
        // controller (one without the app's hint) does.
        val engine = newEngine()
        engine.setListener(recorder)
        awaitOnMain { engine.awaitReady() }

        // An unreachable source: the engine's own setSource sets playWhenReady to false, and that is not external.
        val unreachable = AudioSource.Parts(
            "book-1",
            listOf(SourcePart("http://127.0.0.1:9/unreachable-part.wav", 1_000L, "audio/wav")),
            emptyMap(),
        )
        awaitOnMain { engine.setSource(unreachable) }
        Thread.sleep(1_000)
        assertFalse("setSource reported PausedExternally", recorder.has(EngineEvent.PausedExternally))

        onMain { engine.pause() }
        Thread.sleep(500)
        assertFalse("the engine's own pause reported PausedExternally", recorder.has(EngineEvent.PausedExternally))

        val outside = MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, AudiobookPlaybackService::class.java)),
        ).buildAsync().get(WAIT_MILLIS, TimeUnit.MILLISECONDS)
        try {
            onMain { outside.pause() }
            awaitUntil("a pause from outside the app to report PausedExternally") {
                recorder.has(EngineEvent.PausedExternally)
            }
        } finally {
            onMain { outside.release() }
        }
    }
}
