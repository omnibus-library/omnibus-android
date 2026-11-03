package com.omnibus.omnibus.data.suggestions.remote

import com.omnibus.omnibus.data.suggestions.BookSuggestion
import com.omnibus.omnibus.data.suggestions.SuggestionsAnswer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET api/ebooks/{uuid}/suggestions`. A flat DTO rather than a polymorphic one, so an unknown future status
 * decodes to null in [toAnswer] instead of failing in the middle of decoding.
 */
@Serializable
internal data class SuggestionsResponseDto(
    val status: String = "",
    val items: List<BookSuggestionDto> = emptyList(),
) {
    /** The answer for [status], or null when this client does not know the status. */
    fun toAnswer(): SuggestionsAnswer? = when (status) {
        "not_configured" -> SuggestionsAnswer.NotConfigured
        "pending" -> SuggestionsAnswer.Pending
        "unavailable" -> SuggestionsAnswer.Unavailable
        "ready" -> SuggestionsAnswer.Ready(items.map { it.toDomain() })
        else -> null
    }
}

/** One item of a `ready` answer. Every field has a default, so a sparse item still decodes. */
@Serializable
internal data class BookSuggestionDto(
    @SerialName("hardcover_id") val hardcoverId: Long = 0,
    @SerialName("hardcover_url") val hardcoverUrl: String = "",
    val title: String = "",
    val author: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
) {
    fun toDomain(): BookSuggestion = BookSuggestion(
        hardcoverId = hardcoverId,
        hardcoverUrl = hardcoverUrl,
        title = title,
        author = author,
        coverPath = coverUrl,
    )
}
