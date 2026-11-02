package com.omnibus.omnibus.feature.bookdetail.tabs

import com.omnibus.omnibus.feature.bookdetail.BookDetailTab

/**
 * How far the stage wash is panned for [tab], as a fraction of its width. The tabs step the pan from the right
 * (Overview) to the left (Files), as iOS `BookTab.backdropPan` does.
 */
internal fun tabWashPan(tab: BookDetailTab): Float = (1.5f - tab.ordinal) * 0.11f

/** The stage glow's opacity for [tab] (iOS `BookTab.warmth`). */
internal fun tabWashWarmth(tab: BookDetailTab): Float = when (tab) {
    BookDetailTab.Overview, BookDetailTab.Reviews -> 0.5f
    BookDetailTab.Highlights -> 1f
    BookDetailTab.Files -> 0.1f
}
