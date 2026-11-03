package com.omnibus.omnibus.feature.bookdetail.more.delete

/** Test tags for the Delete entry. They are not user-visible. */
internal object DeleteTestTags {
    const val MENU_ROW = "delete-menu-row"
    const val MENU_ICON = "delete-menu-icon"
    const val SHEET = "delete-sheet"
    const val TITLE = "delete-title"
    const val CLOSE = "delete-close"
    const val LOADING = "delete-loading"
    const val ERROR = "delete-error"
    const val SELECT_ALL = "delete-select-all"
    const val CHOOSE_ACTION = "delete-choose-action"
    const val CANCEL = "delete-cancel"
    const val BACK = "delete-back"
    const val CONFIRM_ACTION = "delete-confirm-action"
    const val CONFIRM_BODY = "delete-confirm-body"
    const val LOSSES = "delete-losses"
    const val INLINE_ERROR = "delete-inline-error"

    /** A file's checkbox row. */
    fun fileRow(id: Long) = "delete-file-$id"

    /** A physical copy's checkbox row. */
    fun copyRow(id: Long) = "delete-copy-$id"
}
