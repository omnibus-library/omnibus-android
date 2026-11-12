package com.omnibus.omnibus.data.shelves.remote

import com.omnibus.omnibus.data.library.remote.BookDto
import kotlinx.serialization.Serializable

/** `GET api/shelves/{id}/page`. The books decode as the Library's own page does. */
@Serializable
internal data class ShelfPageDto(
    val books: List<BookDto> = emptyList(),
)
