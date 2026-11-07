package com.omnibus.omnibus.feature.bookdetail.more.merge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.shotBook
import com.omnibus.omnibus.screenshot.Shot

// The searching spinner is indeterminate and has no reduced-motion form, so that state is not shot;
// MergeSheetContentTest covers it.

/** The sheet's container colour. */
private val SheetContainer = Color(0xFF15120F)

/** The book on the page. It has a series, so a same-series candidate can be drawn. */
private val target = shotBook(title = "The Sign of the Four").copy(uniqueIdentifier = "book-1", seriesId = 5L)

private val alpha = candidate(2, "Alpha", author = "Ann", formats = listOf("epub"))
private val bravo = candidate(3, "Bravo", author = "Bea", year = "1902-01-01", formats = listOf("epub", "m4b"), seriesId = 5L)
private val charlie = candidate(4, "Charlie", author = "Cy", formats = emptyList())

@PreviewTest
@Preview
@Composable
fun MergeSheetIdle() {
    MergeShot(MergeUIState(sheetOpen = true))
}

/** The result count, then rows with an author alone, author and year with same series and formats, and no formats. */
@PreviewTest
@Preview
@Composable
fun MergeSheetResults() {
    MergeShot(MergeUIState(sheetOpen = true, query = "dune", candidates = listOf(alpha, bravo, charlie)))
}

@PreviewTest
@Preview
@Composable
fun MergeSheetNoResults() {
    MergeShot(MergeUIState(sheetOpen = true, query = "zzz"))
}

/** The covers over the body, which names both books in bold, then Back before Merge. */
@PreviewTest
@Preview
@Composable
fun MergeSheetConfirm() {
    MergeShot(MergeUIState(sheetOpen = true, step = MergeStep.Confirm(bravo)))
}

/** The error line sits under the body. */
@PreviewTest
@Preview
@Composable
fun MergeSheetMergeFailed() {
    MergeShot(MergeUIState(sheetOpen = true, step = MergeStep.Confirm(bravo), mergeError = MergeFailure.Generic))
}

private fun candidate(
    id: Long,
    title: String,
    author: String,
    year: String? = null,
    formats: List<String>,
    seriesId: Long? = null,
): Book = shotBook(title = title, creators = listOf(Creator(author, "author", null, null)), publishedDate = year)
    .copy(id = id, uniqueIdentifier = "uuid-$id", formats = formats, seriesId = seriesId)

@Composable
private fun MergeShot(state: MergeUIState) {
    // The sheet draws a fixed dark container but takes its text colours from the app theme, so it is only legible dark.
    Shot(darkTheme = true) {
        Box(Modifier.background(SheetContainer)) {
            MergeSheetContent(state = state, target = target, actions = MergeSheetActions())
        }
    }
}
