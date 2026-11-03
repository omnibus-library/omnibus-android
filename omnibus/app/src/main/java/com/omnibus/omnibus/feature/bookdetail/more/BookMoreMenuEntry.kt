package com.omnibus.omnibus.feature.bookdetail.more

import androidx.compose.runtime.Composable
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext

/**
 * One entry of the More menu on the Book Detail page (contract C17). Each ticket adds its own entry to the
 * `moreMenuEntries` registry.
 *
 * [Rows] emits zero or more menu rows, normally with [BookMoreMenuRow], and may emit none when the entry does not
 * apply. [Overlay] is composed from the page's first frame, outside the menu, so a sheet or dialog it opens survives
 * the menu closing. An entry backed by a Hilt ViewModel must not call `hiltViewModel` in [Overlay] until
 * `LocalMoreMenuActivated.current` is true. [joinsPreviousGroup] keeps this entry's rows in the group above it.
 */
interface BookMoreMenuEntry {

    /** True when this entry's rows belong to the nearest non-empty group above it, so no thick rule is drawn between them. */
    val joinsPreviousGroup: Boolean get() = false

    /** Emits this entry's rows. [dismissMenu] closes the menu, for a row that opens something. */
    @Composable
    fun Rows(context: BookDetailSectionContext, dismissMenu: () -> Unit)

    /** Hosts whatever this entry shows outside the menu, such as a sheet. */
    @Composable
    fun Overlay(context: BookDetailSectionContext)
}
