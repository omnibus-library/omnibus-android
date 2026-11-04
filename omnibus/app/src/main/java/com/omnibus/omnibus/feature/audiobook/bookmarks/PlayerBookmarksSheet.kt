package com.omnibus.omnibus.feature.audiobook.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarkRow
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarkRowModel
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarksContent
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarksSheetGround
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.StateActionButton
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.coroutines.launch

/** The meta text's colour, as in `BookmarksSheet.kt`. */
private val SheetMuted = Color(0xFFC8C4BE)

/** The empty copy's colour, as in `BookmarksSheet.kt`. */
private val EmptyInk = Color(0xFFDBD7D1)

/**
 * The ribbon colour of the player's rows: the cover's tone at lightness .62, the formula [bookmarkRibbonColor] uses for Book
 * Detail. The player holds the cover identity, not the book.
 */
internal fun playerRibbonColor(cover: CoverIdentity): Color = cover.tone.let { Oklch(0.62, it.c, it.h).toColor() }

/**
 * The player's bookmarks sheet (P-BMK-2), opened from the top bar. Its window is Book Detail's sheet. It hides first and then
 * reports, so the player can seek or open the reader after the sheet has gone. Later taps during the hide are ignored.
 *
 * @param bookUuid The book the player is showing.
 * @param cover The cover identity that gives the rows their ribbon colour.
 * @param onDismiss Clears the overlay. Also called for a swipe, the scrim or system back.
 * @param onOpenReader Opens the reader at a text row's place, after [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerBookmarksSheet(
    bookUuid: String,
    cover: CoverIdentity,
    onDismiss: () -> Unit,
    onOpenReader: (bookUuid: String, startCfi: String?) -> Unit,
    viewModel: PlayerBookmarksViewModel = hiltViewModel<PlayerBookmarksViewModel, PlayerBookmarksViewModel.Factory>(
        creationCallback = { it.create(bookUuid) },
    ),
) {
    val palette = OmnibusTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(false) }
    val uiState by viewModel.sheetState.collectAsState()
    val ribbon = remember(cover) { playerRibbonColor(cover) }
    val onChosen: (BookmarkRowModel) -> Unit = { row ->
        if (!chosen) {
            chosen = true
            scope.launch { sheetState.hide() }.invokeOnCompletion { cause ->
                if (cause == null) {
                    onDismiss()
                    when (val tap = playerBookmarkTap(row)) {
                        is PlayerBookmarkTap.Seek -> viewModel.onListeningBookmarkChosen(tap.seconds)
                        is PlayerBookmarkTap.OpenReader -> onOpenReader(bookUuid, tap.startCfi)
                    }
                } else {
                    chosen = false
                }
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = BookmarksSheetGround,
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 5.dp, color = palette.line2) },
    ) {
        PlayerBookmarksSheetContent(
            content = uiState.content,
            nowMillis = uiState.nowMillis,
            ribbonColor = ribbon,
            onRetry = viewModel::onRetry,
            onClose = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) onDismiss()
                }
            },
            onChosen = onChosen,
            onRemove = viewModel::onBookmarkRemoved,
        )
    }
}

/**
 * The sheet's body, without the sheet window, so tests and previews can drive it. The title row stays fixed and the rows
 * scroll beneath it. The body is always dark, as Book Detail's is.
 *
 * @param onChosen Called with the row a tap chose.
 * @param onRemove Called with a row that was swiped away, or whose "Remove bookmark" action was used.
 */
@Composable
internal fun PlayerBookmarksSheetContent(
    content: BookmarksContent,
    nowMillis: Long,
    ribbonColor: Color,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onChosen: (BookmarkRowModel) -> Unit,
    onRemove: (BookmarkRowModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    OmnibusTheme(darkTheme = true) {
        val palette = OmnibusTheme.palette
        Box(
            modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .background(BookmarksSheetGround)
                .testTag(PlayerBookmarksTestTags.SHEET),
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.book_detail_bookmarks_title),
                        style = displayTextStyle(28.sp, palette.ink0, FontWeight.SemiBold),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() }
                            .testTag(PlayerBookmarksTestTags.TITLE),
                    )
                    CloseButton(onClose)
                }
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    when (content) {
                        BookmarksContent.Loading -> LoadingBody()
                        BookmarksContent.Failed -> FailedBody(onRetry)
                        is BookmarksContent.Loaded -> {
                            if (content.rows.isEmpty()) {
                                EmptyBody()
                            } else {
                                PlayerBookmarkList(content.rows, nowMillis, ribbonColor, onChosen, onRemove)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The 44 dp close button at the title's end, as in Book Detail's sheet. */
@Composable
private fun CloseButton(onClose: () -> Unit) {
    Box(
        Modifier
            .testTag(PlayerBookmarksTestTags.CLOSE)
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_reader_close),
            contentDescription = stringResource(R.string.book_detail_bookmarks_close),
            tint = OmnibusTheme.palette.ink0,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The spinner shown while the bookmarks are read. */
@Composable
private fun LoadingBody() {
    val palette = OmnibusTheme.palette
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp).testTag(PlayerBookmarksTestTags.LOADING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = palette.ink2,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.book_detail_bookmarks_loading),
            style = uiTextStyle(14, FontWeight.Normal, palette.ink2),
        )
    }
}

/** The read failure, with a retry. */
@Composable
private fun FailedBody(onRetry: () -> Unit) {
    val palette = OmnibusTheme.palette
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp).testTag(PlayerBookmarksTestTags.ERROR),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.book_detail_bookmarks_error),
            style = uiTextStyle(15, FontWeight.Normal, palette.ink0),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        StateActionButton(label = stringResource(R.string.book_detail_bookmarks_retry), onClick = onRetry)
    }
}

