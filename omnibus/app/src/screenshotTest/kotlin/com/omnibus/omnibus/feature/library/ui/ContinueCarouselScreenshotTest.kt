package com.omnibus.omnibus.feature.library.ui

import android.content.res.Configuration
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.feature.library.ContinueCard
import com.omnibus.omnibus.feature.library.PositionLabel
import com.omnibus.omnibus.feature.library.RelativeAge
import com.omnibus.omnibus.feature.library.continuePageCount
import com.omnibus.omnibus.screenshot.Shot

/** The eyebrow, chapter label, Play pill and progress bar of a listening card. */
@PreviewTest
@PreviewLightDark
@Composable
fun ContinueCardListening() {
    CarouselShot(
        card(1L, ProgressFormat.Listening, fraction = 0.4, position = PositionLabel.Chapter(3, 12)),
    )
}

/** The eyebrow, percent label and Read pill of a reading card. */
@PreviewTest
@PreviewLightDark
@Composable
fun ContinueCardReading() {
    CarouselShot(card(2L, ProgressFormat.Reading, fraction = 0.42, position = PositionLabel.PercentRead(42)))
}

/** A card with no fraction shows "Last opened" in place of the bar. */
@PreviewTest
@PreviewLightDark
@Composable
fun ContinueCardLastOpened() {
    CarouselShot(
        card(3L, ProgressFormat.Reading, fraction = null, position = PositionLabel.InProgress, lastOpened = RelativeAge.Minutes(5)),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun ContinueCardPart() {
    CarouselShot(card(4L, ProgressFormat.Listening, fraction = 0.3, position = PositionLabel.Part(2, 4)))
}

/** Three cards: one dot each, the selected first one a wider capsule. */
@PreviewTest
@PreviewLightDark
@Composable
fun ContinueCarouselDots() {
    CarouselShot(
        card(1L, ProgressFormat.Listening, fraction = 0.4, position = PositionLabel.Chapter(3, 12)),
        card(2L, ProgressFormat.Reading, fraction = 0.42, position = PositionLabel.PercentRead(42)),
        card(3L, ProgressFormat.Reading, fraction = null, position = PositionLabel.InProgress),
    )
}

/** A wide carousel: two cards share page 0, and the dots count pages. */
@PreviewTest
@Preview(name = "Light", widthDp = 800)
@Preview(name = "Dark", widthDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ContinueCarouselTwoUp() {
    CarouselShot(
        card(1L, ProgressFormat.Listening, fraction = 0.4, position = PositionLabel.Chapter(3, 12)),
        card(2L, ProgressFormat.Reading, fraction = 0.42, position = PositionLabel.PercentRead(42)),
        card(3L, ProgressFormat.Reading, fraction = null, position = PositionLabel.InProgress),
        cardsPerPage = 2,
    )
}

/** The last page of three on a wide carousel: the lone card keeps a full page's card width, at the start. */
@PreviewTest
@Preview(name = "Light", widthDp = 800)
@Preview(name = "Dark", widthDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ContinueCarouselLoneCard() {
    CarouselShot(
        card(1L, ProgressFormat.Listening, fraction = 0.4, position = PositionLabel.Chapter(3, 12)),
        card(2L, ProgressFormat.Reading, fraction = 0.42, position = PositionLabel.PercentRead(42)),
        card(3L, ProgressFormat.Reading, fraction = null, position = PositionLabel.InProgress),
        selectedIndex = 2,
        initialPage = 1,
        cardsPerPage = 2,
    )
}

private fun card(
    id: Long,
    medium: ProgressFormat,
    fraction: Double?,
    position: PositionLabel,
    lastOpened: RelativeAge = RelativeAge.Hours(2),
) = ContinueCard(
    id = "b$id:${if (medium == ProgressFormat.Listening) "audio" else "epub"}",
    book = Book(
        id = id,
        uniqueIdentifier = "b$id",
        filename = "book-$id.epub",
        title = "Book $id",
        creators = listOf(Creator("Author $id", null, null, null)),
        coverUrl = null,
        accent = null,
        formats = listOf("epub", "m4b"),
        series = null,
        seriesIndex = null,
        hasPhysical = false,
        addedAt = null,
        lastInteractedAt = null,
        bookFiles = emptyList(),
    ),
    medium = medium,
    fraction = fraction,
    position = position,
    lastOpened = lastOpened,
    resumeFileId = null,
)

/**
 * The carousel on [initialPage], grouped [cardsPerPage] to a page, with the card at [selectedIndex] selected. Every card
 * counts as drawn in, so the bars show at full length, not mid-animation.
 */
@Composable
private fun CarouselShot(
    vararg cards: ContinueCard,
    selectedIndex: Int = 0,
    initialPage: Int = 0,
    cardsPerPage: Int = 1,
) {
    val list = cards.toList()
    val perPage = remember { mutableIntStateOf(cardsPerPage) }
    val pagerState = rememberPagerState(initialPage = initialPage) { continuePageCount(list.size, perPage.intValue) }
    val state = remember { ContinueCarouselState(pagerState, list.map { it.id }.toMutableStateList(), perPage) }
    Shot {
        ContinueCarousel(
            cards = list,
            selectedId = list[selectedIndex].id,
            state = state,
            onSettled = {},
            onOpenBook = {},
            onResume = {},
            onShowContextMenu = {},
        )
    }
}
