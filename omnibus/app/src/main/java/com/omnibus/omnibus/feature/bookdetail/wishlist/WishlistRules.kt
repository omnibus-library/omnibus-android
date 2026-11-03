package com.omnibus.omnibus.feature.bookdetail.wishlist

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.wishlist.WishlistState
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.actionbar.ActionBarModel
import com.omnibus.omnibus.feature.bookdetail.actionbar.hasReadableCopy
import java.net.URLEncoder

/** Whether [book] is on the wishlist with no ebook or audiobook, so its page is the wishlist-only page. */
internal fun isWishlistOnly(book: Book, state: WishlistState): Boolean =
    state is WishlistState.Present && !hasReadableCopy(book)

/**
 * The action bar's wishlist variant for [book], or null unless the book is wishlist-only. "Find a copy" searches the
 * store for the ISBN, else the title and author.
 */
internal fun wishlistActionModel(book: Book, state: WishlistState): ActionBarModel.Wishlist? {
    if (!isWishlistOnly(book, state)) return null
    return ActionBarModel.Wishlist(
        findCopy = BookDetailDestination.ExternalUrl(
            storeSearchUrl(book.isbn13, book.displayTitle, storeAuthor(book)),
        ),
        checkIn = BookDetailDestination.CheckIn(book.uuid),
    )
}

/**
 * The author for the store search. `Book.authorDisplay` falls back to the unknown-author placeholder only when no
 * creator has a name, so that case gives blank and the placeholder is never searched for.
 */
private fun storeAuthor(book: Book): String =
    if (book.creators.any { it.name.isNotBlank() }) book.authorDisplay else ""

/**
 * The Amazon search URL for [isbn13] when it is present, and otherwise for the title and author. The query is
 * percent-encoded as UTF-8.
 */
internal fun storeSearchUrl(isbn13: String?, title: String, author: String): String {
    val query = isbn13?.trim()?.takeIf { it.isNotEmpty() } ?: "$title $author".trim()
    return "https://www.amazon.com/s?k=" + URLEncoder.encode(query, "UTF-8")
}

/** What the More menu's "Remove from wishlist" row shows. */
internal enum class RemoveRow { Hidden, Enabled, DisabledOffline, DisabledRemoving }

/**
 * The row for the More menu. It is hidden unless the book is on the wishlist. Offline wins over a removal in flight,
 * so the sub-line says why the row is off.
 */
internal fun removeRow(state: WishlistState, isOnline: Boolean, removing: Boolean): RemoveRow = when {
    state !is WishlistState.Present -> RemoveRow.Hidden
    !isOnline -> RemoveRow.DisabledOffline
    removing -> RemoveRow.DisabledRemoving
    else -> RemoveRow.Enabled
}

/** Which warning the removal dialog shows. */
internal enum class RemovalWarning { KeepsBook, OnlyPlace }

/** The dialog's warning for a book with or without a readable copy. */
internal fun removalWarning(hasFile: Boolean): RemovalWarning =
    if (hasFile) RemovalWarning.KeepsBook else RemovalWarning.OnlyPlace

/**
 * Whether the page closes after a removal. It does when the server deleted the book, or when nothing else would keep
 * the page open: no readable copy and no physical copy.
 */
internal fun leavesAfterRemoval(bookDeleted: Boolean, hasFile: Boolean, hasPhysical: Boolean): Boolean =
    bookDeleted || (!hasFile && !hasPhysical)
