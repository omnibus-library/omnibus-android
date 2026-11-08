package com.omnibus.omnibus.feature.bookdetail.stage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.InertBookDetailTabController
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.feature.bookdetail.ui.StageRestTop
import com.omnibus.omnibus.feature.bookdetail.ui.TopBarRowHeight
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.feature.library.ui.isLightGround
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.rememberReducedMotion

/**
 * The top of the page: the wash, the cover, the title, the byline and the slot rows under them.
 *
 * @param slots The sections to place. Each one gets the same [context].
 * @param backdrop Whether the stage draws its own wash. Two-pane mode draws one wash behind both panes instead.
 * @param underBar Whether the cover rests in the bar row. False adds the bar row's height above it.
 * @param coverScale The cover's scale, see [stageCoverScale].
 * @param contentModifier Applied inside the stage's padding, around the cover and the rows under it.
 */
@Composable
internal fun BookStage(
    context: BookDetailSectionContext,
    slots: BookDetailSlots,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    backdrop: Boolean = true,
    underBar: Boolean = true,
    coverScale: Float = 1f,
    contentModifier: Modifier = Modifier,
) {
    val lightGround = isLightGround(OmnibusTheme.palette.bg0)
    val byline = remember(context.book) { bylineModel(context.book) }

    Box(modifier.fillMaxWidth().testTag(BookDetailTestTags.STAGE)) {
        if (backdrop) {
            BookStageWash(context.book.coverIdentity(), lightGround, Modifier.matchParentSize(), reducedMotion = reducedMotion)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = StageRestTop + (if (underBar) 0.dp else TopBarRowHeight),
                    bottom = 14.dp,
                )
                .then(contentModifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BookStageCover(context.book, lightGround, scale = coverScale) {
                slots.stageCoverOverlay.forEach { it.Content(context, Modifier) }
            }
            slots.stageAboveTitle.forEach { it.Content(context, Modifier) }
            Spacer(Modifier.height(24.dp))
            BookStageTitle(context.book.displayTitle)
            Spacer(Modifier.height(2.dp))
            BookByline(byline, context.onNavigate, seriesLine = com.omnibus.omnibus.feature.bookdetail.overview.series.rememberSeriesLine(context))
            Row(
                Modifier.fillMaxWidth().testTag(BookDetailTestTags.STATUS_ROW),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                slots.stageStatusStart.forEach { it.Content(context, Modifier) }
                Spacer(Modifier.weight(1f))
                slots.stageStatusEnd.forEach { it.Content(context, Modifier) }
            }
            slots.stageProgress.forEach { it.Content(context, Modifier) }
        }
    }
}

@PreviewLightDark
@Composable
private fun BookStagePreview() {
    val book = Book(
        id = 1,
        uniqueIdentifier = "uuid-1",
        filename = "the-left-hand-of-darkness.epub",
        title = "The Left Hand of Darkness",
        creators = listOf(Creator("Ursula K. Le Guin", "author", null, 7L)),
        coverUrl = null,
        accent = null,
        formats = listOf("epub"),
        series = null,
        seriesIndex = null,
        hasPhysical = false,
        addedAt = null,
        lastInteractedAt = null,
        bookFiles = emptyList(),
        publishedDate = "1969",
    )
    val accent = bookAccent(book.coverIdentity().tone, lightGround = false)
    val context = BookDetailSectionContext(
        book = book,
        accent = accent,
        reloadKey = 0,
        tabs = InertBookDetailTabController,
        onNavigate = { _: BookDetailDestination -> },
        onShowMessage = {},
        onBookChanged = {},
        onReloadBook = {},
        onClose = {},
    )
    OmnibusTheme {
        BookStage(context, BookDetailSlots(), reducedMotion = true)
    }
}
