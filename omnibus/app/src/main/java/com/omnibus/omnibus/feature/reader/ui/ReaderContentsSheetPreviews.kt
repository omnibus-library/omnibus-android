package com.omnibus.omnibus.feature.reader.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** The cover for the contents previews. It has no art, so the generated plate is drawn. */
internal val ReaderContentsPreviewCover = CoverIdentity(
    uuid = "preview",
    title = "The Long Road Home",
    creator = "A. N. Author",
    hasCover = false,
)

/**
 * A three-level table of contents with page numbers. Entry 6 (0-based) is "The Customs House", the
 * chapter [ReaderContentsPreviewLocation] is in.
 */
internal val ReaderContentsPreviewToc = listOf(
    TocEntry(label = "Cover", href = "chapter-0.xhtml", level = 0, page = 1, pct = 1),
    TocEntry(label = "Title Page", href = "chapter-1.xhtml", level = 0, page = 3, pct = 3),
    TocEntry(label = "Part One: The Long Road", href = "chapter-2.xhtml", level = 0, page = 5, pct = 4),
    TocEntry(label = "1. Departure", href = "chapter-3.xhtml", level = 1, page = 7, pct = 6),
    TocEntry(label = "2. The Border", href = "chapter-4.xhtml", level = 1, page = 19, pct = 17),
    TocEntry(label = "Crossing at Dusk", href = "chapter-5.xhtml", level = 2, page = 23, pct = 21),
    TocEntry(label = "The Customs House", href = "chapter-6.xhtml", level = 2, page = 31, pct = 28),
    TocEntry(
        label = "3. A Letter Arrives That Was Never Meant to Be Opened by Anyone but the Addressee",
        href = "chapter-7.xhtml",
        level = 1,
        page = 44,
        pct = 39,
    ),
    TocEntry(label = "Part Two: Winter", href = "chapter-8.xhtml", level = 0, page = 61, pct = 54),
    TocEntry(label = "4. Snowbound", href = "chapter-9.xhtml", level = 1, page = 63, pct = 56),
    TocEntry(label = "5. Thaw", href = "chapter-10.xhtml", level = 1, page = 80, pct = 71),
    TocEntry(label = "Epilogue", href = "chapter-11.xhtml", level = 0, page = 104, pct = 93),
)

/** [ReaderContentsPreviewToc] before the locations pass: the same entries without pages. */
internal val ReaderContentsPreviewBareToc = ReaderContentsPreviewToc.map { it.copy(page = null, pct = null) }

/** Mid-book with page numbers, in chapter 7 (1-based), which marks "The Customs House". */
internal val ReaderContentsPreviewLocation = RelocateData(
    page = 38,
    totalPages = 112,
    pct = 34,
    chapter = 7,
    totalChapters = 12,
)

/** The same chapter before page numbers exist, so the header falls back to the percent. */
internal val ReaderContentsPreviewBareLocation = RelocateData(
    page = 0,
    totalPages = 0,
    pct = 34,
    chapter = 7,
    totalChapters = 12,
)

/** The sheet's content over a plain window, in the app theme that follows the preview's uiMode. */
@Composable
private fun ContentsPreview(toc: List<TocEntry>, location: RelocateData?) {
    OmnibusTheme {
        ReaderContentsSheetContent(
            title = "The Long Road Home",
            cover = ReaderContentsPreviewCover,
            toc = toc,
            location = location,
            onEntryChosen = {},
            onDismiss = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "Nested with pages", widthDp = 360, heightDp = 760)
@Composable
private fun NestedWithPagesPreview() = ContentsPreview(ReaderContentsPreviewToc, ReaderContentsPreviewLocation)

@Preview(name = "Nested with pages dark", widthDp = 360, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NestedWithPagesDarkPreview() = ContentsPreview(ReaderContentsPreviewToc, ReaderContentsPreviewLocation)

@Preview(name = "Nested without pages", widthDp = 360, heightDp = 760)
@Composable
private fun NestedWithoutPagesPreview() = ContentsPreview(ReaderContentsPreviewBareToc, ReaderContentsPreviewBareLocation)

@Preview(name = "Nested without pages dark", widthDp = 360, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NestedWithoutPagesDarkPreview() = ContentsPreview(ReaderContentsPreviewBareToc, ReaderContentsPreviewBareLocation)

@Preview(name = "Empty", widthDp = 360, heightDp = 760)
@Composable
private fun EmptyContentsPreview() = ContentsPreview(emptyList(), ReaderContentsPreviewLocation)
