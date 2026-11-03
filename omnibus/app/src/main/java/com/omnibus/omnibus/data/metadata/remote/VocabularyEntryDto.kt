package com.omnibus.omnibus.data.metadata.remote

import com.omnibus.omnibus.data.metadata.VocabularyEntry
import kotlinx.serialization.Serializable

/** One entry of `GET api/genres` or `GET api/tags`. A missing `count` reads as 0. */
@Serializable
internal data class VocabularyEntryDto(
    val name: String,
    val count: Long = 0,
) {
    fun toDomain() = VocabularyEntry(name, count)
}
