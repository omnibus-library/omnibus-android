package com.omnibus.omnibus.feature.bookdetail.status

import com.omnibus.omnibus.data.readstatus.ReadStatus
import java.util.Locale

/** Test tags of the read status pill and its menu. */
internal object ReadStatusTestTags {
    const val PILL = "book-detail-status-pill"
    const val MENU = "book-detail-status-menu"
    const val CHECK = "book-detail-status-check"
    const val FINISHED_DATE = "book-detail-status-finished-date"

    /** The menu row for [status]. */
    fun row(status: ReadStatus): String = "book-detail-status-row-${status.name.lowercase(Locale.ROOT)}"
}
