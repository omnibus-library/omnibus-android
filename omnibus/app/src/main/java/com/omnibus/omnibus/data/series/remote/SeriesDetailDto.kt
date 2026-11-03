package com.omnibus.omnibus.data.series.remote

import com.omnibus.omnibus.data.series.SeriesMember
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The slim part of `GET api/series/{id}` that the series read needs. It is owned here so the
 * series read does not depend on the book record's decoding.
 */
@Serializable internal data class SeriesDetailDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("book_count") val bookCount: Long = 0,
    val books: List<SeriesBookDto> = emptyList(),
)

/** One book in a series response. */
@Serializable internal data class SeriesBookDto(
    val id: Long = 0,
    @SerialName("unique_identifier") val uniqueIdentifier: String? = null,
    val filename: String = "",
    val title: String? = null,
    @SerialName("series_index") val seriesIndex: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val accent: String? = null,
) {
    /** Same rules as `Book.uuid` and `Book.displayTitle` (`M/data/library/Book.kt`). */
    fun toMember() = SeriesMember(
        uuid = uniqueIdentifier ?: id.toString(),
        title = title?.takeIf { it.isNotBlank() } ?: filename,
        seriesIndex = seriesIndex,
        coverUrl = coverUrl,
        accent = accent,
        hasCover = coverUrl != null,
    )
}
