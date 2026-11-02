package com.omnibus.omnibus.data.shelves

import com.omnibus.omnibus.data.library.ShelfKind

/**
 * Shelf reads and membership writes for the Add to shelf sheet. Each call is one request, answered
 * with a result rather than an exception.
 */
interface ShelvesRepository {

    /** Every shelf the user can see, and which of them hold the book [bookUuid] now. */
    suspend fun getBookShelves(bookUuid: String): BookShelvesResult

    /** The match mode and rules of the smart shelf [shelfId]. */
    suspend fun getShelfRules(shelfId: Long): ShelfRulesResult

    /** Adds [bookUuid] to the hand-picked shelf [shelfId]. A book already on the shelf still succeeds. */
    suspend fun addBook(shelfId: Long, bookUuid: String): ShelfWriteResult

    /** Removes [bookUuid] from the hand-picked shelf [shelfId]. A book not on the shelf still succeeds. */
    suspend fun removeBook(shelfId: Long, bookUuid: String): ShelfWriteResult
}

/**
 * A shelf as the sheet needs it.
 *
 * @property ownerUserId The user who owns the shelf.
 * @property bookCount The server's count of books on the shelf.
 * @property coverUuids Up to four books with covers, in shelf order.
 */
data class ShelfSummary(
    val id: Long,
    val ownerUserId: Long,
    val kind: ShelfKind,
    val name: String,
    val bookCount: Long,
    val coverUuids: List<String>,
)

/**
 * The shelves for one book.
 *
 * @property shelves Every shelf the user can see, in the server's order.
 * @property memberShelfIds The hand-picked shelves that hold the book.
 * @property matchingSmartShelfIds The smart shelves whose rules match the book now.
 */
data class BookShelves(
    val shelves: List<ShelfSummary>,
    val memberShelfIds: Set<Long>,
    val matchingSmartShelfIds: Set<Long>,
)

/**
 * A smart shelf's rules.
 *
 * @property matchMode Whether every rule or any rule must match, or null when the server sent none
 * or an unknown one.
 */
data class ShelfRules(
    val matchMode: ShelfMatchMode?,
    val rules: List<ShelfRule>,
)

/** Whether a smart shelf needs every rule or any rule to match. */
enum class ShelfMatchMode { All, Any }

/**
 * One smart shelf rule. [field] or [op] is null when the server sent a token this app doesn't know.
 */
data class ShelfRule(
    val field: ShelfRuleField?,
    val op: ShelfRuleOp?,
    val value: String,
)

/** The fields a smart shelf rule can test. [wire] is the token the server uses. */
enum class ShelfRuleField(val wire: String) {
    Tag("tag"),
    Genre("genre"),
    Author("author"),
    Series("series"),
    Rating("rating"),
    Status("status"),
    Format("format"),
    Year("year"),
    DateAdded("date_added"),
    DateUpdated("date_updated");

    companion object {

        /** The field whose [wire] token is [token], or null when none matches. */
        fun fromWire(token: String?): ShelfRuleField? = entries.firstOrNull { it.wire == token }
    }
}

/** The comparisons a smart shelf rule can make. [wire] is the token the server uses. */
enum class ShelfRuleOp(val wire: String) {
    Is("is"),
    IsNot("is_not"),
    Contains("contains"),
    StartsWith("starts_with"),
    Gte("gte"),
    Includes("includes"),
    InLast("in_last"),
    Between("between"),
    Before("before"),
    After("after");

    companion object {

        /** The op whose [wire] token is [token], or null when none matches. */
        fun fromWire(token: String?): ShelfRuleOp? = entries.firstOrNull { it.wire == token }
    }
}

/** The outcome of [ShelvesRepository.getBookShelves]. */
sealed interface BookShelvesResult {

    data class Success(val shelves: BookShelves) : BookShelvesResult

    /** No server is saved, so no request was made. */
    data object NoServer : BookShelvesResult

    /** [error] is why the read failed. The UI layer decides what text to show for it. */
    data class Failure(val error: ShelvesError) : BookShelvesResult
}

/** The outcome of [ShelvesRepository.getShelfRules]. */
sealed interface ShelfRulesResult {

    data class Success(val rules: ShelfRules) : ShelfRulesResult

    /** No server is saved, so no request was made. */
    data object NoServer : ShelfRulesResult

    /** [error] is why the read failed. */
    data class Failure(val error: ShelvesError) : ShelfRulesResult
}

/** The outcome of [ShelvesRepository.addBook] and [ShelvesRepository.removeBook]. */
sealed interface ShelfWriteResult {

    data object Success : ShelfWriteResult

    /** No server is saved, so no request was made. */
    data object NoServer : ShelfWriteResult

    /** [error] is why the write failed. */
    data class Failure(val error: ShelvesError) : ShelfWriteResult
}

/** Why a shelves request failed. The UI layer decides what text to show for each. */
sealed interface ShelvesError {

    /** The server could not be reached. */
    data object Unreachable : ShelvesError

    /** The response could not be decoded, or the request failed for any other reason. */
    data object UnexpectedResponse : ShelvesError

    /** The server answered 401, so the user must sign in again. */
    data object SessionExpired : ShelvesError

    /** The server answered 403: the user may not change this shelf. */
    data object Forbidden : ShelvesError

    /** The server answered 404, or the request named a blank uuid, so nothing was sent. */
    data object NotFound : ShelvesError

    /**
     * The server answered with another non-2xx status.
     *
     * @property code The HTTP status code.
     * @property serverMessage The server's own text: the JSON `error`, `message` or `detail` string,
     * else the trimmed plain-text body capped at 300 characters, else null.
     */
    data class Http(val code: Int, val serverMessage: String?) : ShelvesError
}
