package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.screenshot.Shot

// Instants are at noon UTC, so they fall on the same date in every zone the shots could be taken in.
/** 2026-11-02 12:00 UTC. */
private const val NOW_MILLIS = 1_793_620_800_000L

/** 2025-10-29 12:00 UTC. */
private const val OCT_29_2025 = 1_761_739_200_000L

@PreviewTest
@PreviewLightDark
@Composable
fun HighlightsLoading() {
    HighlightsShot(HighlightsUIContent.Loading)
}

@PreviewTest
@PreviewLightDark
@Composable
fun HighlightsEmpty() {
    HighlightsShot(HighlightsUIContent.Loaded(emptyList()))
}

@PreviewTest
@PreviewLightDark
@Composable
fun HighlightsFailed() {
    HighlightsShot(HighlightsUIContent.Failed)
}

/** A passage alone, a note with no passage, no chapter, no date, and a footer with nothing in it. */
@PreviewTest
@PreviewLightDark
@Composable
fun HighlightsRowVariants() {
    HighlightsShot(
        HighlightsUIContent.Loaded(
            listOf(
                row(1, text = "Only passage", chapterNumber = 2),
                row(2, text = null, note = "Kobo note", color = HighlightColor.Green),
                row(3, text = "No chapter", color = HighlightColor.Blue),
                row(4, text = "Chapter alone", chapterNumber = 3, createdAtMillis = null, color = HighlightColor.Rose),
                row(5, text = "No footer text", createdAtMillis = null, color = HighlightColor.Violet),
            ),
        ),
    )
}

private fun row(
    id: Long,
    text: String?,
    note: String? = null,
    color: HighlightColor = HighlightColor.Amber,
    chapterNumber: Int? = null,
    createdAtMillis: Long? = OCT_29_2025,
) = HighlightRowModel(id, text, note, color, chapterNumber, createdAtMillis)

@Composable
private fun HighlightsShot(content: HighlightsUIContent) {
    Shot {
        HighlightsContent(HighlightsUIState(content, NOW_MILLIS), onRetry = {})
    }
}
