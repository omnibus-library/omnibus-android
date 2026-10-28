package com.omnibus.omnibus.data.library.remote

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookFile
import com.omnibus.omnibus.data.library.Creator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class EbooksPageResponseDto(
    val path: String? = null,
    val books: List<BookDto> = emptyList(),
    val error: String? = null,
    val total: Long? = null,
)

@Serializable
internal data class BookDto(
    val id: Long,
    @SerialName("unique_identifier")
    val uniqueIdentifier: String? = null,
    val filename: String = "",
    val title: String? = null,
    val creators: List<CreatorDto> = emptyList(),
    @SerialName("cover_url")
    val coverUrl: String? = null,
    val accent: String? = null,
    val formats: List<String> = emptyList(),
    val series: String? = null,
    @SerialName("series_index")
    val seriesIndex: String? = null,
    @SerialName("has_physical")
    val hasPhysical: Boolean = false,
    @SerialName("added_at")
    val addedAt: String? = null,
    @SerialName("last_interacted_at")
    val lastInteractedAt: String? = null,
    @SerialName("book_files")
    val bookFiles: List<BookFileDto> = emptyList(),
) {
    fun toDomain(): Book = Book(
        id = id,
        uniqueIdentifier = uniqueIdentifier,
        filename = filename,
        title = title,
        creators = creators.map { it.toDomain() },
        coverUrl = coverUrl,
        accent = accent,
        formats = formats,
        series = series,
        seriesIndex = seriesIndex,
        hasPhysical = hasPhysical,
        addedAt = addedAt,
        lastInteractedAt = lastInteractedAt,
        bookFiles = bookFiles.map { it.toDomain() },
    )
}

@Serializable
internal data class CreatorDto(
    val name: String = "",
    val role: String? = null,
    @SerialName("file_as")
    val fileAs: String? = null,
    val id: Long? = null,
) {
    fun toDomain(): Creator = Creator(name = name, role = role, fileAs = fileAs, id = id)
}

@Serializable
internal data class BookFileDto(
    val id: Long,
    val format: String = "",
    val filename: String = "",
    val ordinal: Int = 0,
    val label: String? = null,
    @SerialName("size_bytes")
    val sizeBytes: Long = 0,
    val etag: String? = null,
) {
    fun toDomain(): BookFile = BookFile(
        id = id,
        format = format,
        filename = filename,
        ordinal = ordinal,
        label = label,
        sizeBytes = sizeBytes,
        etag = etag,
    )
}
