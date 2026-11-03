package com.omnibus.omnibus.data.series

/**
 * The series read for the Book Detail page (contract C11). Each call is one request, answered
 * with a result rather than an exception.
 */
interface SeriesRepository {

    /** The books in series [seriesId], in series order. */
    suspend fun getSeries(seriesId: Long): SeriesResult
}

/**
 * One book in a series, as the series fan and the stage line need it.
 *
 * @property uuid The book's identifier, the same rule as `Book.uuid`.
 * @property title The book's title, or its filename when the title is blank.
 * @property seriesIndex The server's series number as sent, e.g. "1.5", or null.
 * @property coverUrl The cover's URL, or null when the book has no cover.
 * @property accent The book's accent token, or null.
 * @property hasCover Whether the book has a cover.
 */
data class SeriesMember(
    val uuid: String,
    val title: String,
    val seriesIndex: String?,
    val coverUrl: String?,
    val accent: String?,
    val hasCover: Boolean,
)

/** The outcome of [SeriesRepository.getSeries]. */
sealed interface SeriesResult {

    /** The series members, in series-index order. */
    data class Success(val members: List<SeriesMember>) : SeriesResult

    /** No server is saved, so no request was made. */
    data object NoServer : SeriesResult

    /** [error] is why the read failed. The UI layer decides what to show for it. */
    data class Failure(val error: SeriesError) : SeriesResult
}

/** Why a series read failed. */
sealed interface SeriesError {

    /** The server could not be reached. */
    data object Unreachable : SeriesError

    /** The response could not be decoded, or the request failed for any other reason. */
    data object UnexpectedResponse : SeriesError

    /** The server answered 401, so the user must sign in again. */
    data object SessionExpired : SeriesError

    /** The server answered 404: no series has that id. */
    data object NotFound : SeriesError

    /** The server answered with another non-2xx status. */
    data class Http(val code: Int) : SeriesError
}
