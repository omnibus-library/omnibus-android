package com.omnibus.omnibus.feature.bookdetail.overview.author

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.components.CoverIdentity

/** The most books the shelf shows. */
internal const val MAX_AUTHOR_BOOKS = 12

/** One book on the shelf, with the year already formatted for display. */
data class AuthorShelfBook(
    val uuid: String,
    val identity: CoverIdentity,
    val title: String,
    val year: String?,
)

/** The author the shelf is about: [id] to fetch their books and open their page, [name] for the heading. */
internal data class AuthorTarget(val id: Long, val name: String)

/**
 * The first author of [book], or null when they have no id, so there is nothing to fetch. Like iOS, only the first
 * credited creator counts. A blank name falls back to the book's author line.
 */
internal fun authorTargetOf(book: Book): AuthorTarget? {
    val creator = book.creators.firstOrNull() ?: return null
    val id = creator.id ?: return null
    return AuthorTarget(id, creator.name.trim().ifEmpty { book.authorDisplay })
}

/** The first [MAX_AUTHOR_BOOKS] of [authorBooks] other than the book [currentBookId], in the server's order. */
internal fun moreByAuthor(authorBooks: List<Book>, currentBookId: Long): List<AuthorShelfBook> =
    authorBooks
        .asSequence()
        .filter { it.id != currentBookId }
        .take(MAX_AUTHOR_BOOKS)
        .map { AuthorShelfBook(uuid = it.uuid, identity = it.coverIdentity(), title = it.displayTitle, year = it.year?.toString()) }
        .toList()
