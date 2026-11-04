package com.omnibus.omnibus.data.audiobook.playback

/**
 * Observes playback. Bind implementations with `@Binds @IntoSet`. Every method has an empty default and runs on the main
 * thread.
 *
 * - [onTick] fires every 0.5 s while playing, and never while paused.
 * - [onAppBackground] comes from `ProcessLifecycleOwner`.
 * - [onUnload] is awaited by [AudiobookController.stop] and by a load of another book, for at most 2 seconds. A listener
 *   that is still running after that is abandoned, and the caller carries on.
 *
 * A listener that needs the controller injects `dagger.Lazy<AudiobookController>`, not the controller itself, to avoid a
 * dependency cycle.
 */
interface PlaybackListener {

    /** Fires once per load, after the start position is decided and before auto-start. [startSeconds] is that position. */
    fun onSettled(state: PlaybackState, startSeconds: Double) {}

    /** Fires every 0.5 s while playing. */
    fun onTick(state: PlaybackState) {}

    /** Fires when audio starts. */
    fun onPlay(state: PlaybackState) {}

    /** Fires when playback stops for any reason other than the end, an unload or a rebuild. */
    fun onPause(state: PlaybackState) {}

    /** Fires when the app goes to the background with a book loaded. */
    fun onAppBackground(state: PlaybackState) {}

    /** Fires when the item reaches its end. [state] has the position at the duration and `isPlaying == false`. */
    fun onEnded(state: PlaybackState) {}

    /** Fires before the loaded book is replaced or stopped, for [reason]. Suspends for at most 2 seconds. */
    suspend fun onUnload(state: PlaybackState, reason: UnloadReason) {}
}

/** Why the loaded book is being unloaded. */
enum class UnloadReason {
    /** The user stopped playback. */
    Stop,

    /** Another book is being loaded. */
    SwitchBook,
}
