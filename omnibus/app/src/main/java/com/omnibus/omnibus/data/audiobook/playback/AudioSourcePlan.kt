package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.audiobook.AudiobookManifest
import kotlin.math.roundToLong

/**
 * A part's placeholder length in milliseconds (I-LOAD-4): the manifest's seconds, rounded. Null unless the value is finite
 * and rounds to at least 1 ms.
 */
internal fun placeholderMillis(durationSeconds: Double): Long? =
    if (!durationSeconds.isFinite() || durationSeconds <= 0) {
        null
    } else {
        (durationSeconds * 1000).roundToLong().takeIf { it > 0 }
    }

/** Whether a manifest can be turned into a source, or why it can't. */
internal sealed interface SourcePlan {
    data class Ready(val source: AudioSource) : SourcePlan

    data class Failed(val error: PlaybackError) : SourcePlan
}

/**
 * Turns a manifest into the source the player plays (I-LOAD-3, I-LOAD-4). A `direct` book becomes its parts in ordinal
 * order, each with its placeholder length; an `hls` book becomes its playlist.
 */
internal fun planAudioSource(bookUuid: String, manifest: AudiobookManifest): SourcePlan = when (manifest) {
    is AudiobookManifest.Hls -> SourcePlan.Ready(AudioSource.Hls(bookUuid, manifest.playlistUrl, manifest.requestHeaders))
    is AudiobookManifest.Direct -> {
        if (manifest.parts.isEmpty()) {
            SourcePlan.Failed(PlaybackError.NoAudioParts)
        } else {
            val parts = manifest.parts.map { part ->
                val millis = placeholderMillis(part.durationSeconds)
                    ?: return SourcePlan.Failed(PlaybackError.TimelineFailed)
                SourcePart(part.url, millis, part.mime)
            }
            SourcePlan.Ready(AudioSource.Parts(bookUuid, parts, manifest.requestHeaders))
        }
    }
}
