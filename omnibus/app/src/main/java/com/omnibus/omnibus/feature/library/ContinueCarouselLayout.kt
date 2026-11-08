package com.omnibus.omnibus.feature.library

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.Spacing

/** The width of the cover column on a Continue card. */
internal val ContinueCoverWidth = 96.dp

/** The padding inside a Continue card, on each side. */
internal val ContinueCardPadding = 16.dp

/** The gap between the cover and the text column of a Continue card. */
internal val ContinueCardContentGap = 16.dp

/** The gap between two Continue cards that share a page. */
internal val ContinuePageCardGap = 16.dp

/** The narrowest text column of a Continue card at font scale 1: "Chapter 888 of 888", 8 dp and the "Read" pill, 180.6 dp. */
internal val ContinueCardMinTextWidth = 184.dp

/** The most Continue cards on one page. */
internal const val MAX_CONTINUE_CARDS_PER_PAGE = 2

/** The narrowest a Continue card can be at [fontScale], so its text column still fits its footer. */
internal fun continueCardMinWidth(fontScale: Float): Dp =
    ContinueCardPadding * 2 + ContinueCoverWidth + ContinueCardContentGap + ContinueCardMinTextWidth * fontScale

/** How many cards share a page in a carousel [width] wide: two once both fit at their minimum width, else one. */
internal fun continueCardsPerPage(width: Dp, fontScale: Float): Int =
    if (width >= continueCardMinWidth(fontScale) * MAX_CONTINUE_CARDS_PER_PAGE + ContinuePageCardGap + Spacing.screen * 2) {
        MAX_CONTINUE_CARDS_PER_PAGE
    } else {
        1
    }

/** The number of pages [cardCount] cards take with [cardsPerPage] to a page, or 0 for no cards. */
internal fun continuePageCount(cardCount: Int, cardsPerPage: Int): Int {
    if (cardCount <= 0) return 0
    val perPage = cardsPerPage.coerceAtLeast(1)
    return (cardCount + perPage - 1) / perPage
}

/** The cards grouped in order, [cardsPerPage] to a page; the last page holds the rest. */
internal fun <T> continuePages(cards: List<T>, cardsPerPage: Int): List<List<T>> =
    cards.chunked(cardsPerPage.coerceAtLeast(1))

/** The page that holds the card [id] when the cards are grouped [cardsPerPage] to a page, or -1 when it is not listed. */
internal fun pageOfCard(id: String?, ids: List<String>, cardsPerPage: Int): Int {
    val index = if (id == null) -1 else ids.indexOf(id)
    return if (index < 0) -1 else index / cardsPerPage.coerceAtLeast(1)
}
