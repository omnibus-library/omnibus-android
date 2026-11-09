package com.omnibus.omnibus.feature.library

import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.Oklch

/** The most shelves the Library's rail shows. */
internal const val RAIL_SHELF_LIMIT = 8

/** The most covers a shelf's mosaic draws. */
private const val MOSAIC_COVER_COUNT = 4

/**
 * The shelves the rail shows, in the order of [previews]: the viewer's own shelves and other users' public shelves that
 * aren't wishlists, without empty wishlists, at most [RAIL_SHELF_LIMIT]. A null [viewerId] owns no shelf.
 */
fun railShelvesOf(previews: List<ShelfPreview>, viewerId: Long?): List<ShelfPreview> =
    previews.filter { preview ->
        val shelf = preview.shelf
        val isOwn = viewerId != null && shelf.ownerUserId == viewerId
        val isSharedByAnother = shelf.visibility == ShelfVisibility.Public && shelf.kind != ShelfKind.Wishlist
        val isUnusedWishlist = shelf.kind == ShelfKind.Wishlist && shelf.bookCount == 0L
        (isOwn || isSharedByAnother) && !isUnusedWishlist
    }.take(RAIL_SHELF_LIMIT)

/** Whether a card names the shelf's owner: someone else's shelf that isn't a wishlist. */
fun showsOwnerAttribution(viewerId: Long?, ownerUserId: Long, kind: ShelfKind): Boolean =
    viewerId != null && viewerId != ownerUserId && kind != ShelfKind.Wishlist

/** One piece of a card's meta line. The screen localises each part and joins them. */
sealed interface ShelfMetaPart {

    data class BookCount(val count: Long) : ShelfMetaPart

    data object Smart : ShelfMetaPart

    data object Public : ShelfMetaPart

    data class Owner(val name: String) : ShelfMetaPart
}

/** The meta line of [shelf] for [viewerId], in display order: count, then Smart, Public and the owner when shown. */
fun shelfMetaParts(shelf: Shelf, viewerId: Long?): List<ShelfMetaPart> = buildList {
    add(ShelfMetaPart.BookCount(shelf.bookCount))
    if (shelf.kind == ShelfKind.Smart) add(ShelfMetaPart.Smart)
    if (shelf.visibility == ShelfVisibility.Public) add(ShelfMetaPart.Public)
    if (showsOwnerAttribution(viewerId, shelf.ownerUserId, shelf.kind)) add(ShelfMetaPart.Owner(shelf.ownerUsername))
}

/** How a shelf's covers are laid out, by how many there are. */
enum class MosaicArrangement { Plate, Single, SideBySide, LeadAndStack, Grid }

/** The arrangement of a shelf's mosaic and the [covers] it draws, in order. */
data class ShelfMosaic(val arrangement: MosaicArrangement, val covers: List<String>)

/** The mosaic for [coverUuids]: the first four, arranged by how many there are. */
fun shelfMosaicOf(coverUuids: List<String>): ShelfMosaic {
    val covers = coverUuids.take(MOSAIC_COVER_COUNT)
    val arrangement = when (covers.size) {
        0 -> MosaicArrangement.Plate
        1 -> MosaicArrangement.Single
        2 -> MosaicArrangement.SideBySide
        3 -> MosaicArrangement.LeadAndStack
        else -> MosaicArrangement.Grid
    }
    return ShelfMosaic(arrangement, covers)
}

/** The tone of a shelf's plate: its accent, else a hue derived from its name, as a book without art gets. */
fun shelfPlateTone(shelf: Shelf): Oklch =
    shelf.accent?.let(Oklch::parse) ?: Oklch(0.55, 0.07, CoverIdentity.titleHue(shelf.name))
