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
    /** Sanitized HTML from the server. */
    val description: String? = null,
    val genres: List<String> = emptyList(),
    /** The book's tags. */
    val subjects: List<String> = emptyList(),
    val publisher: String? = null,
    /** The server's raw `published` string, such as "1925-04-10" or "8/4/2015". */
    val publishedDate: String? = null,
    /** Language code, as the server sends it. */
    val language: String? = null,
    val isbn13: String? = null,
    val seriesId: Long? = null,
    /** Not on the book record and not decoded, so it stays false until a wishlist source sets it (see the epic contract). */
    val isWishlisted: Boolean = false,
    /** Not on the book record and not decoded, so it stays null until a wishlist source sets it (see the epic contract). */
    val wishlistSource: String? = null,
) {

    /** Stable identity for routes and cover URLs. The numeric id is only a fallback for books without one. */
    val uuid: String get() = uniqueIdentifier ?: id.toString()

    /** The title, or the filename when the title is missing or blank. */
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: filename

    /** The first four-digit year in [publishedDate], as iOS `Book.year(in:)` computes it. */
    val year: Int? get() = publishedDate?.let(::yearIn)

    /** Wishlisted with no files, so the only thing to do with it is add it to the library. */
    val isWishlistOnly: Boolean get() = isWishlisted && bookFiles.isEmpty()

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

private val YearRun = Regex("(?<![0-9])[0-9]{4}(?![0-9])")

/** The first run of exactly four ASCII digits in [published], or null when there is none. */
internal fun yearIn(published: String): Int? = YearRun.find(published)?.value?.toInt()

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
