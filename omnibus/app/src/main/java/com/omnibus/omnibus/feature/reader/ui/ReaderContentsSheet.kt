package com.omnibus.omnibus.feature.reader.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.feature.bookdetail.stage.BookAccent
import com.omnibus.omnibus.feature.reader.ReaderBookmarksStatus
import com.omnibus.omnibus.feature.reader.ui.ContentsPosition.PageOf
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.EmptyStateView
import com.omnibus.omnibus.ui.components.ErrorStateView
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags for the contents sheet, mirroring [ReaderChromeTags]. */
internal object ReaderContentsTags {
    const val SHEET = "reader_contents_sheet"
    const val LIST = "reader_contents_list"
    const val TITLE = "reader_contents_title"
    const val POSITION = "reader_contents_position"
    const val COVER = "reader_contents_cover"
    const val CLOSE = "reader_contents_close"
    const val EMPTY = "reader_contents_empty"
    const val CURRENT_MARKER = "reader_contents_current_marker"
    const val BOOKMARKS_LIST = "reader_contents_bookmarks_list"
    const val BOOKMARKS_LOADING = "reader_contents_bookmarks_loading"
    const val BOOKMARKS_ERROR = "reader_contents_bookmarks_error"
    const val BOOKMARKS_EMPTY = "reader_contents_bookmarks_empty"

    fun segment(tab: ReaderContentsTab) = "reader_contents_segment_${tab.name}"
    fun row(index: Int) = "reader_contents_row_$index"
    fun label(index: Int) = "reader_contents_label_$index"
    fun page(index: Int) = "reader_contents_page_$index"
    fun divider(index: Int) = "reader_contents_divider_$index"
    fun bookmarkRow(index: Int) = "reader_contents_bookmark_row_$index"
    fun bookmarkPercent(index: Int) = "reader_contents_bookmark_percent_$index"
}

/** The share of the window the sheet takes, nearly full height as iOS `.large`. */
internal const val ContentsSheetHeightFraction = 0.92f

/** The segment's label. */
@StringRes
internal fun ReaderContentsTab.titleRes(): Int = when (this) {
    ReaderContentsTab.Contents -> R.string.reader_contents_tab_contents
    ReaderContentsTab.Bookmarks -> R.string.reader_contents_tab_bookmarks
}

/**
 * The reader's contents sheet: a modal bottom sheet listing the book's table of contents.
 *
 * It is not themed here. The host wraps it in the app theme, so tests and previews choose their own.
 * Swipe down, a tap on the scrim, the close button and system back all arrive as [onDismiss].
 *
 * @param accent The open book's accent, for the active segment and the current chapter.
 * @param onEntryChosen Called with the entry whose row was tapped.
 * @param onDismiss Called when the sheet is dismissed by any means.
 * @param initialTab The segment to open on.
 * @param bookmarks The book's bookmarks, listed on the Bookmarks segment.
 * @param bookmarksStatus Whether [bookmarks] is loading, failed to load, or loaded.
 * @param onBookmarkChosen Called with the bookmark whose row was tapped.
 * @param onBookmarksRetry Called when the Bookmarks segment's Try again is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderContentsSheet(
    title: String,
    cover: CoverIdentity,
    accent: BookAccent,
    toc: List<TocEntry>,
    location: RelocateData?,
    onEntryChosen: (TocEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: ReaderContentsTab = ReaderContentsTab.Contents,
    bookmarks: List<Bookmark> = emptyList(),
    bookmarksStatus: ReaderBookmarksStatus = ReaderBookmarksStatus.Loaded,
    onBookmarkChosen: (Bookmark) -> Unit = {},
    onBookmarksRetry: () -> Unit = {},
) {
    val palette = OmnibusTheme.palette
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.bg0,
        contentColor = palette.ink0,
    ) {
        ReaderContentsSheetContent(
            title = title,
            cover = cover,
            accent = accent,
            toc = toc,
            location = location,
            onEntryChosen = onEntryChosen,
            onDismiss = onDismiss,
            // The 8% left over keeps a strip of scrim above the sheet.
            modifier = Modifier.fillMaxWidth().fillMaxHeight(ContentsSheetHeightFraction),
            initialTab = initialTab,
            bookmarks = bookmarks,
            bookmarksStatus = bookmarksStatus,
            onBookmarkChosen = onBookmarkChosen,
            onBookmarksRetry = onBookmarksRetry,
        )
    }
}

/**
 * The sheet's body without its window: the header, the segmented control and the selected tab.
 * Previews and most tests use this directly.
 *
 * The selected tab survives recomposition and rotation. The list's state is hoisted here, so a
 * new [toc] (for example the one that adds page numbers) keeps the scroll position.
 *
 * @param accent The open book's accent, for the active segment and the current chapter.
 * @param onEntryChosen Called with the entry whose row was tapped.
 * @param onDismiss Called when the close button is tapped.
 * @param bookmarks The book's bookmarks, listed on the Bookmarks segment.
 * @param bookmarksStatus Whether [bookmarks] is loading, failed to load, or loaded.
 * @param onBookmarkChosen Called with the bookmark whose row was tapped.
 * @param onBookmarksRetry Called when the Bookmarks segment's Try again is tapped.
 */
