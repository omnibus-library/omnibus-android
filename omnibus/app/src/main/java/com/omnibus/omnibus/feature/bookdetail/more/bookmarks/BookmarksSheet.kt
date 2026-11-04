package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.highlights.HighlightRibbonShape
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.components.StateActionButton
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.TimeZone
import kotlinx.coroutines.launch

/** The sheet's ground, the design's #15120f. It stays dark in both app themes. */
internal val BookmarksSheetGround = Color(0xFF15120F)

/** The meta line's colour (the design's #c8c4be). */
private val SheetMuted = Color(0xFFC8C4BE)

/** The empty copy's colour (the design's #dbd7d1). */
private val EmptyInk = Color(0xFFDBD7D1)

/** The hairline above each row, rgba(247, 244, 239, .1). */
private val RowRule = Color(0xFFF7F4EF).copy(alpha = 0.10f)

/**
 * The ribbon colour of a book's rows: its cover tone at lightness .62, which reads on the dark ground.
 */
internal fun bookmarkRibbonColor(book: Book): Color =
    book.coverIdentity().tone.let { Oklch(0.62, it.c, it.h).toColor() }

/**
 * The Bookmarks bottom sheet (#117). It hides first and then reports the close, so the page can open a bookmark
 * after the sheet has gone. A row tap closes the sheet and then sends its destination to [onOpen]. Later taps during
 * the hide are ignored.
 *
 * @param onClose Closes the sheet's state. Also called for a swipe, the scrim or system back.
 * @param onOpen Opens a tapped bookmark's destination, after [onClose].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarksSheet(
    content: BookmarksContent,
    nowMillis: Long,
    ribbonColor: Color,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onOpen: (BookDetailDestination) -> Unit,
) {
    val palette = OmnibusTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(false) }
    val onChosen: (BookmarkRowModel) -> Unit = { row ->
        if (!chosen) {
            chosen = true
            scope.launch { sheetState.hide() }.invokeOnCompletion { cause ->
                if (cause == null) {
                    onClose()
                    onOpen(row.destination)
                } else {
                    chosen = false
                }
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = BookmarksSheetGround,
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 5.dp, color = palette.line2) },
    ) {
        BookmarksSheetContent(
            content = content,
            nowMillis = nowMillis,
            ribbonColor = ribbonColor,
            onRetry = onRetry,
            onClose = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) onClose()
                }
            },
            onChosen = onChosen,
        )
    }
}

/**
 * The sheet's body, without the sheet window, so tests and previews can drive it. The title row stays fixed and the
 * rows scroll beneath it. The body is always dark: it paints its own ground in either app theme.
 *
 * @param onChosen Called with the row a tap chose.
 */
