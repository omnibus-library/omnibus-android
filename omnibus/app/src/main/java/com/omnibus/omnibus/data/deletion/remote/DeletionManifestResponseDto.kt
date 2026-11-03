package com.omnibus.omnibus.data.deletion.remote

import com.omnibus.omnibus.data.deletion.DeletableCopy
import com.omnibus.omnibus.data.deletion.DeletableFile
import com.omnibus.omnibus.data.deletion.DeletionImpact
import com.omnibus.omnibus.data.deletion.DeletionManifest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The body of `GET api/books/{uuid}/deletion-manifest`. A missing list reads as empty, and missing counts read as zero.
 */
@Serializable
internal data class DeletionManifestResponseDto(
    @SerialName("files") val files: List<DeletionFileDto> = emptyList(),
    @SerialName("copies") val copies: List<DeletionCopyDto> = emptyList(),
    @SerialName("impact") val impact: DeletionImpactDto = DeletionImpactDto(),
) {
    /** This manifest as a domain manifest. */
    fun toDomain(): DeletionManifest = DeletionManifest(
        files = files.map { it.toDomain() },
        copies = copies.map { it.toDomain() },
        impact = impact.toDomain(),
    )
}

/** A file in the manifest. `size_bytes` is 0 when it is missing. */
@Serializable
internal data class DeletionFileDto(
    @SerialName("id") val id: Long,
    @SerialName("format") val format: String = "",
    @SerialName("filename") val filename: String = "",
    @SerialName("label") val label: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
) {
    fun toDomain(): DeletableFile = DeletableFile(
        id = id,
        format = format,
        filename = filename,
        label = label,
        sizeBytes = sizeBytes,
    )
}

/** A physical copy in the manifest. */
@Serializable
internal data class DeletionCopyDto(
    @SerialName("id") val id: Long,
    @SerialName("isbn") val isbn: String? = null,
    @SerialName("note") val note: String? = null,
) {
    fun toDomain(): DeletableCopy = DeletableCopy(id = id, isbn = isbn, note = note)
}

/** The counts of what deleting the whole book would remove. The server calls reviews `ratings`. */
@Serializable
internal data class DeletionImpactDto(
    @SerialName("highlights") val highlights: Long = 0,
    @SerialName("journal_entries") val journalEntries: Long = 0,
    @SerialName("bookmarks") val bookmarks: Long = 0,
    @SerialName("reading_sessions") val readingSessions: Long = 0,
    @SerialName("listening_sessions") val listeningSessions: Long = 0,
    @SerialName("ratings") val ratings: Long = 0,
    @SerialName("shelves") val shelves: Long = 0,
) {
    fun toDomain(): DeletionImpact = DeletionImpact(
        highlights = highlights,
        journalEntries = journalEntries,
        bookmarks = bookmarks,
        readingSessions = readingSessions,
        listeningSessions = listeningSessions,
        reviews = ratings,
        shelves = shelves,
    )
}
