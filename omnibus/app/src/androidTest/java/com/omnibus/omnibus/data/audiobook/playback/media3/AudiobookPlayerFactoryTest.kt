package com.omnibus.omnibus.data.audiobook.playback.media3

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.audiobook.playback.AudioSource
import com.omnibus.omnibus.data.audiobook.playback.audioCallFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The player and the sources it plays, as the service builds them (R15). */
@OptIn(UnstableApi::class)
class AudiobookPlayerFactoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val factory by lazy { entryPoint(context).audiobookPlayerFactory() }

    // I-LOAD-4
    @Test
    fun aBookSecondLandsInTheRightPart() {
        // TC-111: three parts of 2, 3 and 2 seconds read as one 7-second book, and playback crosses into part two
        // without a seek.
        val source = AudioSource.Parts("book-1", localParts(context, "factory", listOf(2.0, 3.0, 2.0)), emptyMap())
        val exo: ExoPlayer = onMain { factory.createPlayer() }
        try {
            onMain {
                exo.setMediaSource(factory.mediaSource(source, DefaultDataSource.Factory(context)), true)
                exo.prepare()
            }
            awaitUntil("the parts to be ready") { exo.playbackState == Player.STATE_READY }
            assertTrue("duration ${onMain { exo.duration }} is not about 7 s", abs(onMain { exo.duration } - 7_000) <= 100)

            onMain { exo.seekTo(2_500) }
            awaitUntil("a seek to 2.5 s") { near(2_500, exo.currentPosition, 100) }

            onMain { exo.seekTo(1_800) }
            awaitUntil("a seek to 1.8 s") { near(1_800, exo.currentPosition, 100) }
            awaitUntil("the parts to be ready after the seek") { exo.playbackState == Player.STATE_READY }

            val reasons = CopyOnWriteArrayList<Int>()
            onMain {
                exo.addListener(object : Player.Listener {
                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int,
                    ) {
                        reasons += reason
                    }
                })
                exo.playWhenReady = true
            }
            awaitUntil("playback to pass 2.6 s") { exo.currentPosition >= 2_600 }
            onMain { exo.playWhenReady = false }

            assertFalse("playback crossed a part with a seek", reasons.contains(Player.DISCONTINUITY_REASON_SEEK))
        } finally {
            onMain { exo.release() }
        }
    }

    // I-LOAD-3
    @Test
    fun hlsBuildsAnHlsMediaSource() {
        // TC-111: an hls book becomes one HLS media source.
        val source = onMain {
            factory.mediaSource(
                AudioSource.Hls("book-1", "https://server.test/api/audiobooks/book-1/playlist.m3u8", emptyMap()),
            )
        }
        assertTrue("the hls source is ${source.javaClass.simpleName}", source is HlsMediaSource)
    }

    // AND-STK-3
    @Test
    fun okHttpDataSourceRunsOnTheAppSOkHttp5Client() {
        // TC-112: the data source runs on the app's client with the source's headers. Cleartext HTTP to a loopback
        // server is refused by the app's network policy (there is no network security config), so the read may fail
        // with an IOException. A linkage failure (NoSuchMethodError, AbstractMethodError) is an Error and fails here.
        val client = entryPoint(context).omnibusHttpClient()
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        server.soTimeout = WAIT_MILLIS.toInt()
        val requestLines = CopyOnWriteArrayList<String>()
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
                    generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.forEach { requestLines += it }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 64\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                        write(ByteArray(64))
                        flush()
                    }
                }
            }
        }

        val dataSource = OkHttpDataSource.Factory(audioCallFactory(client))
            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer secret", "Accept" to "*/*"))
            .createDataSource()
        try {
            val spec = DataSpec(Uri.parse("http://127.0.0.1:${server.localPort}/api/audiobooks/book-1/parts/1"))
            val outcome = runCatching {
                dataSource.open(spec)
                val buffer = ByteArray(64)
                var read = 0
                while (read < buffer.size) {
                    val count = dataSource.read(buffer, read, buffer.size - read)
                    if (count == C.RESULT_END_OF_INPUT) break
                    read += count
                }
                read
            }
            val failure = outcome.exceptionOrNull()
            if (failure == null) {
                assertEquals(64, outcome.getOrThrow())
                assertTrue(requestLines.any { it.equals("Authorization: Bearer secret", ignoreCase = true) })
                assertTrue(requestLines.any { it.equals("Accept: */*", ignoreCase = true) })
            } else {
                assertTrue("OkHttp failed with ${failure.javaClass.name}, not an IOException", failure is IOException)
            }
        } finally {
            dataSource.close()
            server.close()
        }
    }
}
