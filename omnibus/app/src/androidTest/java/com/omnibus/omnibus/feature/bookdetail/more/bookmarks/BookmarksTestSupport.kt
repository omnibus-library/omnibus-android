package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuEntry
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** The sheet tests' "now": 2025-11-02 12:00 UTC, so a 2025 date reads as "October 28" with no year. */
const val SHEET_NOW = 1_762_084_800_000L

/** The ribbon colour the sheet tests pass in. */
val TestRibbon = Color(0xFFB8864A)

/** A bookmark for sheet tests. The default is a text CFI, dated 2025-10-28. */
fun bookmark(
    id: Long,
    position: String = "epubcfi(/6/14!/4/2)",
    title: String? = null,
    fraction: Float? = 0.47f,
    createdAtSeconds: Long? = 1_761_609_600L,
): Bookmark = Bookmark(
    id = id,
    clientId = null,
    bookUuid = "u",
    position = position,
    title = title,
    chapterTitle = null,
    fraction = fraction,
    createdAtSeconds = createdAtSeconds,
)

/** A row for sheet tests, keyed "id:<id>", with the default placement of chapter 7 at 47 %. */
fun rowModel(
    bookmark: Bookmark,
    placement: BookmarkPlacement = BookmarkPlacement(7, 47),
    destination: BookDetailDestination = BookDetailDestination.Reader("u", bookmark.position),
): BookmarkRowModel = BookmarkRowModel(
    key = "id:${bookmark.id}",
    bookmark = bookmark,
    listeningSeconds = listeningSeconds(bookmark.position),
    placement = placement,
    createdAtMillis = bookmark.createdAtSeconds?.times(1_000L),
    destination = destination,
)

/** Renders [BookmarksSheetContent] alone inside [darkTheme], filling the window. */
fun ComposeContentTestRule.setSheetContent(
    content: BookmarksContent,
    darkTheme: Boolean = true,
    onRetry: () -> Unit = {},
    onChosen: (BookmarkRowModel) -> Unit = {},
    onClose: () -> Unit = {},
) {
    setContent {
        OmnibusTheme(darkTheme = darkTheme) {
            Box(Modifier.fillMaxSize()) {
                BookmarksSheetContent(
                    content = content,
                    nowMillis = SHEET_NOW,
                    ribbonColor = TestRibbon,
                    onRetry = onRetry,
                    onClose = onClose,
                    onChosen = onChosen,
                )
            }
        }
    }
}

/**
 * Stands in for [BookmarksMenuEntry] without its ViewModel: the same joining group and the same VM-free row. [onOpen]
 * runs on the row's tap, and [overlay] is composed beside the disc.
 */
class BookmarksTestEntry(
    private val onOpen: () -> Unit = {},
    private val overlay: @Composable () -> Unit = {},
) : BookMoreMenuEntry {

    override val joinsPreviousGroup: Boolean get() = true

    @Composable
    override fun Rows(context: BookDetailSectionContext, dismissMenu: () -> Unit) {
        BookmarksMenuRows(context.book) {
            dismissMenu()
            onOpen()
        }
    }

    @Composable
    override fun Overlay(context: BookDetailSectionContext) = overlay()
}
