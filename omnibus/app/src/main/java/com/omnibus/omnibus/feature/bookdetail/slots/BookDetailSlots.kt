package com.omnibus.omnibus.feature.bookdetail.slots

import androidx.compose.runtime.Immutable
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection

/**
 * The page's slot lists. The screen reads slots only through this, so tests can inject fakes and no ticket
 * has to edit the screen to add a section. [Registered] holds the real registries.
 */
@Immutable
data class BookDetailSlots(
    val stageAboveTitle: List<BookDetailSection> = emptyList(),
    val stageStatusStart: List<BookDetailSection> = emptyList(),
    val stageStatusEnd: List<BookDetailSection> = emptyList(),
    val stageProgress: List<BookDetailSection> = emptyList(),
    val stageCoverOverlay: List<BookDetailSection> = emptyList(),
    val topBarActions: List<BookDetailSection> = emptyList(),
    val overviewSections: List<BookDetailSection> = emptyList(),
    val reviewsSections: List<BookDetailSection> = emptyList(),
    val highlightsSections: List<BookDetailSection> = emptyList(),
    val filesSections: List<BookDetailSection> = emptyList(),
    val actionBar: List<BookDetailSection> = emptyList(),
    val screenOverlays: List<BookDetailSection> = emptyList(),
) {
    companion object {
        val Registered = BookDetailSlots(
            stageAboveTitle = stageAboveTitle,
            stageStatusStart = stageStatusStart,
            stageStatusEnd = stageStatusEnd,
            stageProgress = stageProgress,
            stageCoverOverlay = stageCoverOverlay,
            topBarActions = topBarActions,
            overviewSections = overviewSections,
            reviewsSections = reviewsSections,
            highlightsSections = highlightsSections,
            filesSections = filesSections,
            actionBar = actionBar,
            screenOverlays = screenOverlays,
        )
    }
}
