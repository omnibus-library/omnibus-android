package com.omnibus.omnibus.feature.shelves.detail

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.shelves.ShelfDetail
import com.omnibus.omnibus.data.shelves.ShelvesError
import com.omnibus.omnibus.feature.library.LibraryEntry
import com.omnibus.omnibus.feature.library.libraryEntries

/** How many covers the hero's mosaic shows. */
private const val MOSAIC_COVERS = 4

/** The signed-in user as the shelf rules need them. */
data class ShelfViewer(val userId: Long, val isAdmin: Boolean)

/**
 * What the viewer may do to a shelf. Edit is the owner's or an admin's, never the wishlist's. Adding and removing books
 * is further limited to hand-picked shelves.
 */
data class ShelfActions(val canEdit: Boolean, val canAddBooks: Boolean, val canRemoveBooks: Boolean)

/** The actions for [shelf] and [viewer]. All are false while either is unknown. */
fun shelfActionsOf(shelf: Shelf?, viewer: ShelfViewer?): ShelfActions {
    if (shelf == null || viewer == null) return ShelfActions(canEdit = false, canAddBooks = false, canRemoveBooks = false)
    val canEdit = shelf.kind != ShelfKind.Wishlist && (viewer.userId == shelf.ownerUserId || viewer.isAdmin)
    val canManageBooks = canEdit && shelf.kind == ShelfKind.Manual
    return ShelfActions(canEdit = canEdit, canAddBooks = canManageBooks, canRemoveBooks = canManageBooks)
}

/**
 * The grid's entries for a shelf's [books]. Two or more books of one series form a stack, as the Library does;
 * a lone series book is a plain cell.
 */
fun shelfEntries(books: List<Book>): List<LibraryEntry> = libraryEntries(books, emptyMap()).map { entry ->
    if (entry is LibraryEntry.Series && entry.group.count < 2) LibraryEntry.Single(entry.group.top) else entry
}

/** One part of the hero's meta line. */
sealed interface ShelfDetailMetaPart {

    data class BookCount(val count: Long) : ShelfDetailMetaPart

    data class Kind(val kind: ShelfKind) : ShelfDetailMetaPart

    data class Visibility(val visibility: ShelfVisibility) : ShelfDetailMetaPart
}

/** The meta line for [shelf]: its book count, kind and visibility, in that order. Visibility shows even when private. */
fun shelfDetailMetaParts(shelf: Shelf): List<ShelfDetailMetaPart> = listOf(
    ShelfDetailMetaPart.BookCount(shelf.bookCount),
    ShelfDetailMetaPart.Kind(shelf.kind),
    ShelfDetailMetaPart.Visibility(shelf.visibility),
)

/**
 * The hero's mosaic covers: the first books with covers of the last unfiltered page once it has loaded, else the
 * cached preview's covers, else none. A filtered page never supplies them, because [unfilteredBooks] is only set by
 * an unfiltered read.
 */
fun mosaicCoversOf(unfilteredBooks: List<Book>?, preview: ShelfPreview?): List<String> {
    val pageCovers = unfilteredBooks.orEmpty().filter { it.coverUrl != null }.take(MOSAIC_COVERS).map { it.uuid }
    if (pageCovers.isNotEmpty()) return pageCovers
    return preview?.coverUuids?.take(MOSAIC_COVERS).orEmpty()
}

/** The state the whole screen shows, decided by the first state that applies. */
sealed interface ShelfDetailScreenState {

    /** The shelf was deleted or is no longer visible: a full-screen message with only a back arrow. */
    data object Gone : ShelfDetailScreenState

    /** No shelf identity yet, and nothing has failed. */
    data object Loading : ShelfDetailScreenState

    /** No shelf identity, and the detail read failed. [error] is null when no server is saved. */
    data class Error(val error: ShelvesError?) : ShelfDetailScreenState

    /** The hero shows, with the books or their state under it. */
    data object Content : ShelfDetailScreenState
}

/** What fills the area under the hero. [Failed] and [FilterFailed] carry a null error when no server is saved. */
sealed interface ShelfContentState {

    /** The page read has not answered yet. */
    data object Loading : ShelfContentState

    /** The shelf's books. */
    data object Books : ShelfContentState

    /** The unfiltered shelf has no books, shown by its [kind]. */
    data class Empty(val kind: ShelfKind) : ShelfContentState

    /** The books have not reached this device, and [count] books are on the shelf. */
    data class Unreachable(val count: Long) : ShelfContentState

    /** The page read failed with nothing cached. */
    data class Failed(val error: ShelvesError?) : ShelfContentState

    /** A filtered read answered with no books. */
    data object FilteredEmpty : ShelfContentState

    /** A filtered read failed with nothing cached. */
    data class FilterFailed(val unreachable: Boolean, val error: ShelvesError?) : ShelfContentState
}
