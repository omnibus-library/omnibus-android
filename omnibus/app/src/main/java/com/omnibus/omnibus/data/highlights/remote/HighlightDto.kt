package com.omnibus.omnibus.data.highlights.remote

import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.HighlightColor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of `GET api/highlights/book/{uuid}`. The server's times are Unix seconds and its percent runs 0 to 100. Fields
 * the app doesn't read (`book_uuid`, `client_id`, `spine_index`, `chapter_title`, `created_at_iso`) are ignored.
 */
@Serializable
internal data class HighlightDto(
    @SerialName("id") val id: Long,
    @SerialName("text") val text: String? = null,
    @SerialName("note") val note: String? = null,
    @SerialName("color") val color: String? = null,
    @SerialName("epub_cfi_range") val epubCfiRange: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("percent_through_book") val percentThroughBook: Double? = null,
) {
    /** This row as a domain highlight, with times in milliseconds and the placement as a 0..1 fraction. */
    fun toDomain(): Highlight = Highlight(
        id = id,
        text = text,
        note = note,
        color = highlightColorFromToken(color),
        location = epubCfiRange,
        format = formatOf(epubCfiRange),
        createdAtMillis = createdAt?.times(1_000L),
        fraction = fractionOf(percentThroughBook),
    )
}

/** The colour for a server token. Matching ignores case and surrounding spaces; an unknown or missing token reads as Amber. */
internal fun highlightColorFromToken(token: String?): HighlightColor =
    HighlightColor.entries.firstOrNull { it.name.equals(token?.trim(), ignoreCase = true) } ?: HighlightColor.Amber

/** The format of a location: "epub" for a CFI, "pdf" for a PDF anchor, otherwise null. */
internal fun formatOf(location: String?): String? = when {
    location == null -> null
    location.startsWith("epubcfi(") -> "epub"
    location.startsWith("pdf:") || location.startsWith("pdf-page:") -> "pdf"
    else -> null
}

/** The server's percent through the book as a fraction clamped to 0..1, or null when it is missing or NaN. */
internal fun fractionOf(percent: Double?): Float? =
    percent?.takeIf { !it.isNaN() }?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() }
