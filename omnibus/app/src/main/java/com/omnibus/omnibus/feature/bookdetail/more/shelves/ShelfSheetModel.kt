package com.omnibus.omnibus.feature.bookdetail.more.shelves

import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.shelves.BookShelves
import com.omnibus.omnibus.data.shelves.ShelfRules
import com.omnibus.omnibus.data.shelves.ShelfSummary

/** What the Add to shelf sheet shows. */
sealed interface ShelfSheetContent {

    /** The shelves are being read. */
    data object Loading : ShelfSheetContent

    /** The shelves could not be read. The sheet offers a retry. */
    data object Failed : ShelfSheetContent

    /** The shelves are read. [manual] are the toggleable rows, [smart] the read-only ones. */
    data class Loaded(val manual: List<ManualShelfRow>, val smart: List<SmartShelfRow>) : ShelfSheetContent
}

/**
 * A hand-picked shelf row.
 *
 * @property bookCount The count the row shows, already moved by this session's toggles.
 * @property otherCoverUuids Up to three covers of other books on the shelf, in shelf order.
 * @property isOn Whether the book is on the shelf now, which can be ahead of the server while a write is pending.
 * @property isPending Whether a write for this shelf is in flight.
 */
data class ManualShelfRow(
    val id: Long,
    val name: String,
    val bookCount: Long,
    val otherCoverUuids: List<String>,
    val isOn: Boolean,
    val isPending: Boolean,
)

/**
 * A smart shelf row the book matches. It is read only.
 *
 * @property rules The shelf's rules, or null while they load or when they cannot be summarised.
 */
data class SmartShelfRow(
    val id: Long,
    val name: String,
    val bookCount: Long,
    val coverUuids: List<String>,
    val rules: ShelfRules?,
)

/** How many covers of other books a row's stack shows at most. */
private const val MAX_OTHER_COVERS = 3

/**
 * The ids of the user's own smart shelves that the book matches, in server order. The sheet lists
 * these and the ViewModel reads their rules.
 */
internal fun matchingOwnSmartShelves(book: BookShelves, user: CurrentUser): List<ShelfSummary> =
    book.shelves.filter {
        it.kind == ShelfKind.Smart && it.ownerUserId == user.id && it.id in book.matchingSmartShelfIds
    }

/**
 * Builds the sheet's rows from the server's shelves and this session's toggles. Only the user's own hand-picked
 * shelves and own matching smart shelves appear; wishlist and other users' shelves never do, even for an admin.
 *
 * @param member The hand-picked shelves the book is on now, including optimistic changes.
 * @param serverMember The hand-picked shelves the server said the book is on, at the last read.
 * @param pending The shelves with a write in flight.
 * @param rules The rules read so far, by smart shelf id.
 */
internal fun buildShelfSheet(
    book: BookShelves,
    user: CurrentUser,
    bookUuid: String,
    member: Set<Long>,
    serverMember: Set<Long>,
    pending: Set<Long>,
    rules: Map<Long, ShelfRules>,
): ShelfSheetContent.Loaded {
    val manual = book.shelves
        .filter { it.kind == ShelfKind.Manual && it.ownerUserId == user.id }
        .map { shelf ->
            val isOn = shelf.id in member
            val wasOn = shelf.id in serverMember
            val count = shelf.bookCount + (if (isOn && !wasOn) 1 else 0) - (if (!isOn && wasOn) 1 else 0)
            ManualShelfRow(
                id = shelf.id,
                name = shelf.name,
                bookCount = count.coerceAtLeast(0),
                otherCoverUuids = otherCovers(shelf, bookUuid),
                isOn = isOn,
                isPending = shelf.id in pending,
            )
        }
    val smart = matchingOwnSmartShelves(book, user).map { shelf ->
        SmartShelfRow(
            id = shelf.id,
            name = shelf.name,
            bookCount = shelf.bookCount,
            coverUuids = otherCovers(shelf, bookUuid),
            rules = rules[shelf.id],
        )
    }
    return ShelfSheetContent.Loaded(manual = manual, smart = smart)
}

/** The first covers of books other than [bookUuid], at most [MAX_OTHER_COVERS]. */
private fun otherCovers(shelf: ShelfSummary, bookUuid: String): List<String> =
    shelf.coverUuids.filter { it != bookUuid }.take(MAX_OTHER_COVERS)

/**
 * A smart row's meta line: the count, then the rule text when there is one.
 *
 * @param join Joins the count and the rule text, such as "%1$s · %2$s".
 */
internal fun smartShelfMeta(countText: String, ruleText: String?, join: (String, String) -> String): String =
    ruleText?.let { join(countText, it) } ?: countText
