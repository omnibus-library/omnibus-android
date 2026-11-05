package com.omnibus.omnibus.feature.audiobook.chapters

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot

/** Each row's number, title and length, with the progress line on the current row only. */
@PreviewTest
@PreviewLightDark
@Composable
fun ChaptersSheetRows() {
    Shot {
        ChaptersSheetContent(
            ChaptersUiState(
                rows = (1..5).map { ChapterRowModel(it, "Part $it", "$it:00") },
                currentIndex = 2,
                currentProgress = 0.5f,
            ),
            onChosen = {},
            onDone = {},
        )
    }
}
