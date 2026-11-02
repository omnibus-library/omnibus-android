package com.omnibus.omnibus.feature.bookdetail.more

import com.omnibus.omnibus.feature.bookdetail.more.shelves.ShelfMenuEntry

/** The More menu's entries, in menu order. Each ticket replaces only its own `null,`. */
internal val moreMenuEntries: List<BookMoreMenuEntry> = listOfNotNull(
    // #116 Add to shelf
    ShelfMenuEntry,
    // #117 Bookmarks
    null,
    // #118 Export
    null,
    // #119 Remove from wishlist
    null,
    // #120 Merge with
    null,
    // #121 Delete
    null,
)
