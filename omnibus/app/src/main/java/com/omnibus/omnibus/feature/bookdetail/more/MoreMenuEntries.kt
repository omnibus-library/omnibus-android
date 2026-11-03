package com.omnibus.omnibus.feature.bookdetail.more

import com.omnibus.omnibus.feature.bookdetail.more.shelves.ShelfMenuEntry

/** The More menu's entries, in menu order. Each ticket replaces only its own `null,`. */
internal val moreMenuEntries: List<BookMoreMenuEntry> = listOfNotNull(
    // #116 Add to shelf
    ShelfMenuEntry,
    // #117 Bookmarks
    com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarksMenuEntry,
    // #118 Export
    com.omnibus.omnibus.feature.bookdetail.more.export.ExportMenuEntry,
    // #119 Remove from wishlist
    com.omnibus.omnibus.feature.bookdetail.wishlist.WishlistMenuEntry,
    // #120 Merge with
    com.omnibus.omnibus.feature.bookdetail.more.merge.MergeMenuEntry,
    // #121 Delete
    com.omnibus.omnibus.feature.bookdetail.more.delete.DeleteMenuEntry,
)
