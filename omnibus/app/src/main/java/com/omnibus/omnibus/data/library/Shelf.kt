package com.omnibus.omnibus.data.library

/** A shelf of books the user can see, including shelves owned by other users. */
data class Shelf(
    val id: Long,
    val ownerUserId: Long,
    val ownerUsername: String,
    val kind: ShelfKind,
    val name: String,
    val visibility: ShelfVisibility,
    val accent: String?,
    val bookCount: Long,
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

/** A [Shelf] with the first few of its books, used as covers on the Library screen. */
data class ShelfPreview(
    val shelf: Shelf,
    val covers: List<Book>,
)
