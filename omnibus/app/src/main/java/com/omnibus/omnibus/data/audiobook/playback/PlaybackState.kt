package com.omnibus.omnibus.data.audiobook.playback

/**
 * What the audiobook player shows and does. `PlaybackState()` is the idle state, with nothing loaded.
 */
data class PlaybackState(
    /** The loaded book, or null when nothing is loaded. Set as soon as a load starts (see [PlayingBook]). */
    val book: PlayingBook? = null,
    /** Sorted by start. Empty when the book has no chapters. */
    val chapters: List<Chapter> = emptyList(),
    /** Position in book time, in seconds. */
    val positionSeconds: Double = 0.0,
    /** Length of the whole book, in seconds. */
    val durationSeconds: Double = 0.0,
    /** The chapter at [positionSeconds], or null when there are no chapters. */
    val currentChapterIndex: Int? = null,
    val isPlaying: Boolean = false,
    /** True from the start of a load until the item is ready, or the load fails. */
    val isLoading: Boolean = false,
    /** True while the player waits for data during playback. */
    val isBuffering: Boolean = false,
    /** The playback speed, within [0.5, 3.0]. */
    val rate: Double = 1.0,
    /** Why playback stopped, or null when it has not failed. */
    val error: PlaybackError? = null,
    /** The sleep timer's state, mirrored from [SleepTimer.state]. Null when no timer is set. */
    val sleepTimer: SleepTimerState? = null,
)

/**
 * The loaded book.
 *
 * `hasEbook` is `Book.hasEpub`, not `Book.hasEbook`, because it gates the reader button and Sync: the EPUB reader must be
 * able to open the book.
 *
 * `bookFileId` is the file actually loaded. It is final once [PlaybackState.isLoading] is false. While a load is in
 * flight it is the explicit id, or `0`, and `title` is `""`.
 */
data class PlayingBook(
    val bookUuid: String,
    val bookFileId: Long,
    val title: String,
    val author: String?,
    val hasEbook: Boolean,
)

/**
 * One chapter of the loaded book, in book time.
 *
 * `durationSeconds` is the resolved length: the manifest's value, or the gap to the next start (or to the book's end)
 * when the manifest gives 0. Consumers never recompute it.
 */
data class Chapter(
    val ordinal: Long,
    val title: String,
    val startSeconds: Double,
    val durationSeconds: Double,
)

/** Why playback failed. UI copy for each case lives in the UI layer, not here. */
sealed interface PlaybackError {
    /** The manifest is a `direct` manifest with no parts. */
    data object NoAudioParts : PlaybackError

    /** The timeline can't be built: a part has no positive, finite duration, or the media source can't be built. */
    data object TimelineFailed : PlaybackError

    /** A part can't be read: no audio track, or a parse or decode error before the first ready state. Never retried. */
    data object PartUnreadable : PlaybackError

    /** The server failed the manifest request. [message] is the server's own message, when it sent one. */
    data class Server(val message: String?) : PlaybackError

    /** Playback failed four times in a row. [AudiobookController.play] refills the budget and retries. */
    data object StoppedAfterRetries : PlaybackError
}
