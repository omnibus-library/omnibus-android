package com.omnibus.omnibus.feature.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.R
import kotlinx.serialization.Serializable

/**
 * The tabs of the signed-in shell, in bar order. They match iOS (#124, I-NAV-1): Search takes the place
 * the Authors and Series tabs had on the web client.
 *
 * @property root The destination at the bottom of the tab's back stack.
 * @property label The tab's name in the bar and to screen readers.
 * @property icon Outline glyph for the resting state.
 * @property selectedIcon The filled glyph, or for Search the same glyph at a heavier stroke (P-TAB-3).
 */
enum class ShellTab(
    val root: NavKey,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
) {
    Library(LibraryRoot, R.string.shell_tab_library, R.drawable.ic_tab_library, R.drawable.ic_tab_library_filled),
    Search(SearchRoot, R.string.shell_tab_search, R.drawable.ic_tab_search, R.drawable.ic_tab_search_selected),
    Stats(StatsRoot, R.string.shell_tab_stats, R.drawable.ic_tab_stats, R.drawable.ic_tab_stats_filled),
    You(YouRoot, R.string.shell_tab_you, R.drawable.ic_tab_you, R.drawable.ic_tab_you_filled);

    companion object {
        /** The tab the shell opens on. */
        val Start = Library
    }
}

@Serializable
data object LibraryRoot : NavKey

@Serializable
data object SearchRoot : NavKey

@Serializable
data object StatsRoot : NavKey

@Serializable
data object YouRoot : NavKey
