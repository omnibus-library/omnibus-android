package com.omnibus.omnibus.feature.shelves

import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.feature.library.CoverGridMetrics

/** The Shelves screen's tile grid: tiles 156 to 300 dp wide, 12 dp apart (iOS `CoverGridDensity.tiles`). */
internal val ShelvesGridMetrics = CoverGridMetrics(minCell = 156f, maxCell = 300f, spacing = 12f)

/** One owner's shelves on the Shelves screen. [isViewer] marks the signed-in reader's own group. */
data class ShelfOwnerGroup(
    val ownerId: Long,
    val ownerName: String,
    val ownerHasAvatar: Boolean,
    val isViewer: Boolean,
    val shelves: List<ShelfPreview>,
)

/** The header's counts. The UI drops a zero part and picks the plurals. */
data class ShelvesCensus(val total: Int, val yours: Int, val fromOthers: Int, val otherReaders: Int)

/**
 * The shelves grouped by owner, with nothing left out. The viewer's group comes first, then the others by name
 * ignoring case and then by owner id. Within a group, wishlists come last and the rest by name ignoring case, then
 * by shelf id.
 */
fun shelfOwnerGroups(previews: List<ShelfPreview>, viewerId: Long?): List<ShelfOwnerGroup> {
    val groups = previews.groupBy { it.shelf.ownerUserId }.map { (ownerId, owned) ->
        val owner = owned.first().shelf
        ShelfOwnerGroup(
            ownerId = ownerId,
            ownerName = owner.ownerUsername,
            ownerHasAvatar = owner.ownerHasAvatar,
            isViewer = viewerId != null && ownerId == viewerId,
            shelves = owned.sortedWith(inGroupOrder),
        )
    }
    return groups.sortedWith(
        compareByDescending<ShelfOwnerGroup> { it.isViewer }
            .thenBy { it.ownerName.lowercase() }
            .thenBy { it.ownerId },
    )
}

/** How many shelves the viewer owns, how many others do, and how many other readers those are spread over. */
fun shelvesCensus(previews: List<ShelfPreview>, viewerId: Long?): ShelvesCensus {
    val owners = previews.map { it.shelf.ownerUserId }
    val yours = if (viewerId == null) 0 else owners.count { it == viewerId }
    val otherReaders = owners.distinct().count { it != viewerId }
    return ShelvesCensus(
        total = previews.size,
        yours = yours,
        fromOthers = previews.size - yours,
        otherReaders = otherReaders,
    )
}

/** Whether the list holds another reader's private shelf, which only shows for a known viewer (an admin's view). */
fun showsPrivateShelvesNotice(previews: List<ShelfPreview>, viewerId: Long?): Boolean =
    viewerId != null && previews.any {
        it.shelf.visibility == ShelfVisibility.Private && it.shelf.ownerUserId != viewerId
    }

/** The order of shelves within one owner's group: wishlists last, then by name ignoring case, then by id. */
private val inGroupOrder: Comparator<ShelfPreview> =
    compareBy<ShelfPreview> { it.shelf.kind == ShelfKind.Wishlist }
        .thenBy { it.shelf.name.lowercase() }
        .thenBy { it.shelf.id }
