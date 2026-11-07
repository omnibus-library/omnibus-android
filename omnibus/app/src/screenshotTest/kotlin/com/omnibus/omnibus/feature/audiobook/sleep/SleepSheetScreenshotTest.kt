package com.omnibus.omnibus.feature.audiobook.sleep

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.audiobook.playback.Chapter
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.PlayingBook
import com.omnibus.omnibus.screenshot.Shot

private val book = PlayingBook("book-1", 11L, "The Book", "An Author", false)

/** The title, the eight presets in two columns, and the full-width "End of chapter 2" cell under them. */
@PreviewTest
@PreviewLightDark
@Composable
fun SleepSheetWithChapters() {
    SleepShot(
        PlaybackState(
            book = book,
            chapters = listOf(
                Chapter(1L, "One", 0.0, 600.0),
                Chapter(2L, "Two", 600.0, 1200.0),
                Chapter(3L, "Three", 1800.0, 900.0),
            ),
            positionSeconds = 700.0,
            durationSeconds = 2700.0,
        ),
    )
}

@Composable
private fun SleepShot(playback: PlaybackState) {
    Shot {
        SleepSheetContent(sleepUIState(playback), onSelect = {})
    }
}
