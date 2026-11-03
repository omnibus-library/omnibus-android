package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

/** Test tags of the Bookmarks menu row and sheet. The row parts take the row's key, so each row has its own tags. */
internal object BookmarksTestTags {
    const val MENU_ROW = "bookmarks-menu-row"
    const val SHEET = "bookmarks-sheet"
    const val TITLE = "bookmarks-title"
    const val CLOSE = "bookmarks-close"
    const val LIST = "bookmarks-list"
    const val LOADING = "bookmarks-loading"
    const val ERROR = "bookmarks-error"
    const val EMPTY = "bookmarks-empty"

    /** The row for the bookmark with [key]. */
    fun row(key: String): String = "bookmarks-row-$key"

    /** The name of the row for [key]. */
    fun name(key: String): String = "bookmarks-name-$key"

    /** The meta line of the row for [key]. */
    fun meta(key: String): String = "bookmarks-meta-$key"

    /** The ribbon of the row for [key]. */
    fun ribbon(key: String): String = "bookmarks-ribbon-$key"

    /** The chevron of the row for [key]. */
    fun chevron(key: String): String = "bookmarks-chevron-$key"
}
