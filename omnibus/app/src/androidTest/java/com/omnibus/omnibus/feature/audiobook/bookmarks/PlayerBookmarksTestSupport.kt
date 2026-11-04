package com.omnibus.omnibus.feature.audiobook.bookmarks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarkRowModel
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarksContent
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** The sheet tests' "now": 2025-11-02 12:00 UTC, so a 2025 date reads as "October 28" with no year. */
const val SHEET_NOW = 1_762_084_800_000L

/** The date every sample bookmark is dated, as Book Detail's sheet prints it at [SHEET_NOW]. */
const val SAMPLE_DATE = "October 28"

/** The ribbon colour the sheet tests pass in. */
val TestRibbon = Color(0xFFB8864A)

/** A bookmark of the test book, dated 2025-10-28. The default is a text CFI with no title. */
fun bookmark(
    id: Long,
    position: String = "epubcfi(/6/14!/4/2)",
    title: String? = null,
    clientId: String? = null,
): Bookmark = Bookmark(
    id = id,
    clientId = clientId,
    bookUuid = "u",
    position = position,
    title = title,
    chapterTitle = null,
    fraction = 0.47f,
    createdAtSeconds = 1_761_609_600L,
)

/** The rows for [bookmarks], built by the player's rules. */
fun rowsOf(vararg bookmarks: Bookmark): List<BookmarkRowModel> = playerBookmarkRows("u", bookmarks.toList())

/**
 * Renders [PlayerBookmarksSheetContent] over [content] inside the dark theme, filling the window. A test can change
 * [content] between frames.
 */
fun ComposeContentTestRule.setSheetHost(
    content: State<BookmarksContent>,
    onRetry: () -> Unit = {},
    onClose: () -> Unit = {},
    onChosen: (BookmarkRowModel) -> Unit = {},
    onRemove: (BookmarkRowModel) -> Unit = {},
) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            Box(Modifier.fillMaxSize()) {
                PlayerBookmarksSheetContent(
                    content = content.value,
                    nowMillis = SHEET_NOW,
                    ribbonColor = TestRibbon,
                    onRetry = onRetry,
                    onClose = onClose,
                    onChosen = onChosen,
                    onRemove = onRemove,
                )
            }
        }
    }
}

/** Renders the sheet over a fixed [content]. */
fun ComposeContentTestRule.setSheetContent(
    content: BookmarksContent,
    onRetry: () -> Unit = {},
    onClose: () -> Unit = {},
    onChosen: (BookmarkRowModel) -> Unit = {},
    onRemove: (BookmarkRowModel) -> Unit = {},
) = setSheetHost(mutableStateOf(content), onRetry, onClose, onChosen, onRemove)
