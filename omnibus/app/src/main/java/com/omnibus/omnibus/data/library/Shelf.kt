package com.omnibus.omnibus.data.library

/**
 * A shelf of books the user can see, including shelves owned by other users.
 *
 * @property ownerHasAvatar Whether the server has a picture for the owner. False when it doesn't say.
 */
data class Shelf(
    val id: Long,
    val ownerUserId: Long,
    val ownerUsername: String,
    val kind: ShelfKind,
    val name: String,
    val visibility: ShelfVisibility,
    val accent: String?,
    val bookCount: Long,
    val ownerHasAvatar: Boolean = false,
)

/** How a [Shelf] gets its books. */
enum class ShelfKind {
    /** Books chosen by a rule. */
    Smart,

    /** Books added by hand. */
    Manual,

    /** Books the user wants to acquire. */
    Wishlist,
}

/** Who can see a [Shelf]. */
enum class ShelfVisibility {
    Private,
    Public,
}

/** A [Shelf] with the uuids of up to four of its books that have covers, in shelf order, for the Library's shelf mosaic. */
data class ShelfPreview(
    val shelf: Shelf,
    val coverUuids: List<String>,
)
