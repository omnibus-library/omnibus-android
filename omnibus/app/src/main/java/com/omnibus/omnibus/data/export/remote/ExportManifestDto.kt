package com.omnibus.omnibus.data.export.remote

import kotlinx.serialization.Serializable

/** The answer to `GET api/audiobooks/{uuid}/manifest`. [mode] is `direct` (with [parts]) or `hls` (no parts). */
@Serializable
data class ExportManifestDto(
    val mode: String = "",
    val parts: List<ExportManifestPartDto> = emptyList(),
)

/** One audio part of an audiobook manifest. [ordinal] is the `part` query value that downloads it. */
@Serializable
data class ExportManifestPartDto(
    val ordinal: Long = 0,
)
