package com.omnibus.omnibus.feature.library.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.library.LibraryGridItem
import com.omnibus.omnibus.feature.library.LibraryGridMetrics
import com.omnibus.omnibus.feature.library.SeriesGroup
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/** The space below the last row, on top of the bottom overlay (#11). */
private val GRID_BOTTOM_GAP = 40.dp

/**
 * The cover grid of books: series stacks and summaries with their unfold and fold-up animations, cells in
 * [LibraryGridMetrics] columns, load-more reporting and a footer spinner. [header] is a full-width item above
 * the cells.
 *
 * The column count and cell width come from [LibraryGridMetrics] for the width left after the screen insets.
 * Reports the grid items after the last visible one to [onLoadMore], with the footer counted as none.
 *
 * [topInset] and [bottomInset] are the status bar and the bottom overlay the grid scrolls under: they pad the
 * content so it starts and ends clear of them.
 *
 * @param items The grid's entries in order, as [libraryGridItems] builds them.
 * @param unfolded The ids of the series that are unfolded.
 * @param groupBySeries Whether series are grouped. Drives the unfold state's sync and placement.
 * @param onOpenBook Called with the uuid of a tapped book. The caller wraps it to record where the cover was tapped.
 * @param onLoadMore Called with how many grid items follow the last visible one whenever that changes.
 * @param gridTestTag The test tag of the grid.
 * @param onLongPressBook Called with a book whose single or unfolded cell is long-pressed. Null makes long presses do nothing.
 * @param bookMenu The menu anchored to a book's cell, drawn inside that cell.
 * @param header The full-width first item of the grid.
 */
