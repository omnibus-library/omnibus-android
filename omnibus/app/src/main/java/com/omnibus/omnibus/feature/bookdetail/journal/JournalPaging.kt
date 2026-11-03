package com.omnibus.omnibus.feature.bookdetail.journal

/** Entries composed per page of the journal list. */
internal const val JOURNAL_PAGE = 20

/** How many entries to compose after one more page: [shown] plus [page] (at least 1), clamped to 0..[total]. */
internal fun nextJournalShownCount(shown: Int, total: Int, page: Int = JOURNAL_PAGE): Int =
    if (total <= 0) 0 else (shown.coerceAtLeast(0) + page.coerceAtLeast(1)).coerceAtMost(total)
