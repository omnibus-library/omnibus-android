package com.omnibus.omnibus.feature.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.R
import kotlinx.serialization.Serializable

/**
 * The tabs of the signed-in shell, in bar order.
 *
 * These are the web client's tabs rather than iOS's, which swaps Authors and Series for Search.
 *
 * @property root The destination at the bottom of the tab's back stack.
 * @property label The tab's name in the bar and to screen readers.
 * @property icon Outline glyph for the resting state.
 * @property selectedIcon Filled glyph for the selected state.
 */
enum class ShellTab(
    val root: NavKey,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
) {
    Library(LibraryRoot, R.string.shell_tab_library, R.drawable.ic_tab_library, R.drawable.ic_tab_library_filled),
    Authors(AuthorsRoot, R.string.shell_tab_authors, R.drawable.ic_tab_authors, R.drawable.ic_tab_authors_filled),
    Series(SeriesRoot, R.string.shell_tab_series, R.drawable.ic_tab_series, R.drawable.ic_tab_series_filled),
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
data object AuthorsRoot : NavKey

@Serializable
data object SeriesRoot : NavKey

@Serializable
data object StatsRoot : NavKey

@Serializable
data object YouRoot : NavKey
