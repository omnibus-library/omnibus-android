package com.omnibus.omnibus.data.authors.remote

import com.omnibus.omnibus.data.authors.Author
import com.omnibus.omnibus.data.library.remote.BookDto
import kotlinx.serialization.Serializable

/** `GET api/authors/{id}`, reduced to the author and their books. The books are the library's own book records. */
@Serializable
internal data class AuthorDetailDto(
    val id: Long,
    val name: String = "",
    val books: List<BookDto> = emptyList(),
) {
    fun toDomain(): Author = Author(id = id, name = name, books = books.map { it.toDomain() })
}
