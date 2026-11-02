package com.omnibus.omnibus.feature.bookdetail

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.omnibus.omnibus.feature.bookdetail.tabs.BookDetailAnchorElement

/** The tabs of the Book Detail page, in strip order. */
enum class BookDetailTab { Overview, Reviews, Highlights, Files }

/** The places within a tab that another part of the page can scroll to. */
enum class BookDetailAnchor { Series }

/**
 * Drives the tab strip. [jumpTo] selects the tab and brings the strip to the top under the bar.
 * [scrollTo] selects the anchor's tab and lands the anchored element below the pinned strip.
 * The page's real controller is in `tabs/BookDetailTabState.kt`.
 */
@Stable
interface BookDetailTabController {
    val selected: BookDetailTab
    fun select(tab: BookDetailTab)
    fun jumpTo(tab: BookDetailTab)
    fun scrollTo(anchor: BookDetailAnchor)
}

/**
 * Marks the element that [anchor] scrolls to. The element reports its position to the tab panel that holds it; outside
 * a panel the modifier does nothing.
 */
fun Modifier.bookDetailAnchor(anchor: BookDetailAnchor): Modifier = this then BookDetailAnchorElement(anchor)

/** A controller that never moves. Previews and tests that render a section without the page use it. */
internal object InertBookDetailTabController : BookDetailTabController {
    override val selected: BookDetailTab = BookDetailTab.Overview
    override fun select(tab: BookDetailTab) = Unit
    override fun jumpTo(tab: BookDetailTab) = Unit
    override fun scrollTo(anchor: BookDetailAnchor) = Unit
}
