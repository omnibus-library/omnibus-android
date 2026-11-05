package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination

// The sheet is always dark, so each state has one shot. Instants are at noon UTC, so they fall on the same date in
// every zone the shots could be taken in.
/** 2025-11-02 12:00 UTC. */
private const val NOW_MILLIS = 1_762_084_800_000L

/** 2025-10-28 12:00 UTC. */
private const val OCT_28_2025_SECONDS = 1_761_652_800L

private val Ribbon = Color(0xFFB8864A)

@PreviewTest
@Preview
@Composable
fun BookmarksSheetRows() {
    BookmarksShot(
        BookmarksContent.Loaded(
            listOf(
                row(Bookmark(1, null, "u", "epubcfi(/6/14!/4/2)", "The Episode of the Barrel", null, 0.47f, OCT_28_2025_SECONDS), BookmarkPlacement(7, 47)),
                row(Bookmark(2, null, "u", "4332.5", null, null, null, OCT_28_2025_SECONDS), BookmarkPlacement(4, 28)),
                row(Bookmark(3, null, "u", "epubcfi(/6/20!/4/2)", null, null, 0.41f, OCT_28_2025_SECONDS), BookmarkPlacement(5, 41)),
                row(Bookmark(4, null, "u", "epubcfi(/6/22!/4/2)", null, null, null, null), BookmarkPlacement(null, null)),
            ),
        ),
    )
}

@PreviewTest
@Preview
@Composable
fun BookmarksSheetEmpty() {
    BookmarksShot(BookmarksContent.Loaded(emptyList()))
}

@PreviewTest
@Preview
@Composable
fun BookmarksSheetLoading() {
    BookmarksShot(BookmarksContent.Loading)
}

@PreviewTest
@Preview
@Composable
fun BookmarksSheetFailed() {
    BookmarksShot(BookmarksContent.Failed)
}

private fun row(bookmark: Bookmark, placement: BookmarkPlacement) = BookmarkRowModel(
    key = "id:${bookmark.id}",
    bookmark = bookmark,
    listeningSeconds = listeningSeconds(bookmark.position),
    placement = placement,
    createdAtMillis = bookmark.createdAtSeconds?.times(1_000L),
    destination = BookDetailDestination.Reader("u", bookmark.position),
)

@Composable
private fun BookmarksShot(content: BookmarksContent) {
    BookmarksSheetContent(
        content = content,
        nowMillis = NOW_MILLIS,
        ribbonColor = Ribbon,
        onRetry = {},
        onClose = {},
        onChosen = {},
    )
}
