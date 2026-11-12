package com.omnibus.omnibus.data.authors.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One entry of `GET api/authors`: the author's name and how many books of theirs the library holds. */
@Serializable
internal data class AuthorSummaryDto(
    val name: String = "",
    @SerialName("book_count")
    val bookCount: Long = 0,
)
