package com.omnibus.omnibus.feature.bookdetail.highlights

/** Rows composed per page of the highlights list. */
internal const val HIGHLIGHTS_PAGE = 20

/** How many rows to compose after one more page: [shown] plus [page] (at least 1), clamped to 0..[total]. */
internal fun nextShownCount(shown: Int, total: Int, page: Int = HIGHLIGHTS_PAGE): Int =
    if (total <= 0) 0 else (shown.coerceAtLeast(0) + page.coerceAtLeast(1)).coerceAtMost(total)
