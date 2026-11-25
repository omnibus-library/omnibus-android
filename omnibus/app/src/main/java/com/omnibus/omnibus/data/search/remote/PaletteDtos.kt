package com.omnibus.omnibus.data.search.remote

import com.omnibus.omnibus.data.search.LibraryBookMatches
import com.omnibus.omnibus.data.search.LibraryHit
import com.omnibus.omnibus.data.search.LibrarySearchResults
import com.omnibus.omnibus.data.search.SearchMatches
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The JSON for `GET api/search/palette`. Unknown keys are ignored and null properties are omitted when encoding. */
internal val PaletteJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** The part of `GET api/search/palette` the library search reads. [bookTotal] is absent from older servers. */
@Serializable
internal data class PaletteResultsDto(
    val books: List<PaletteBookDto> = emptyList(),
    @SerialName("book_total") val bookTotal: Int? = null,
) {
    fun toDomain() = LibraryBookMatches(
        books = books.map { it.toDomain() },
        total = bookTotal ?: books.size,
    )

    fun toResults() = LibrarySearchResults(
        books = books.matches(bookTotal) { it.toDomain() },
    )
}

/** A section's rows mapped to domain models, with [total] (or the row count when the server omits it). */
private inline fun <D, T> List<D>.matches(total: Int?, map: (D) -> T): SearchMatches<T> =
    SearchMatches(this.map(map), total ?: size)

@Serializable
internal data class PaletteBookDto(
    val uuid: String = "",
    val title: String,
    @SerialName("author_display") val authorDisplay: String = "",
    val year: String? = null,
    val formats: List<String> = emptyList(),
    @SerialName("cover_url") val coverUrl: String? = null,
    val accent: String? = null,
    @SerialName("has_physical") val hasPhysical: Boolean = false,
) {
    fun toDomain() = LibraryHit(uuid, title, authorDisplay, year, formats, coverUrl, accent, hasPhysical)
}
