package com.omnibus.omnibus.data.audiobook

import com.omnibus.omnibus.data.audiobook.playback.Chapter

/** Reads the audiobook manifest, which says how a book's audio is split into parts and chapters (C14). */
interface AudiobookRepository {

    /**
     * Reads the manifest of [bookUuid]. Adds `file_id` only when [bookFileId] is set, and retries a 404 once without it
     * (I-LOAD-2). Never throws.
     */
    suspend fun getManifest(bookUuid: String, bookFileId: Long?): AudiobookManifestResult
}

/** A book's playable manifest, in one of the two modes the server sends (I-LOAD-3). */
sealed interface AudiobookManifest {

    /** The file the manifest is for: the server's `book_file_id`, else the requested id, else 0. */
    val bookFileId: Long

    /** Headers every audio fetch must send: `Authorization` (when signed in) and an `Accept` for any media type. */
    val requestHeaders: Map<String, String>

    /** The audio as separate parts, in book order, with the chapters across them. */
    data class Direct(
        override val bookFileId: Long,
        val parts: List<AudioPart>,
        val totalDurationSeconds: Double,
        val chapters: List<Chapter>,
        override val requestHeaders: Map<String, String>,
    ) : AudiobookManifest

    /** The audio as one HLS playlist. The server sends no chapters in this mode. */
    data class Hls(
        override val bookFileId: Long,
        val playlistUrl: String,
        override val requestHeaders: Map<String, String>,
    ) : AudiobookManifest
}

/** One part. [url] is absolute. */
data class AudioPart(val ordinal: Long, val url: String, val durationSeconds: Double, val mime: String)

/** The outcome of [AudiobookRepository.getManifest]. */
sealed interface AudiobookManifestResult {
    data class Success(val manifest: AudiobookManifest) : AudiobookManifestResult

    data class Failure(val error: AudiobookManifestError) : AudiobookManifestResult
}

/** Why a manifest could not be read. The controller maps each to a `PlaybackError`. */
sealed interface AudiobookManifestError {
    /** No server is saved, so no request was made. */
    data object NoServer : AudiobookManifestError

    /** The server rejected the session (401). */
    data object SessionExpired : AudiobookManifestError

    /** The server could not be reached. */
    data object Unreachable : AudiobookManifestError

    /** The response could not be decoded, or failed for another unexpected reason. */
    data object UnexpectedResponse : AudiobookManifestError

    /** The server answered with a non-success status. [serverMessage] is its own text, when it sent one. */
    data class Http(val code: Int, val serverMessage: String?) : AudiobookManifestError

    /** A `direct` manifest with no parts. */
    data object NoAudioParts : AudiobookManifestError

    /** An unknown mode, or an `hls` manifest with no playlist. */
    data object InvalidManifest : AudiobookManifestError
}

/**
 * The file to play (I-LOAD-1): the explicit one, else the saved position's. Null lets the server pick the book's first
 * audio file.
 */
internal fun chooseAudioFileId(explicit: Long?, saved: Long?): Long? = explicit ?: saved