@Composable
internal fun BookGrid(
    items: List<LibraryGridItem>,
    unfolded: Set<Long>,
    groupBySeries: Boolean,
    onOpenBook: (uuid: String) -> Unit,
    onUnfoldSeries: (seriesId: Long) -> Unit,
    onFoldUpSeries: (seriesId: Long) -> Unit,
    onOpenSeries: (seriesId: Long, name: String) -> Unit,
    gridState: LazyGridState,
    topInset: Dp,
    bottomInset: Dp,
    isLoadingMore: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    onLoadMore: (itemsAfterLastVisible: Int) -> Unit = {},
    gridTestTag: String = LibraryTestTags.GRID,
    onLongPressBook: ((Book) -> Unit)? = null,
    bookMenu: @Composable (Book) -> Unit = {},
    header: @Composable () -> Unit,
) {
    val palette = OmnibusTheme.palette

    val unfoldState = rememberSeriesUnfoldState(unfolded, reducedMotion)
    LaunchedEffect(unfolded, groupBySeries) { unfoldState.sync(unfolded, groupBySeries) }
    val folding = unfoldState.folding(unfolded)
    val placement = unfoldState.placementSpec(unfolded, groupBySeries)
    val fade: FiniteAnimationSpec<Float>? = if (reducedMotion) Motion.crossfade() else null
    val density = LocalDensity.current
    val rowGapPx = with(density) { ROW_GAP.toPx() }
    val columnGapPx = with(density) { COLUMN_GAP.toPx() }
    val screenPx = with(density) { Spacing.screen.toPx() }

    val gridItemCount = items.size
    // A folding series keeps its container over its stack's cell, which shrinks back into it.
    val runs = remember(items, folding) {
        items.mapIndexedNotNull { i, item ->
            when (item) {
                is LibraryGridItem.Summary -> OutlineRun(
                    firstGridIndex = i + HEADER_ITEM_COUNT,
                    cellCount = 1 + item.group.count,
                    seriesId = item.group.seriesId,
                )

                is LibraryGridItem.Stack -> if (item.group.seriesId in folding) {
                    OutlineRun(
                        firstGridIndex = i + HEADER_ITEM_COUNT,
                        cellCount = 1 + item.group.count,
                        seriesId = item.group.seriesId,
                    )
                } else {
                    null
                }

                else -> null
            }
        }
    }
    LaunchedEffect(gridState, items) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filterNotNull()
            .filter { it >= HEADER_ITEM_COUNT } // only the header visible: no report, as today
            .map { (HEADER_ITEM_COUNT + gridItemCount - 1 - it).coerceAtLeast(0) } // the footer counts as 0
            .distinctUntilChanged()
            .collect { onLoadMore(it) }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val available = maxWidth - Spacing.screen * 2
        val columns = LibraryGridMetrics.columns(available.value)
        val cellWidth = LibraryGridMetrics.cellWidth(available.value).dp

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = topInset,
                bottom = GRID_BOTTOM_GAP + bottomInset,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .seriesOutlines(
                    gridState,
                    runs,
                    columns,
                    stroke = palette.accent.copy(alpha = 0.40f),
                    fill = palette.seriesFill,
                    frame = { unfoldState.frame(it, unfolded) },
                )
                .testTag(gridTestTag),
        ) {
            item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) {
                header()
            }

            itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                // Rows after the first get 26 dp above them, from the cells, so the heading gap stays 12 dp.
                val rowGap = Modifier
                    .animateItem(fadeInSpec = fade, placementSpec = placement, fadeOutSpec = fade)
                    .padding(top = if (index >= columns) 26.dp else 0.dp)
                // The slot of member [member] of [group] relative to this item, which is the series' anchor cell.
                fun slotOf(group: SeriesGroup, member: Int) = memberCoverOffset(
                    gridState.layoutInfo,
                    index + HEADER_ITEM_COUNT,
                    member,
                    LibraryGridItem.Member(group, group.books[member]).key,
                    columns,
                    rowGapPx,
                    columnGapPx,
                    screenPx,
                )
                when (item) {
                    is LibraryGridItem.Single -> BookGridCell(
                        book = item.book,
                        width = cellWidth,
                        onClick = { onOpenBook(item.book.uuid) },
                        modifier = rowGap,
                        onLongClick = onLongPressBook?.let { press -> { press(item.book) } },
                        menu = { bookMenu(item.book) },
                    )

                    is LibraryGridItem.Stack -> {
                        val id = item.group.seriesId
                        SeriesGhostHost(
                            ghosting = unfoldState.ghosting(id, unfolded),
                            group = item.group,
                            width = cellWidth,
                            t = { unfoldState.t(id, unfolded) ?: if (id in unfolded) 1f else 0f },
                            memberOffset = { i -> slotOf(item.group, i) },
                            modifier = rowGap,
                        ) { hidden ->
                            SeriesStackCell(
                                group = item.group,
                                width = cellWidth,
                                onUnfold = { onUnfoldSeries(item.group.seriesId) },
                                modifier = hidden,
                            )
                        }
                    }

                    is LibraryGridItem.Summary -> {
                        val id = item.group.seriesId
                        SeriesGhostHost(
                            ghosting = unfoldState.ghosting(id, unfolded),
                            group = item.group,
                            width = cellWidth,
                            t = { unfoldState.t(id, unfolded) ?: if (id in unfolded) 1f else 0f },
                            memberOffset = { i -> slotOf(item.group, i) },
                            modifier = rowGap,
                        ) { hidden ->
                            SeriesSummaryCell(
                                group = item.group,
                                width = cellWidth,
                                onOpenSeries = { onOpenSeries(item.group.seriesId, item.group.name) },
                                onFoldUp = { onFoldUpSeries(item.group.seriesId) },
                                modifier = hidden,
                            )
                        }
                    }

                    is LibraryGridItem.Member -> BookGridCell(
                        book = item.book,
                        width = cellWidth,
                        onClick = { onOpenBook(item.book.uuid) },
                        modifier = if (unfoldState.ghosting(item.group.seriesId, unfolded)) rowGap.alpha(0f) else rowGap,
                        subtitle = memberSubtitle(item.book),
                        onLongClick = onLongPressBook?.let { press -> { press(item.book) } },
                        menu = { bookMenu(item.book) },
                    )
                }
            }

            if (isLoadingMore) {
                item(key = FOOTER_KEY, span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 26.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = palette.ink3,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp).testTag(LibraryTestTags.FOOTER_SPINNER),
                        )
                    }
                }
            }
        }
    }
}
