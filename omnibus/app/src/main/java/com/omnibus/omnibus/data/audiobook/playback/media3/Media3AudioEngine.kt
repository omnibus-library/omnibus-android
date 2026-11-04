package com.omnibus.omnibus.data.audiobook.playback.media3

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.omnibus.omnibus.data.audiobook.playback.AudioEngine
import com.omnibus.omnibus.data.audiobook.playback.AudioSource
import com.omnibus.omnibus.data.audiobook.playback.EngineEvent
import com.omnibus.omnibus.data.audiobook.playback.EngineSnapshot
import com.omnibus.omnibus.data.audiobook.playback.FailureKind
import com.omnibus.omnibus.data.audiobook.playback.NowPlaying
import com.omnibus.omnibus.data.audiobook.playback.toMillis
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * The [AudioEngine] over the service (AND-STK-1). Transport commands go through a [MediaController], which the session's
 * player handles. Source setup, book-time seeks and reads go to the service's ExoPlayer through [PlaybackServiceBridge].
 * Reports come from that ExoPlayer's listener, on the main thread.
 */
@OptIn(UnstableApi::class)
@Singleton
class Media3AudioEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridge: PlaybackServiceBridge,
    private val playerFactory: AudiobookPlayerFactory,
) : AudioEngine {

    private var listener: AudioEngine.Listener? = null
    private var controller: MediaController? = null
    private var observed: ExoPlayer? = null
    private var readyReported = false
    private var pendingExternalPause = false
    private val mainExecutor = ContextCompat.getMainExecutor(context)

    private val rawListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady &&
                (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY ||
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
            ) {
                pendingExternalPause = true
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            val out = mutableListOf<EngineEvent>()
            if (events.contains(Player.EVENT_PLAYER_ERROR)) {
                player.playerError?.let { out += EngineEvent.Failed(failureKindFor(it.errorCode)) }
            }
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                if (player.playbackState == Player.STATE_READY && !readyReported) {
                    readyReported = true
                    out += EngineEvent.Ready
                }
                if (player.playbackState == Player.STATE_ENDED) out += EngineEvent.Ended
            }
            if (events.contains(Player.EVENT_TRACKS_CHANGED)) {
                val tracks = player.currentTracks
                if (!tracks.isEmpty && !tracks.containsType(C.TRACK_TYPE_AUDIO)) out += EngineEvent.NoAudioTrack
            }
            if (pendingExternalPause) {
                pendingExternalPause = false
                out += EngineEvent.PausedExternally
            }
            out.forEach { listener?.onEvent(it) }
            listener?.onSnapshot(snapshotOf(player))
        }
    }

    override fun setListener(listener: AudioEngine.Listener?) {
        this.listener = listener
    }

    override suspend fun awaitReady() {
        if (controller?.isConnected != true) {
            controller = connect()
        }
        attach(withTimeout(CONNECT_TIMEOUT_MILLIS) { bridge.playerFlow.filterNotNull().first() })
    }

    /** Builds the controller and waits for its connection, which also starts the service. */
    private suspend fun connect(): MediaController {
        val token = SessionToken(context, ComponentName(context, AudiobookPlaybackService::class.java))
        val hints = Bundle().apply { putBoolean(AudiobookPlaybackService.HINT_APP_CONTROLLER, true) }
        val future = MediaController.Builder(context, token)
            .setConnectionHints(hints)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(mediaController: MediaController) = lost()
            })
            .buildAsync()
        return suspendCancellableCoroutine { cont ->
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: ExecutionException) {
                        cont.resumeWithException(e.cause ?: e)
                    }
                },
                mainExecutor,
            )
            cont.invokeOnCancellation { future.cancel(false) }
        }
    }

    private fun attach(player: ExoPlayer) {
        if (player === observed) return
        observed?.removeListener(rawListener)
        player.addListener(rawListener)
        observed = player
        bridge.onExternalPause = { listener?.onEvent(EngineEvent.PausedExternally) }
    }

    /** The service went away: drop the handles, so the next command reconnects. */
    private fun lost() {
        observed?.removeListener(rawListener)
        observed = null
        controller?.release()
        controller = null
        bridge.onExternalPause = null
        listener?.onEvent(EngineEvent.Disconnected)
    }

    override suspend fun setSource(source: AudioSource) {
        val player = checkNotNull(observed) { "The engine is not connected" }
        val media = playerFactory.mediaSource(source)
        readyReported = false
        player.setMediaSource(media, /* resetPosition = */ true)
        player.playWhenReady = false
        player.prepare()
    }

    override fun play() {
        controller?.play()
    }

    override fun pause() {
        controller?.pause()
    }

    override fun stop() {
        controller?.stop()
        observed?.clearMediaItems()
    }

    override fun seekTo(bookSeconds: Double) {
        observed?.seekTo(bookSeconds.toMillis())
    }

    override fun setSpeed(speed: Double) {
        controller?.setPlaybackSpeed(speed.toFloat())
    }

    override fun snapshot(): EngineSnapshot = observed?.let { snapshotOf(it) } ?: EngineSnapshot()

    override fun showNowPlaying(nowPlaying: NowPlaying?) {
        bridge.nowPlaying = nowPlaying
    }

    private fun snapshotOf(p: Player) = EngineSnapshot(
        positionSeconds = p.currentPosition / 1000.0,
        durationSeconds = p.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { it / 1000.0 },
        isPlaying = p.playWhenReady &&
            p.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
            (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING),
        isAudible = p.isPlaying,
        isBuffering = p.playWhenReady && p.playbackState == Player.STATE_BUFFERING,
        hasMedia = p.mediaItemCount > 0,
    )

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000L

        /** Errors that mean the data can't be read: they are never retried (R14). Everything else is a stream failure. */
        fun failureKindFor(code: Int): FailureKind = when (code) {
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            -> FailureKind.Unreadable
            else -> FailureKind.Stream
        }
    }
}
