package com.omnibus.omnibus.data.export

import com.omnibus.omnibus.data.export.remote.ExportManifestDto

/**
 * One request an audiobook download makes.
 *
 * @property partOrdinal The `part` query value, or null for the whole book.
 * @property partNumber The 1-based position used in the saved name, or null when the file is not a part.
 */
data class AudiobookDownload(val partOrdinal: Long?, val partNumber: Int?)

/**
 * The requests an audiobook needs: one per part in manifest order for a direct book with several parts,
 * otherwise a single request for the whole book.
 */
internal fun audiobookDownloads(manifest: ExportManifestDto): List<AudiobookDownload> =
    if (manifest.mode == DIRECT_MODE && manifest.parts.size > 1) {
        manifest.parts.mapIndexed { index, part -> AudiobookDownload(part.ordinal, index + 1) }
    } else {
        listOf(AudiobookDownload(null, null))
    }

private const val DIRECT_MODE = "direct"
