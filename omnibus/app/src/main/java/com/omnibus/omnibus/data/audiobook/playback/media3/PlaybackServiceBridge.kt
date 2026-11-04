package com.omnibus.omnibus.data.audiobook.playback.media3

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.omnibus.omnibus.data.audiobook.playback.NowPlaying
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The in-process hand-off between the service and the engine (AND-STK-1). A `MediaController` can't carry a
 * `MediaSource`, and the session's player is chapter-relative, so the engine reaches the service's ExoPlayer through this
 * bridge for source setup, book-time seeks and reads. It also carries the now-playing titles and artwork back to the
 * session. Main thread only.
 */
@Singleton
class PlaybackServiceBridge @Inject constructor() {

    private val _player = MutableStateFlow<ExoPlayer?>(null)

    /** The service's ExoPlayer, or null while no service is running. */
    val playerFlow: StateFlow<ExoPlayer?> = _player.asStateFlow()

    /** The service's ExoPlayer, or null while no service is running. */
    val player: ExoPlayer? get() = _player.value

    /** The service's session, or null while no service is running. Read by instrumented tests. */
    var mediaSession: MediaSession? = null

    /** Publishes the service's ExoPlayer when it is created. */
    fun attach(player: ExoPlayer) {
        _player.value = player
    }

    /** Withdraws the ExoPlayer when the service is destroyed. */
    fun detach() {
        _player.value = null
    }

    private val nowPlayingListeners = mutableListOf<() -> Unit>()

    /** What the session shows. Setting an equal value does nothing. */
    var nowPlaying: NowPlaying? = null
        set(value) {
            if (value == field) return
            field = value
            nowPlayingListeners.toList().forEach { it() }
        }

    /** Calls [listener] whenever [nowPlaying] changes. */
    fun addNowPlayingListener(listener: () -> Unit) {
        nowPlayingListeners += listener
    }

    /** Stops calling [listener]. */
    fun removeNowPlayingListener(listener: () -> Unit) {
        nowPlayingListeners -= listener
    }

    /** Called when playback is paused from outside the app, such as a system control. */
    var onExternalPause: (() -> Unit)? = null

    /** Reports an external pause to the engine, if it is listening. */
    fun notifyExternalPause() {
        onExternalPause?.invoke()
    }
}