/** The empty state: the plain paragraph, with no list and no footer. */
@Composable
private fun EmptyBody() {
    Text(
        text = stringResource(R.string.book_detail_bookmarks_empty),
        style = uiTextStyle(16, FontWeight.Normal, EmptyInk),
        modifier = Modifier.padding(top = 4.dp).testTag(PlayerBookmarksTestTags.EMPTY),
    )
}

/** The loaded rows, each swipeable to remove, with the swipe hint under them. */
@Composable
private fun PlayerBookmarkList(
    rows: List<BookmarkRowModel>,
    nowMillis: Long,
    ribbonColor: Color,
    onChosen: (BookmarkRowModel) -> Unit,
    onRemove: (BookmarkRowModel) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(PlayerBookmarksTestTags.LIST),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        items(rows, key = { it.key }) { row ->
            SwipeableBookmarkRow(row, nowMillis, ribbonColor, onChosen, onRemove)
        }
        item(key = "footer") {
            Text(
                text = stringResource(R.string.audiobook_bookmarks_swipe_hint),
                style = uiTextStyle(13, FontWeight.Normal, SheetMuted),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag(PlayerBookmarksTestTags.FOOTER),
            )
        }
    }
}

/**
 * One row that swipes away end to start. The swipe state is remembered within the item, not saved, so a row that comes back
 * after a failed removal starts unswiped. The remove action is also a custom accessibility action (P-A11Y-5).
 */
@Composable
private fun SwipeableBookmarkRow(
    row: BookmarkRowModel,
    nowMillis: Long,
    ribbonColor: Color,
    onChosen: (BookmarkRowModel) -> Unit,
    onRemove: (BookmarkRowModel) -> Unit,
) {
    val palette = OmnibusTheme.palette
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val swipeState = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    val removeLabel = stringResource(R.string.audiobook_bookmarks_remove)
    // The dismiss callback is restarted by SwipeToDismissBox whenever its identity changes, so it must stay the same
    // instance while the row is settled. The current row and remover are read through state instead.
    val currentRow by rememberUpdatedState(row)
    val currentOnRemove by rememberUpdatedState(onRemove)
    val onDismiss: (SwipeToDismissBoxValue) -> Unit = remember {
        { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) currentOnRemove(currentRow)
        }
    }
    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(palette.bad)
                    .padding(end = 24.dp)
                    .testTag(PlayerBookmarksTestTags.removeBackground(row.key)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_book_detail_more_delete),
                    contentDescription = null,
                    tint = BookmarksSheetGround,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        modifier = Modifier.testTag(PlayerBookmarksTestTags.swipe(row.key)),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        onDismiss = onDismiss,
    ) {
        Box(Modifier.fillMaxWidth().background(BookmarksSheetGround)) {
            BookmarkRow(
                row = row,
                nowMillis = nowMillis,
                ribbonColor = ribbonColor,
                onChosen = onChosen,
                modifier = Modifier.semantics {
                    customActions = listOf(CustomAccessibilityAction(removeLabel) { onRemove(row); true })
                },
            )
        }
    }
}

/** Sample bookmarks for the previews, through the same rules the player uses. */
private val previewRows = playerBookmarkRows(
    "preview",
    listOf(
        Bookmark(1, null, "preview", "epubcfi(/6/14!/4/2)", "The Episode of the Barrel", "Chapter 7", 0.47f, 1_761_609_600L),
        Bookmark(2, null, "preview", "4332.5", null, null, null, 1_761_609_600L),
        Bookmark(3, null, "preview", "120", null, null, null, null),
    ),
)

@PreviewLightDark
@Composable
private fun PlayerBookmarksSheetContentPreview() {
    OmnibusTheme {
        PlayerBookmarksSheetContent(
            content = BookmarksContent.Loaded(previewRows),
            nowMillis = 1_762_084_800_000L,
            ribbonColor = Color(0xFFB8864A),
            onRetry = {},
            onClose = {},
            onChosen = {},
            onRemove = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PlayerBookmarksSheetEmptyPreview() {
    OmnibusTheme {
        PlayerBookmarksSheetContent(
            content = BookmarksContent.Loaded(emptyList()),
            nowMillis = 1_762_084_800_000L,
            ribbonColor = Color(0xFFB8864A),
            onRetry = {},
            onClose = {},
            onChosen = {},
            onRemove = {},
        )
    }
}
