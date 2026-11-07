package com.omnibus.omnibus.feature.bookdetail.tabs

import com.omnibus.omnibus.feature.bookdetail.BookDetailTab

/** Test tags for the tab strip, its ribbon and its panel. */
internal object BookDetailTabsTestTags {
    const val STRIP = "book-detail-tabs-strip"
    const val RIBBON = "book-detail-tabs-ribbon"
    const val PANEL = "book-detail-tabs-panel"
    const val PANEL_VIEWPORT = "book-detail-tabs-panel-viewport"

    fun tab(tab: BookDetailTab) = "book-detail-tabs-tab-${tab.name}"
    fun tabLabel(tab: BookDetailTab) = "book-detail-tabs-label-${tab.name}"
}
