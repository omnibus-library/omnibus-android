package com.omnibus.omnibus.data.bookmarks.remote

import com.omnibus.omnibus.data.bookmarks.Bookmark
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of `GET api/bookmarks/book/{uuid}` or the answer to `POST api/bookmarks`. The server's times are Unix seconds and
 * its percent runs 0 to 100. Fields the app doesn't read (`created_at_iso`, `spine_index`) are ignored.
 */
@Serializable
internal data class BookmarkDto(
    @SerialName("id") val id: Long,
    @SerialName("book_uuid") val bookUuid: String = "",
    @SerialName("position") val position: String = "",
    @SerialName("title") val title: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("chapter_title") val chapterTitle: String? = null,
    @SerialName("percent_through_book") val percentThroughBook: Double? = null,
) {
    /** This row as a domain bookmark, with the percent as a 0..1 fraction. */
    fun toDomain(): Bookmark = Bookmark(
        id = id,
        clientId = clientId,
        bookUuid = bookUuid,
        position = position,
        title = title,
        chapterTitle = chapterTitle,
        fraction = fractionOf(percentThroughBook),
        createdAtSeconds = createdAt,
    )
}

/** The server's percent through the book as a fraction clamped to 0..1, or null when it is missing or NaN. */
internal fun fractionOf(percent: Double?): Float? =
    percent?.takeIf { !it.isNaN() }?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() }
