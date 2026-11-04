package com.omnibus.omnibus.data.audiobook.playback.media3

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.audiobook.playback.AudioSource
import com.omnibus.omnibus.data.audiobook.playback.SourcePart
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.fail

/** How long a test waits for the player, the service or the main thread before it fails. */
internal const val WAIT_MILLIS = 15_000L

/** The silent parts the shared fixture plays: 40, 50 and 40 seconds, a 130-second book. */
internal val FIXTURE_PART_SECONDS = listOf(40.0, 50.0, 40.0)

/** Runs [block] on the main thread and returns its result, rethrowing its failure. */
internal fun <T> onMain(block: () -> T): T {
    var result: Result<T>? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
    return result!!.getOrThrow()
}

/**
 * Runs [block] as a coroutine on the main thread and waits for it from the test thread. Use it for suspend calls that
 * must run on the player's looper, such as `awaitReady`, where `runBlocking` on the test thread would touch the player
 * off the main thread.
 */
internal fun <T> awaitOnMain(block: suspend () -> T): T {
    val done = CountDownLatch(1)
    var result: Result<T>? = null
    CoroutineScope(Dispatchers.Main).launch {
        result = runCatching { block() }
        done.countDown()
    }
    check(done.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)) { "Timed out waiting on the main thread" }
    return result!!.getOrThrow()
}

/** Polls [condition] on the main thread until it holds, failing with [what] after [WAIT_MILLIS]. */
internal fun awaitUntil(what: String, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + WAIT_MILLIS
    while (!onMain(condition)) {
        if (System.currentTimeMillis() > deadline) fail("Timed out waiting for $what")
        Thread.sleep(25)
    }
}

/** True when [actual] is within [toleranceMillis] of [expected]. */
internal fun near(expected: Long, actual: Long, toleranceMillis: Long = 200): Boolean =
    abs(expected - actual) <= toleranceMillis

/** The application context, with Hilt's singletons reachable through the playback entry point. */
internal fun entryPoint(context: Context): AudiobookPlaybackEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, AudiobookPlaybackEntryPoint::class.java)

/** Silent parts of [seconds] in the cache directory, written once and reused, as `file://` parts. */
internal fun localParts(context: Context, name: String, seconds: List<Double>): List<SourcePart> =
    seconds.mapIndexed { index, length ->
        val file = File(context.cacheDir, "$name-part${index + 1}-$length.wav")
        if (!file.exists()) SilentWav.write(file, length)
        SourcePart(Uri.fromFile(file).toString(), (length * 1000).roundToLong(), "audio/wav")
    }

/**
 * Sets the local fixture book on [exo] through the production media source, and waits until it is ready. The source is
 * built with the factory's internal overload, because its `OkHttpDataSource` can't read `file://`.
 */
internal fun prepareLocalBook(
    context: Context,
    factory: AudiobookPlayerFactory,
    exo: ExoPlayer,
    seconds: List<Double> = FIXTURE_PART_SECONDS,
) {
    val source = AudioSource.Parts("book-1", localParts(context, "fixture", seconds), emptyMap())
    onMain {
        exo.setMediaSource(factory.mediaSource(source, DefaultDataSource.Factory(context)), /* resetPosition = */ true)
        exo.prepare()
    }
    awaitUntil("the local book to be ready") { exo.playbackState == Player.STATE_READY }
}

/** Connects a [MediaController] to the playback service, which starts it. [appController] adds the app's hint. */
internal fun connectController(context: Context, appController: Boolean): MediaController {
    val token = SessionToken(context, ComponentName(context, AudiobookPlaybackService::class.java))
    val builder = MediaController.Builder(context, token)
    if (appController) {
        builder.setConnectionHints(Bundle().apply { putBoolean(AudiobookPlaybackService.HINT_APP_CONTROLLER, true) })
    }
    return builder.buildAsync().get(WAIT_MILLIS, TimeUnit.MILLISECONDS)
}
