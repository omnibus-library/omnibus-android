package com.omnibus.omnibus.data.audiobook.playback

import okio.ByteString

/**
 * What the session shows for the loaded book: the titles, the span and the artwork. The controller hands it to
 * [AudioEngine.showNowPlaying]. Playback internals; not for UI use.
 */
data class NowPlaying(
    val bookUuid: String,
    val bookTitle: String,
    val author: String?,
    val chapters: List<Chapter>,
    val durationSeconds: Double,
    val chapterIndex: Int?,
    val image: NowPlayingImage,
)

/** The artwork the session shows. Playback internals; not for UI use. */
sealed interface NowPlayingImage {
    /** No artwork at all. */
    data object None : NowPlayingImage

    /** No cover: the service draws a generated plate (I-SYS-4). */
    data object Plate : NowPlayingImage

    /** An encoded cover image. [ByteString] equality keeps [NowPlaying] comparisons meaningful. */
    data class Cover(val bytes: ByteString) : NowPlayingImage
}
