package com.omnibus.omnibus.feature.bookdetail.more.shelves

/** Test tags for the Add to shelf sheet. They are not user-visible. */
internal object ShelfSheetTestTags {
    const val SHEET = "add-to-shelf-sheet"
    const val TITLE = "add-to-shelf-title"
    const val CLOSE = "add-to-shelf-close"
    const val INTRO = "add-to-shelf-intro"
    const val EMPTY = "add-to-shelf-empty"
    const val SMART_TITLE = "add-to-shelf-smart-title"
    const val LOADING = "add-to-shelf-loading"
    const val ERROR = "add-to-shelf-error"

    /** A hand-picked shelf's row. */
    fun row(id: Long) = "add-to-shelf-row-$id"

    /** The toggle drawn at the end of a hand-picked shelf's row. */
    fun toggle(id: Long) = "add-to-shelf-toggle-$id"

    /** A smart shelf's read-only row. */
    fun smartRow(id: Long) = "add-to-shelf-smart-row-$id"

    /** The cover stack of a row, with its covers. */
    fun stack(id: Long) = "add-to-shelf-stack-$id"

    /** The cover of another book at [index] in a row's stack. */
    fun cover(id: Long, index: Int) = "add-to-shelf-cover-$id-$index"

    /** This book's cover in a hand-picked shelf's stack, which pops in when the shelf is turned on. */
    fun bookCover(id: Long) = "add-to-shelf-book-cover-$id"
}
