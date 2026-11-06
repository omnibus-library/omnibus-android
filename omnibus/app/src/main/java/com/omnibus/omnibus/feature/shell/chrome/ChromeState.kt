package com.omnibus.omnibus.feature.shell.chrome

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute
import com.omnibus.omnibus.feature.shell.ShellTab

/** Whether the chrome and the FAB show, derived from navigation and never stored (tech spec §5.3). */
data class ChromeState(val visible: Boolean, val fabVisible: Boolean) {
    companion object {
        /**
         * Hidden while Book Detail is anywhere in [selectedStack], not just on top (I-STATE-1, P-VIS-1). The FAB shows on
         * Library's root only (P-FAB-3).
         */
        fun of(selected: ShellTab, selectedStack: List<NavKey>): ChromeState {
            val visible = selectedStack.none { it is BookDetailRoute }
            return ChromeState(visible, visible && selected == ShellTab.Library && selectedStack.size == 1)
        }
    }
}

/** A tab root's floating action (I-STATE-2). */
data class FabSpec(@param:DrawableRes val icon: Int, @param:StringRes val label: Int, val onClick: () -> Unit)
