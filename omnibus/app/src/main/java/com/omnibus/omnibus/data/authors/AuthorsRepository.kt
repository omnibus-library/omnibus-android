package com.omnibus.omnibus.data.authors

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.metadata.VocabularyEntry

/** Author reads. Each call is one request, answered with a result rather than an exception. */
interface AuthorsRepository {

    /** The author [authorId] and every book of theirs in the library, in the server's order. */
    suspend fun getAuthor(authorId: Long): AuthorResult

    /** Every author the library has, with how many books each has, in the server's order. */
    suspend fun getAuthors(): AuthorListResult
}

/** The outcome of [AuthorsRepository.getAuthors]. Each entry is an author's name and book count. */
sealed interface AuthorListResult {

    data class Success(val entries: List<VocabularyEntry>) : AuthorListResult

    /** No server is saved, so no request was made. */
    data object NoServer : AuthorListResult

    /** [error] is why the read failed. */
    data class Failure(val error: AuthorsError) : AuthorListResult
}

/** An author and their books. */
data class Author(
    val id: Long,
    val name: String,
    val books: List<Book>,
)

/** The outcome of [AuthorsRepository.getAuthor]. */
sealed interface AuthorResult {

    data class Success(val author: Author) : AuthorResult

    /** No server is saved, so no request was made. */
    data object NoServer : AuthorResult

    /** [error] is why the read failed. The UI layer decides what to show for it. */
    data class Failure(val error: AuthorsError) : AuthorResult
}

/** Why an authors request failed. */
sealed interface AuthorsError {

    /** The server could not be reached. */
    data object Unreachable : AuthorsError

    /** The response could not be decoded, or the request failed for any other reason. */
    data object UnexpectedResponse : AuthorsError

    /** The server answered 401, so the user must sign in again. */
    data object SessionExpired : AuthorsError

    /** The server answered 404: no author has that id. */
    data object NotFound : AuthorsError

    /** The server answered with another non-2xx status. */
    data class Http(val code: Int) : AuthorsError
}
