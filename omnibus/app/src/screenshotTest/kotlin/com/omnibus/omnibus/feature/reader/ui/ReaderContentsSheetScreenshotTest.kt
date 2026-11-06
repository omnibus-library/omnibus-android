package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.feature.reader.ReaderBookmarksStatus
import com.omnibus.omnibus.screenshot.Shot

/** Three levels with pages, "Page 38 of 112" in the header beside the cover, and the marker on "The Customs House". */
@PreviewTest
@PreviewLightDark
@Composable
fun ReaderContentsWithPages() {
    ContentsShot(ReaderContentsPreviewToc, ReaderContentsPreviewLocation)
}

/** Before page numbers exist: no pages on the rows, and no position line in the header. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReaderContentsWithoutPages() {
    ContentsShot(ReaderContentsPreviewBareToc, ReaderContentsPreviewBareLocation)
}

/** No location, so no position line and no marker. A padded label is trimmed, and a page of 0 or less shows nothing. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReaderContentsOddEntries() {
    ContentsShot(
        toc = listOf(
            TocEntry(label = "   Spaced Out  ", href = "a.xhtml", level = 0, page = 4),
            TocEntry(label = "Alpha", href = "b.xhtml", level = 0, page = 0),
            TocEntry(label = "Beta", href = "c.xhtml", level = 1, page = -3),
            TocEntry(label = "Gamma", href = "d.xhtml", level = 1, page = 9),
        ),
        location = null,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun ReaderContentsEmpty() {
    ContentsShot(emptyList(), ReaderContentsPreviewLocation)
}

/** A titled row with its percent, a chapter-titled row without one, and an untitled row at 100%, in server order. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReaderContentsBookmarks() {
    ContentsShot(
        ReaderContentsPreviewToc,
        ReaderContentsPreviewLocation,
        tab = ReaderContentsTab.Bookmarks,
        bookmarks = listOf(
            Bookmark(1L, "client-1", "book-1", "epubcfi(/6/4)", "The ferry", "Part One", 0.43f, null),
            Bookmark(2L, "client-2", "book-1", "epubcfi(/6/8)", null, "Crossing at Dusk", null, null),
            Bookmark(3L, "client-3", "book-1", "epubcfi(/6/12)", null, null, 1f, null),
        ),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun ReaderContentsBookmarksLoading() {
    ContentsShot(
        ReaderContentsPreviewToc,
        ReaderContentsPreviewLocation,
        tab = ReaderContentsTab.Bookmarks,
        status = ReaderBookmarksStatus.Loading,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun ReaderContentsBookmarksEmpty() {
    ContentsShot(ReaderContentsPreviewToc, ReaderContentsPreviewLocation, tab = ReaderContentsTab.Bookmarks)
}

@PreviewTest
@PreviewLightDark
@Composable
fun ReaderContentsBookmarksFailed() {
    ContentsShot(
        ReaderContentsPreviewToc,
        ReaderContentsPreviewLocation,
        tab = ReaderContentsTab.Bookmarks,
        status = ReaderBookmarksStatus.Failed,
    )
}

@Composable
private fun ContentsShot(
    toc: List<TocEntry>,
    location: RelocateData?,
    tab: ReaderContentsTab = ReaderContentsTab.Contents,
    bookmarks: List<Bookmark> = emptyList(),
    status: ReaderBookmarksStatus = ReaderBookmarksStatus.Loaded,
) {
    Shot {
        ReaderContentsSheetContent(
            title = "The Long Road Home",
            cover = ReaderContentsPreviewCover,
            toc = toc,
            location = location,
            onEntryChosen = {},
            onDismiss = {},
            initialTab = tab,
            bookmarks = bookmarks,
            bookmarksStatus = status,
        )
    }
}
