package com.omnibus.omnibus.data.audiobook.playback

/**
 * The player, as the controller sees it: book time only, main thread only. Playback internals; not for UI use.
 */
interface AudioEngine {

    /** Sets where engine events and snapshots go, or stops them with null. */
    fun setListener(listener: Listener?)

    /** Connects to the service and returns once commands can run. Throws if the service can't be reached. */
    suspend fun awaitReady()

    /** Replaces the media and prepares it, paused. Throws if the source can't be built. */
    suspend fun setSource(source: AudioSource)

    /** Starts playback. */
    fun play()

    /** Pauses playback. */
    fun pause()

    /** Stops playback and clears the media. */
    fun stop()

    /** Seeks to [bookSeconds] in book time. */
    fun seekTo(bookSeconds: Double)

    /** Sets the speed, with the pitch untouched. */
    fun setSpeed(speed: Double)

    /** The player's current state, in book time. */
    fun snapshot(): EngineSnapshot

    /** Gives the session what it shows: titles, span and artwork. Null clears it. */
    fun showNowPlaying(nowPlaying: NowPlaying?)

    /** Receives what the engine reports. Both calls run on the main thread. */
    interface Listener {
        /** Something happened that the controller must act on. */
        fun onEvent(event: EngineEvent)

        /** The player's state changed. */
        fun onSnapshot(snapshot: EngineSnapshot)
    }
}

/** What the player plays: one source for a `direct` book, one playlist for `hls`. Playback internals; not for UI use. */
sealed interface AudioSource {
    /** The book's uuid, which the session uses as the media id. */
    val mediaId: String

    /** Headers every fetch sends. */
    val headers: Map<String, String>

    /** The book as separate parts, each with a placeholder length until its own length is known. */
    data class Parts(
        override val mediaId: String,
        val parts: List<SourcePart>,
        override val headers: Map<String, String>,
    ) : AudioSource

    /** The book as one HLS playlist. */
    data class Hls(
        override val mediaId: String,
        val url: String,
        override val headers: Map<String, String>,
    ) : AudioSource
}

/** One part of a [AudioSource.Parts] source. Playback internals; not for UI use. */
data class SourcePart(val url: String, val placeholderMillis: Long, val mime: String)

/**
 * What the player is doing right now. Playback internals; not for UI use.
 *
 * @property isPlaying playWhenReady, not suppressed, and buffering or ready (the listener's intent)
 * @property isAudible audio is actually rendering (Player.isPlaying)
 */
data class EngineSnapshot(
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double? = null,
    val isPlaying: Boolean = false,
    val isAudible: Boolean = false,
    val isBuffering: Boolean = false,
    val hasMedia: Boolean = false,
)

/** A change the controller acts on. Playback internals; not for UI use. */
sealed interface EngineEvent {
    /** The current source reached STATE_READY for the first time. */
    data object Ready : EngineEvent

    /** The player failed, for [kind]. */
    data class Failed(val kind: FailureKind) : EngineEvent

    /** The source has no audio track. */
    data object NoAudioTrack : EngineEvent

    /** The item reached its end. */
    data object Ended : EngineEvent

    /** Paused by something other than this app: a system control, a headset, becoming noisy, or losing focus. */
    data object PausedExternally : EngineEvent

    /** The service went away. */
    data object Disconnected : EngineEvent
}

/** Why the player failed. Playback internals; not for UI use. */
enum class FailureKind {
    /** The data can't be parsed or decoded. */
    Unreadable,

    /** The stream failed to load. */
    Stream,
}
