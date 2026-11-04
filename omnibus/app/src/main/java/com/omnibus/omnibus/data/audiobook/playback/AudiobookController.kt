package com.omnibus.omnibus.data.audiobook.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * The audiobook player. One instance per app, shared by the player screen, the mini player and the media session.
 *
 * Every command runs on the main thread, never throws, and may be called before the media session connects. Commands
 * wait for the connection and then apply in call order.
 *
 * - A same-book [load] with `bookFileId` null or equal and `positionSeconds` null is a no-op that keeps playing, so
 *   opening the player from the mini player never reloads. With a position it seeks there. A failed item is rebuilt.
 * - A [load] of another book first awaits `PlaybackListener.onUnload(SwitchBook)` on every listener, then clears the
 *   sleep timer.
 * - [play] with [PlaybackError.StoppedAfterRetries] refills the rebuild budget and retries. [play] with nothing loaded
 *   resumes the most recent audiobook.
 * - [setRate] clamps to [0.5, 3.0].
 * - [stop] awaits `PlaybackListener.onUnload(Stop)` on every listener, then clears the sleep timer and the state.
 */
interface AudiobookController {

    /** The player's state. Read it, do not write it. */
    val state: StateFlow<PlaybackState>

    /**
     * Loads [bookUuid] and starts it at [positionSeconds], or at the saved position when that is null.
     *
     * @param bookFileId the file to play, or null to let the saved position or the server choose.
     * @param autoplay whether to start playing once the load settles.
     */
    fun load(bookUuid: String, bookFileId: Long?, positionSeconds: Double?, autoplay: Boolean)

    /** Starts playback. Retries a failed load or a stopped item. */
    fun play()

    /** Pauses playback. Also cancels an auto-start that is still owed by a load in flight. */
    fun pause()

    /** Plays when paused, pauses when playing. */
    fun togglePlayPause()

    /** Skips back 15 seconds in book time, clamped to the book. */
    fun skipBack()

    /** Skips forward 30 seconds in book time, clamped to the book. */
    fun skipForward()

    /** Goes to the start of the current chapter, or of the previous one when near its start. Does nothing without chapters. */
    fun previousChapter()

    /** Goes to the start of the next chapter. Does nothing on the last chapter or without chapters. */
    fun nextChapter()

    /** Seeks to [bookSeconds] in book time, clamped to the book. */
    fun seekTo(bookSeconds: Double)

    /**
     * Sets the playback speed, clamped to [0.5, 3.0]. Applies at once. A NaN or infinite [rate] is ignored.
     *
     * @param persist whether to save the rate for this book through the bound `PlaybackRateStore`.
     */
    fun setRate(rate: Double, persist: Boolean)

    /** Sets the sleep timer, or clears it with null. Does nothing when no `SleepTimer` is bound. */
    fun setSleepTimer(option: SleepTimerOption?)

    /** Stops playback and clears the loaded book. */
    fun stop()
}
