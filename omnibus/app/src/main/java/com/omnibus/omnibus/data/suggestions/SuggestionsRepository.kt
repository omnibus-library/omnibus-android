package com.omnibus.omnibus.data.suggestions

/**
 * Read-alike suggestions for a book. Each call is one request, answered with a result rather than an exception.
 * The server builds the suggestions in the background, so a read can answer [SuggestionsAnswer.Pending].
 */
interface SuggestionsRepository {

    /** One read of the suggestions for the book [bookUuid]. */
    suspend fun getSuggestions(bookUuid: String): SuggestionsResult

    /** The bytes of a suggestion cover at the server-relative [coverPath], such as `/api/suggestions/{uuid}/{rank}/cover`. */
    suspend fun getCover(coverPath: String): SuggestionCoverResult
}

/** One suggested book. [coverPath] is null when the server has no cover for it. */
data class BookSuggestion(
    val hardcoverId: Long,
    val hardcoverUrl: String,
    val title: String,
    val author: String,
    val coverPath: String?,
)

/** What the server said about a book's suggestions. */
sealed interface SuggestionsAnswer {

    /** The server has no Hardcover key, so it will never have suggestions. */
    data object NotConfigured : SuggestionsAnswer

    /** The server is still building the suggestions. Ask again later. */
    data object Pending : SuggestionsAnswer

    /** The server could not build suggestions for this book. */
    data object Unavailable : SuggestionsAnswer

    /** The suggestions, in the server's order. The list may be empty. */
    data class Ready(val items: List<BookSuggestion>) : SuggestionsAnswer
}

/** The outcome of [SuggestionsRepository.getSuggestions]. */
sealed interface SuggestionsResult {

    data class Success(val answer: SuggestionsAnswer) : SuggestionsResult

    /** No server is saved, so no request was made. */
    data object NoServer : SuggestionsResult

    /** [error] is why the read failed. The UI layer decides what to show for it. */
    data class Failure(val error: SuggestionsError) : SuggestionsResult
}

/** Why a suggestions request failed. */
sealed interface SuggestionsError {

    /** The server could not be reached. */
    data object Unreachable : SuggestionsError

    /** The response could not be decoded, or it carried a status this client does not know. */
    data object UnexpectedResponse : SuggestionsError

    /** The server answered 401, so the user must sign in again. */
    data object SessionExpired : SuggestionsError

    /** The server answered with another non-2xx status. */
    data class Http(val code: Int) : SuggestionsError
}

/** The outcome of [SuggestionsRepository.getCover]. */
sealed interface SuggestionCoverResult {

    /** The cover's [bytes] and the server's [mimeType], which is null when the server sent no Content-Type. */
    class Found(val bytes: ByteArray, val mimeType: String?) : SuggestionCoverResult

    /** The server has no cover at that path (404), or the path is not a suggestion cover path. */
    data object NotFound : SuggestionCoverResult

    /** The cover could not be loaded: the server failed, could not be reached, or no server is saved. */
    data object Failure : SuggestionCoverResult
}
