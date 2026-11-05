package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.feature.bookdetail.highlights.HighlightRowModel
import com.omnibus.omnibus.screenshot.Shot

/** 2026-11-02 12:00 UTC. The rows have no dates, so it shows nowhere. */
private const val NOW_MILLIS = 1_793_620_800_000L

@PreviewTest
@Preview
@Composable
fun ReviewHighlightPickerLoading() {
    PickerShot(PickerContent.Loading)
}

@PreviewTest
@Preview
@Composable
fun ReviewHighlightPickerFailed() {
    PickerShot(PickerContent.Failed)
}

@PreviewTest
@Preview
@Composable
fun ReviewHighlightPickerEmpty() {
    PickerShot(PickerContent.Loaded(emptyList()))
}

/** A passage with its chapter in the footer, and one with no chapter and so no footer. */
@PreviewTest
@Preview
@Composable
fun ReviewHighlightPickerRows() {
    PickerShot(PickerContent.Loaded(listOf(row(1, "Placed passage", chapter = 3), row(2, "Unplaced passage", chapter = null))))
}

private fun row(id: Long, text: String, chapter: Int?) =
    HighlightRowModel(id, text, note = null, color = HighlightColor.Amber, chapterNumber = chapter, createdAtMillis = null)

@Composable
private fun PickerShot(content: PickerContent) {
    // The sheet draws a fixed dark container but takes its text colours from the app theme, so it is only legible dark.
    Shot(darkTheme = true) {
        ReviewHighlightPickerContent(content = content, nowMillis = NOW_MILLIS, onPick = {}, onRetry = {}, onClose = {})
    }
}
