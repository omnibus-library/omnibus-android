package com.omnibus.omnibus.feature.bookdetail.more.merge

/** Test tags of the Merge with… sheet and its menu row, so UI tests can find its parts. */
internal object MergeTestTags {
    const val SHEET = "merge-sheet"
    const val TITLE = "merge-title"
    const val CLOSE = "merge-close"
    const val INTRO = "merge-intro"
    const val PANEL = "merge-target-panel"
    const val LABEL = "merge-search-label"
    const val FIELD = "merge-search-field"
    const val NOTE = "merge-note"
    const val SPINNER = "merge-spinner"
    const val RETRY = "merge-retry"
    const val CONFIRM_COVERS = "merge-confirm-covers"
    const val CONFIRM_BODY = "merge-confirm-body"
    const val OFFLINE_NOTE = "merge-offline-note"
    const val MERGE_ERROR = "merge-error"
    const val BACK = "merge-back"
    const val MERGE = "merge-confirm"
    const val MENU_ROW = "merge-menu-row"

    /** The tag of the result row for the book [uuid]. */
    fun candidate(uuid: String) = "merge-candidate-$uuid"
}
