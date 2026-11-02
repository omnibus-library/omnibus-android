package com.omnibus.omnibus.feature.bookdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/** The tabs of the Book Detail page, in strip order. */
enum class BookDetailTab { Overview, Reviews, Highlights, Files }

/** The places within a tab that another part of the page can scroll to. */
enum class BookDetailAnchor { Series }

/**
 * Drives the tab strip. [jumpTo] selects the tab and brings the strip to the top under the bar.
 * [scrollTo] selects the anchor's tab and lands the anchored element below the pinned strip.
 * #93 ships an inert controller and a no-op [bookDetailAnchor]; #94 replaces both.
 */
@Stable
interface BookDetailTabController {
    val selected: BookDetailTab
    fun select(tab: BookDetailTab)
    fun jumpTo(tab: BookDetailTab)
    fun scrollTo(anchor: BookDetailAnchor)
}

/** Marks the element that [anchor] scrolls to. A no-op until #94. */
fun Modifier.bookDetailAnchor(anchor: BookDetailAnchor): Modifier = this

/** A controller that never moves, so the page shows Overview until #94 mounts the real tabs. */
internal object InertBookDetailTabController : BookDetailTabController {
    override val selected: BookDetailTab = BookDetailTab.Overview
    override fun select(tab: BookDetailTab) = Unit
    override fun jumpTo(tab: BookDetailTab) = Unit
    override fun scrollTo(anchor: BookDetailAnchor) = Unit
}

/** The tab controller the page hands to its sections. */
@Composable
fun rememberBookDetailTabController(): BookDetailTabController = InertBookDetailTabController