@Composable
internal fun ReaderContentsSheetContent(
    title: String,
    cover: CoverIdentity,
    accent: BookAccent,
    toc: List<TocEntry>,
    location: RelocateData?,
    onEntryChosen: (TocEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: ReaderContentsTab = ReaderContentsTab.Contents,
    bookmarks: List<Bookmark> = emptyList(),
    bookmarksStatus: ReaderBookmarksStatus = ReaderBookmarksStatus.Loaded,
    onBookmarkChosen: (Bookmark) -> Unit = {},
    onBookmarksRetry: () -> Unit = {},
) {
    val palette = OmnibusTheme.palette
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val listState = rememberLazyListState()
    val doneLabel = stringResource(R.string.reader_contents_done)

    Column(modifier.background(palette.bg0).testTag(ReaderContentsTags.SHEET)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.lg, bottom = Spacing.lg),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // The wrapper carries the tag, since BookCover clears its own semantics.
            Box(Modifier.width(46.dp).testTag(ReaderContentsTags.COVER)) {
                BookCover(identity = cover, size = CoverSize.Sm, cornerRadius = 4.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = displayTextStyle(23.sp, palette.ink0),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(ReaderContentsTags.TITLE),
                )
                when (val position = ContentsPosition.from(location)) {
                    null -> Unit
                    is PageOf -> Text(
                        text = stringResource(R.string.reader_contents_page_of, position.page, position.total),
                        style = uiTextStyle(13.sp, FontWeight.Normal, palette.ink2),
                        modifier = Modifier.testTag(ReaderContentsTags.POSITION),
                    )
                    is ContentsPosition.Percent -> Text(
                        text = stringResource(R.string.reader_contents_percent_through, position.pct),
                        style = uiTextStyle(13.sp, FontWeight.Normal, palette.ink2),
                        modifier = Modifier.testTag(ReaderContentsTags.POSITION),
                    )
                }
            }
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(palette.bg2)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = doneLabel }
                    .testTag(ReaderContentsTags.CLOSE),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_reader_close),
                    contentDescription = null,
                    tint = palette.ink1,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.md),
        ) {
            ReaderContentsTab.entries.forEachIndexed { index, segment ->
                SegmentedButton(
                    selected = segment == tab,
                    onClick = { tab = segment },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ReaderContentsTab.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = accent.tintSoft,
                        activeContentColor = palette.ink0,
                        activeBorderColor = palette.ink3,
                        inactiveContainerColor = palette.bg0,
                        inactiveContentColor = palette.ink2,
                        inactiveBorderColor = palette.ink3,
                    ),
                    modifier = Modifier.testTag(ReaderContentsTags.segment(segment)),
                ) {
                    Text(
                        text = stringResource(segment.titleRes()),
                        style = uiTextStyle(14.sp, FontWeight.Medium, LocalContentColor.current),
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                ReaderContentsTab.Contents ->
                    if (toc.isEmpty()) {
                        EmptyStateView(
                            title = stringResource(R.string.reader_contents_empty_title),
                            message = stringResource(R.string.reader_contents_empty_message),
                            modifier = Modifier.testTag(ReaderContentsTags.EMPTY),
                            glyph = R.drawable.ic_reader_contents,
                        )
                    } else {
                        ContentsList(toc, location, accent, listState, onEntryChosen)
                    }
                ReaderContentsTab.Bookmarks -> when (bookmarksStatus) {
                    ReaderBookmarksStatus.Loading -> LoadingStateView(
                        label = stringResource(R.string.reader_bookmarks_loading),
                        modifier = Modifier.testTag(ReaderContentsTags.BOOKMARKS_LOADING),
                    )
                    ReaderBookmarksStatus.Failed -> ErrorStateView(
                        title = stringResource(R.string.reader_bookmarks_error_title),
                        message = stringResource(R.string.reader_bookmarks_error_message),
                        actionLabel = stringResource(R.string.reader_try_again),
                        onRetry = onBookmarksRetry,
                        modifier = Modifier.testTag(ReaderContentsTags.BOOKMARKS_ERROR),
                    )
                    ReaderBookmarksStatus.Loaded ->
                        if (bookmarks.isEmpty()) {
                            EmptyStateView(
                                title = stringResource(R.string.reader_bookmarks_empty_title),
                                message = stringResource(R.string.reader_bookmarks_empty_message),
                                modifier = Modifier.testTag(ReaderContentsTags.BOOKMARKS_EMPTY),
                                glyph = R.drawable.ic_reader_bookmark,
                            )
                        } else {
                            BookmarksList(bookmarks, onBookmarkChosen)
                        }
                }
            }
        }
    }
}

