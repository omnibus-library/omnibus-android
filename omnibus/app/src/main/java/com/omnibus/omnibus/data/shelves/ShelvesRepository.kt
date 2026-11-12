package com.omnibus.omnibus.data.shelves

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfVisibility
import kotlinx.coroutines.flow.Flow

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

    /** The shelf [shelfId], cached first when this session has read it, then from the network. */
    fun getShelfFlow(shelfId: Long): Flow<ShelfDetailResult>

    /** The shelf [shelfId] read from the network only, for the editor, which must not seed from a cache. */
    suspend fun getShelf(shelfId: Long): ShelfDetailResult

    /**
     * The books on the shelf [shelfId] that match [clauses] (all of them when empty), in the server's order.
     * The unfiltered list is cached first when this session has read it; a filtered read is never cached.
     */
    fun getShelfBooksFlow(shelfId: Long, clauses: List<FilterClause>): Flow<ShelfBooksResult>

    /** Sends the fields set in [update] to the shelf [shelfId]. */
    suspend fun updateShelf(shelfId: Long, update: ShelfUpdate): ShelfWriteResult

    /** Deletes the shelf [shelfId]. Its books stay in the library. */
    suspend fun deleteShelf(shelfId: Long): ShelfWriteResult

    /** How many books the rules [rules] match under [matchMode], as the editor's preview. */
    suspend fun previewRules(matchMode: ShelfMatchMode, rules: List<ShelfRule>): RulePreviewResult
}

/**
 * A shelf with the fields the detail and editor need beyond the summary.
 *
 * @property description The description, or null when blank.
 * @property rules The match mode and rules; [ShelfRules.rules] is empty for a shelf that is not smart.
 */
data class ShelfDetail(
    val shelf: Shelf,
    val description: String?,
    val rules: ShelfRules,
)

/**
 * The fields of a shelf to change. A null field is left as it is on the server.
 *
 * @property rules Sent only together with [matchMode].
 */
data class ShelfUpdate(
    val name: String? = null,
    val description: String? = null,
    val visibility: ShelfVisibility? = null,
    val matchMode: ShelfMatchMode? = null,
    val rules: List<ShelfRule>? = null,
)

/** How many books a set of rules matches, out of all the books in the library. */
data class RuleMatchCount(val matched: Long, val total: Long)

/** The outcome of [ShelvesRepository.getShelfFlow] and [ShelvesRepository.getShelf]. */
sealed interface ShelfDetailResult {

    data class Success(val detail: ShelfDetail) : ShelfDetailResult

    /** The server answered 404: the shelf was deleted, or it is not visible to this user any more. */
    data object Gone : ShelfDetailResult

    /** No server is saved, so no request was made. */
    data object NoServer : ShelfDetailResult

    /** [error] is why the read failed. */
    data class Failure(val error: ShelvesError) : ShelfDetailResult
}

/** The outcome of [ShelvesRepository.getShelfBooksFlow]. */
sealed interface ShelfBooksResult {

    data class Success(val books: List<Book>) : ShelfBooksResult

    /** The server answered 404: the shelf was deleted, or it is not visible to this user any more. */
    data object Gone : ShelfBooksResult

    /** No server is saved, so no request was made. */
    data object NoServer : ShelfBooksResult

    /** [error] is why the read failed. */
    data class Failure(val error: ShelvesError) : ShelfBooksResult
}

/** The outcome of [ShelvesRepository.previewRules]. */
sealed interface RulePreviewResult {

    data class Success(val count: RuleMatchCount) : RulePreviewResult

    /** No server is saved, so no request was made. */
    data object NoServer : RulePreviewResult

    /** [error] is why the preview failed. */
    data class Failure(val error: ShelvesError) : RulePreviewResult
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

/** The ops a rule on this field accepts, in the order the editor lists them. */
val ShelfRuleField.acceptedOps: List<ShelfRuleOp>
    get() = when (this) {
        ShelfRuleField.Tag, ShelfRuleField.Genre, ShelfRuleField.Author, ShelfRuleField.Series ->
            listOf(ShelfRuleOp.Is, ShelfRuleOp.IsNot, ShelfRuleOp.Contains, ShelfRuleOp.StartsWith)
        ShelfRuleField.Rating, ShelfRuleField.Year -> listOf(ShelfRuleOp.Is, ShelfRuleOp.Gte)
        ShelfRuleField.Status -> listOf(ShelfRuleOp.Is)
        ShelfRuleField.Format -> listOf(ShelfRuleOp.Includes, ShelfRuleOp.Contains, ShelfRuleOp.StartsWith)
        ShelfRuleField.DateAdded, ShelfRuleField.DateUpdated ->
            listOf(ShelfRuleOp.InLast, ShelfRuleOp.Between, ShelfRuleOp.Before, ShelfRuleOp.After)
    }

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
