package com.omnibus.omnibus.feature.audiobook.bookmarks

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarksContent

// The sheet is always dark, so it has one shot. Instants are at noon UTC, so they fall on the same date in every zone
// the shots could be taken in.
/** 2025-11-02 12:00 UTC. */
private const val NOW_MILLIS = 1_762_084_800_000L

/** 2025-10-28 12:00 UTC. */
private const val OCT_28_2025_SECONDS = 1_761_652_800L

/** A titled row, an audio row named by its listening time and a text row named "Saved position", each dated alone. */
@PreviewTest
@Preview
@Composable
fun PlayerBookmarksSheetRows() {
    PlayerBookmarksSheetContent(
        content = BookmarksContent.Loaded(
            playerBookmarkRows(
                "u",
                listOf(
                    bookmark(1, "epubcfi(/6/14!/4/2)", title = "Fav"),
                    bookmark(2, "3725"),
                    bookmark(3, "epubcfi(/6/14!/4/2)"),
                ),
            ),
        ),
        nowMillis = NOW_MILLIS,
        ribbonColor = Color(0xFFB8864A),
        onRetry = {},
        onClose = {},
        onChosen = {},
        onRemove = {},
    )
}

private fun bookmark(id: Long, position: String, title: String? = null) = Bookmark(
    id = id,
    clientId = null,
    bookUuid = "u",
    position = position,
    title = title,
    chapterTitle = null,
    fraction = 0.47f,
    createdAtSeconds = OCT_28_2025_SECONDS,
)
