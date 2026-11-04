package com.omnibus.omnibus.feature.audiobook.bookmarks

/**
 * Test tags of the player's bookmarks sheet. A row's own parts keep Book Detail's tags (`BookmarksTestTags.row` and so on),
 * since [com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarkRow] draws them.
 */
internal object PlayerBookmarksTestTags {
    const val SHEET = "player-bookmarks-sheet"
    const val TITLE = "player-bookmarks-title"
    const val CLOSE = "player-bookmarks-close"
    const val LIST = "player-bookmarks-list"
    const val LOADING = "player-bookmarks-loading"
    const val ERROR = "player-bookmarks-error"
    const val EMPTY = "player-bookmarks-empty"
    const val FOOTER = "player-bookmarks-footer"

    /** The swipe box of the row for [key]. */
    fun swipe(key: String): String = "player-bookmarks-swipe-$key"

    /** The remove background behind the row for [key], shown while it is swiped. */
    fun removeBackground(key: String): String = "player-bookmarks-remove-$key"
}
