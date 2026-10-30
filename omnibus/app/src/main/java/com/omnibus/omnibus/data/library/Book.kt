package com.omnibus.omnibus.data.library

/**
 * A book in the library. The derived properties apply the display fallbacks once, so screens
 * never repeat them.
 */
data class Book(
    val id: Long,
    val uniqueIdentifier: String?,
    val filename: String,
    val title: String?,
    val creators: List<Creator>,
    val coverUrl: String?,
    val accent: String?,
    val formats: List<String>,
    val series: String?,
    /** Kept as the server's string, e.g. "1.5", because series numbers are not always whole. */
    val seriesIndex: String?,
    val hasPhysical: Boolean,
    /** ISO-8601 timestamp as sent by the server. */
    val addedAt: String?,
    /** ISO-8601 timestamp as sent by the server. */
    val lastInteractedAt: String?,
    val bookFiles: List<BookFile>,
) {

    /** Stable identity for routes and cover URLs. The numeric id is only a fallback for books without one. */
    val uuid: String get() = uniqueIdentifier ?: id.toString()

    /** The title, or the filename when the title is missing or blank. */
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: filename

    /** Creator names joined with commas, skipping blank names. */
    val authorDisplay: String
        get() = creators.map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
            .ifEmpty { UNKNOWN_AUTHOR }

    /** Whether any format is an ebook format. Format names are compared case-insensitively. */
    val hasEbook: Boolean get() = formats.any { it.lowercase() in EBOOK_FORMATS }

    /**
     * Whether the book can be read in the EPUB reader, from its formats or its files. Unlike
     * [hasEbook], pdf and cbz don't count. Format names are compared case-insensitively.
     */
    val hasEpub: Boolean
        get() = formats.any { it.equals(EPUB_FORMAT, ignoreCase = true) } ||
            bookFiles.any { it.format.equals(EPUB_FORMAT, ignoreCase = true) }

    /** Whether any format is an audiobook format. Format names are compared case-insensitively. */
    val hasAudiobook: Boolean get() = formats.any { it.lowercase() in AUDIO_FORMATS }

    private companion object {
        const val UNKNOWN_AUTHOR = "Unknown author"
        const val EPUB_FORMAT = "epub"

        val EBOOK_FORMATS = setOf("epub", "kepub", "pdf", "mobi", "azw3", "cbz", "cbr")
        val AUDIO_FORMATS = setOf("m4b", "m4a", "mp3", "aac", "flac", "ogg", "opus", "wav")
    }
}

/** A person credited on a [Book], such as an author or narrator. */
data class Creator(
    val name: String,
    val role: String?,
    val fileAs: String?,
    val id: Long?,
)

/** One file of a [Book], such as an EPUB or an audio part. */
data class BookFile(
    val id: Long,
    val format: String,
    val filename: String,
    val ordinal: Int,
    val label: String?,
    val sizeBytes: Long,
    val etag: String?,
)
