package com.omnibus.omnibus.data.journals.remote

import com.omnibus.omnibus.data.journals.JournalEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of `GET api/journals/book/{uuid}`. The server's time is Unix seconds. Fields the app doesn't read (`body_html`,
 * `progress`, `client_id`, `author_has_avatar`, `book_uuid`, `updated_at` and the `*_iso` times) are ignored.
 */
@Serializable
internal data class JournalEntryDto(
    @SerialName("id") val id: Long,
    @SerialName("author_id") val authorId: Long,
    @SerialName("author_name") val authorName: String = "",
    @SerialName("body_md") val bodyMd: String = "",
    @SerialName("status") val status: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
) {
    /** This row as a domain entry, with the time in milliseconds. Only a status of "draft" reads as a draft. */
    fun toDomain(): JournalEntry = JournalEntry(
        id = id,
        authorId = authorId,
        authorName = authorName,
        bodyMd = bodyMd,
        isDraft = status?.trim().equals("draft", ignoreCase = true),
        createdAtMillis = createdAt?.times(1_000L),
    )
}