/**
 * The contents rows. The current entry is scrolled to the middle of the list once, when the list
 * first appears, as iOS does. Near either end the scroll stops where the list does.
 */
@Composable
private fun ContentsList(
    toc: List<TocEntry>,
    location: RelocateData?,
    accent: BookAccent,
    listState: LazyListState,
    onEntryChosen: (TocEntry) -> Unit,
) {
    val current = currentTocIndex(toc, location)
    val currentIndex by rememberUpdatedState(current)

    LaunchedEffect(Unit) {
        val index = currentIndex ?: return@LaunchedEffect
        listState.scrollToItem(index)
        // Wait for the layout that contains the item, then pull it up to the middle of the viewport.
        val info = snapshotFlow { listState.layoutInfo }
            .first { layout -> layout.visibleItemsInfo.any { it.index == index } }
        val item = info.visibleItemsInfo.first { it.index == index }
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        listState.scrollBy(-((viewport - item.size) / 2f))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(ReaderContentsTags.LIST),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 32.dp),
    ) {
        // Keyed by index, not label or href (which can repeat), so a replaced toc keeps the scroll.
        items(count = toc.size, key = { it }) { index ->
            ContentsRow(
                index = index,
                entry = toc[index],
                isCurrent = index == current,
                accent = accent,
                onClick = { onEntryChosen(toc[index]) },
            )
        }
    }
}

/** The bookmark rows, in the repository's order. Keyed by index, as the contents list is, so a replaced list keeps its scroll. */
@Composable
private fun BookmarksList(bookmarks: List<Bookmark>, onBookmarkChosen: (Bookmark) -> Unit) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(ReaderContentsTags.BOOKMARKS_LIST),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 32.dp),
    ) {
        items(count = bookmarks.size, key = { it }) { index ->
            BookmarkRow(
                index = index,
                bookmark = bookmarks[index],
                onClick = { onBookmarkChosen(bookmarks[index]) },
            )
        }
    }
}

/** One bookmark row: a hairline above it (none above the first), its label and its percent when known. */
@Composable
private fun BookmarkRow(
    index: Int,
    bookmark: Bookmark,
    onClick: () -> Unit,
) {
    val palette = OmnibusTheme.palette
    val label = bookmarkLabel(bookmark) ?: stringResource(R.string.reader_bookmark_untitled)
    val percent = bookmarkPercent(bookmark)

    Column {
        if (index > 0) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = palette.line,
                modifier = Modifier.testTag(ReaderContentsTags.divider(index)),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .testTag(ReaderContentsTags.bookmarkRow(index))
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = label,
                style = uiTextStyle(15.5.sp, FontWeight.Normal, palette.ink1),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            percent?.let { pct ->
                Text(
                    text = stringResource(R.string.reader_percent, pct),
                    style = uiTextStyle(14.sp, FontWeight.Normal, palette.ink3).copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.testTag(ReaderContentsTags.bookmarkPercent(index)),
                )
            }
        }
    }
}

/**
 * One contents row: a hairline above it (none above the first), a marker column that is reserved
 * on every row, the indented label and the page number.
 */
@Composable
private fun ContentsRow(
    index: Int,
    entry: TocEntry,
    isCurrent: Boolean,
    accent: BookAccent,
    onClick: () -> Unit,
) {
    val palette = OmnibusTheme.palette
    val currentLabel = stringResource(R.string.reader_contents_current_chapter)
    val weight = when {
        isCurrent -> FontWeight.SemiBold
        entry.level == 0 -> FontWeight.Medium
        else -> FontWeight.Normal
    }
    val labelColor = if (isCurrent) accent.tint else palette.ink1
    val pageColor = if (isCurrent) accent.tint else palette.ink3

    Column {
        if (index > 0) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = palette.line,
                modifier = Modifier.testTag(ReaderContentsTags.divider(index)),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    if (isCurrent) stateDescription = currentLabel
                }
                .testTag(ReaderContentsTags.row(index))
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Reserved on every row, so the label never shifts when the current one is marked.
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .size(width = 2.5.dp, height = 15.dp)
                    .then(
                        if (isCurrent) {
                            Modifier.clip(CircleShape).background(accent.tint).testTag(ReaderContentsTags.CURRENT_MARKER)
                        } else {
                            Modifier
                        },
                    ),
            )
            // The indent sits outside the text, so the label's own bounds start where its text starts.
            Box(Modifier.weight(1f).padding(start = (entry.level.coerceAtLeast(0) * 16).dp)) {
                Text(
                    text = entry.label.trim(),
                    style = uiTextStyle(15.5.sp, weight, labelColor),
                    modifier = Modifier.fillMaxWidth().testTag(ReaderContentsTags.label(index)),
                )
            }
            pageText(entry.page)?.let { page ->
                Text(
                    text = page,
                    style = uiTextStyle(14.sp, FontWeight.Normal, pageColor).copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.testTag(ReaderContentsTags.page(index)),
                )
            }
        }
    }
}