@Composable
internal fun BookmarksSheetContent(
    content: BookmarksContent,
    nowMillis: Long,
    ribbonColor: Color,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onChosen: (BookmarkRowModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    OmnibusTheme(darkTheme = true) {
        val palette = OmnibusTheme.palette
        Box(
            modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .background(BookmarksSheetGround)
                .testTag(BookmarksTestTags.SHEET),
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
                            .testTag(BookmarksTestTags.TITLE),
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
                                BookmarkList(content.rows, nowMillis, ribbonColor, onChosen)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The 44 dp close button at the title's end. */
@Composable
private fun CloseButton(onClose: () -> Unit) {
    Box(
        Modifier
            .testTag(BookmarksTestTags.CLOSE)
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
        Modifier.fillMaxWidth().padding(top = 48.dp).testTag(BookmarksTestTags.LOADING),
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
        Modifier.fillMaxWidth().padding(top = 48.dp).testTag(BookmarksTestTags.ERROR),
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

/** The empty state: the design's plain paragraph, with no list. */
@Composable
private fun EmptyBody() {
    Text(
        text = stringResource(R.string.book_detail_bookmarks_empty),
        style = uiTextStyle(16, FontWeight.Normal, EmptyInk),
        modifier = Modifier.padding(top = 4.dp).testTag(BookmarksTestTags.EMPTY),
    )
}

/** The loaded rows, in the order the state gives them. */
@Composable
private fun BookmarkList(
    rows: List<BookmarkRowModel>,
    nowMillis: Long,
    ribbonColor: Color,
    onChosen: (BookmarkRowModel) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(BookmarksTestTags.LIST),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        items(rows, key = { it.key }) { row ->
            BookmarkRow(row, nowMillis, ribbonColor, onChosen)
        }
    }
}

/** A row's text, built once per row, state and locale. */
private class BookmarkRowText(val name: String, val meta: String?, val description: String)

/**
 * One bookmark: a ribbon in the book's accent, its name, its meta line when it has one, and a chevron. The whole row is
 * one button whose description is the name and the place.
 *
 * Also used by the player's bookmarks sheet (#171), which passes [modifier] for its remove action.
 */
@Composable
internal fun BookmarkRow(
    row: BookmarkRowModel,
    nowMillis: Long,
    ribbonColor: Color,
    onChosen: (BookmarkRowModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val locale = LocalConfiguration.current.locales[0]
    val savedPosition = stringResource(R.string.book_detail_bookmarks_saved_position)
    val listeningPattern = stringResource(R.string.book_detail_bookmarks_listening)
    val underAMinute = stringResource(R.string.book_detail_bookmarks_under_a_minute)
    val chapterPattern = stringResource(R.string.book_detail_bookmarks_chapter)
    val percentPattern = stringResource(R.string.book_detail_bookmarks_percent)
    val key = row.key
    val text = remember(
        row,
        nowMillis,
        locale,
        savedPosition,
        listeningPattern,
        underAMinute,
        chapterPattern,
        percentPattern,
    ) {
        val wording = BookmarkWording(savedPosition, { String.format(locale, listeningPattern, it) }, underAMinute)
        val name = bookmarkName(row.bookmark, wording)
        val parts = bookmarkMetaParts(
            placement = row.placement,
            createdAtMillis = row.createdAtMillis,
            nowMillis = nowMillis,
            locale = locale,
            timeZone = TimeZone.getDefault(),
            chapterLabel = { String.format(locale, chapterPattern, it) },
            percentLabel = { String.format(locale, percentPattern, it) },
        )
        BookmarkRowText(name, bookmarkMeta(parts), bookmarkRowDescription(name, parts))
    }
    Row(
        modifier
            .testTag(BookmarksTestTags.row(key))
            .topRule()
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clickable(role = Role.Button) { onChosen(row) }
            .semantics(mergeDescendants = true) { contentDescription = text.description }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(15.dp, 26.dp)
                .clip(HighlightRibbonShape)
                .background(ribbonColor)
                .testTag(BookmarksTestTags.ribbon(key)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = text.name,
                style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0),
                modifier = Modifier.testTag(BookmarksTestTags.name(key)),
            )
            text.meta?.let {
                Text(
                    text = it,
                    style = uiTextStyle(14, FontWeight.Normal, SheetMuted),
                    modifier = Modifier.testTag(BookmarksTestTags.meta(key)),
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_book_detail_merge_chevron),
            contentDescription = null,
            tint = SheetMuted,
            modifier = Modifier.size(16.dp).testTag(BookmarksTestTags.chevron(key)),
        )
    }
}

/** A hairline across the top of a row. */
private fun Modifier.topRule(): Modifier = drawBehind {
    drawLine(RowRule, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
}

/** One sample row for the previews. */
private fun previewRow(id: Long, bookmark: Bookmark, placement: BookmarkPlacement): BookmarkRowModel =
    BookmarkRowModel(
        key = "id:$id",
        bookmark = bookmark,
        listeningSeconds = listeningSeconds(bookmark.position),
        placement = placement,
        createdAtMillis = bookmark.createdAtSeconds?.times(1_000L),
        destination = BookDetailDestination.Reader("preview", bookmark.position),
    )

/** The design's sample list: a text row, two audio rows and a row without a meta line. */
private val previewRows = listOf(
    previewRow(
        1,
        Bookmark(1, null, "preview", "epubcfi(/6/14!/4/2)", "The Episode of the Barrel", "Chapter 7", 0.47f, 1_761_609_600L),
        BookmarkPlacement(7, 47),
    ),
    previewRow(
        2,
        Bookmark(2, null, "preview", "4332.5", null, null, null, 1_761_609_600L),
        BookmarkPlacement(4, 28),
    ),
    previewRow(
        3,
        Bookmark(3, null, "preview", "120", null, null, null, null),
        BookmarkPlacement(null, null),
    ),
    previewRow(
        4,
        Bookmark(4, null, "preview", "epubcfi(/6/20!/4/2)", null, "Chapter 5", 0.41f, 1_761_264_000L),
        BookmarkPlacement(5, 41),
    ),
)

@PreviewLightDark
@Composable
private fun BookmarksSheetContentPreview() {
    OmnibusTheme {
        BookmarksSheetContent(
            content = BookmarksContent.Loaded(previewRows),
            nowMillis = 1_762_084_800_000L,
            ribbonColor = Color(0xFFB8864A),
            onRetry = {},
            onClose = {},
            onChosen = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun BookmarksSheetEmptyPreview() {
    OmnibusTheme {
        BookmarksSheetContent(
            content = BookmarksContent.Loaded(emptyList()),
            nowMillis = 1_762_084_800_000L,
            ribbonColor = Color(0xFFB8864A),
            onRetry = {},
            onClose = {},
            onChosen = {},
        )
    }
}
