package com.omnibus.omnibus.data.metadata

import com.omnibus.omnibus.data.library.Book

/** Which chip list on a book. [wireField] is the override key the server uses. */
enum class MetadataListKind(val wireField: String) {
    Genres("genres"),
    Tags("subjects"),
}

/**
 * One value in the library's vocabulary, with how many books carry it.
 *
 * @property count The server's book count for [name].
 */
data class VocabularyEntry(val name: String, val count: Long)

/**
 * Metadata overrides for a book's chip lists. Each call is one request, answered with a result rather than an
 * exception.
 */
interface MetadataRepository {

    /**
     * Replaces [kind]'s whole list on the book with [values], sending only that list. Saves for one book and kind
     * leave in call order, one at a time, and a save that a newer call has replaced before it is sent is skipped.
     */
    suspend fun replaceList(bookUuid: String, kind: MetadataListKind, values: List<String>): MetadataWriteResult

    /** The library's values for [kind]: the 500 most-used, most-used first. */
    suspend fun getVocabulary(kind: MetadataListKind): VocabularyResult
}

/** The outcome of [MetadataRepository.replaceList]. */
sealed interface MetadataWriteResult {

    /** The server saved the list. [book] is the merged record it sent back. */
    data class Saved(val book: Book) : MetadataWriteResult

    /** A newer list for the same book and kind replaced this one before it was sent, so nothing was sent. */
    data object Superseded : MetadataWriteResult

    /** No server is saved, so no request was made. */
    data object NoServer : MetadataWriteResult

    /** [error] is why the save failed. The UI layer decides what text to show for it. */
    data class Failure(val error: MetadataError) : MetadataWriteResult
}

/** The outcome of [MetadataRepository.getVocabulary]. */
sealed interface VocabularyResult {

    /** The library's values, in the server's order. */
    data class Success(val entries: List<VocabularyEntry>) : VocabularyResult

    /** No server is saved, so no request was made. */
    data object NoServer : VocabularyResult

    /** [error] is why the read failed. */
    data class Failure(val error: MetadataError) : VocabularyResult
}

/** Why a metadata request failed. The UI layer decides what text to show for each. */
sealed interface MetadataError {

    /** The server could not be reached. */
    data object Unreachable : MetadataError

    /** The response could not be decoded, or the request failed for any other reason. */
    data object UnexpectedResponse : MetadataError

    /** The server answered 401, so the user must sign in again. */
    data object SessionExpired : MetadataError

    /** The server answered 403: the user may not edit this book. */
    data object Forbidden : MetadataError

    /** The server answered 404, or the request named a blank uuid, so nothing was sent. */
    data object NotFound : MetadataError

    /**
     * The server answered with another non-2xx status.
     *
     * @property code The HTTP status code.
     * @property serverMessage The server's own text: the JSON `error`, `message` or `detail` string,
     * else the trimmed plain-text body capped at 300 characters, else null.
     */
    data class Http(val code: Int, val serverMessage: String?) : MetadataError
}

/** The book's raw list for [kind]. */
fun Book.valuesOf(kind: MetadataListKind): List<String> = when (kind) {
    MetadataListKind.Genres -> genres
    MetadataListKind.Tags -> subjects
}

/** A copy of the book with [kind]'s list replaced by [values]. */
fun Book.withValues(kind: MetadataListKind, values: List<String>): Book = when (kind) {
    MetadataListKind.Genres -> copy(genres = values)
    MetadataListKind.Tags -> copy(subjects = values)
}
