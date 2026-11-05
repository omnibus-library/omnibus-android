package com.omnibus.omnibus.feature.audiobook.carmode

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.ui.theme.OmnibusTheme

// Car mode is always drawn on black in the dark palette, so it has one shot.
/** The chapter title with the time left under it, and the tiles below. */
@PreviewTest
@Preview
@Composable
fun CarModeReady() {
    OmnibusTheme(darkTheme = true) {
        CarModeContent(
            uiState = CarModeUIState(
                title = "004",
                bookSecondsLeftAtRate = 20 * 3600.0 + 25 * 60.0,
                isPlaying = false,
                canPreviousChapter = true,
                canNextChapter = true,
            ),
            onDismiss = {},
            onSkipBack = {},
            onSkipForward = {},
            onPlayPauseToggled = {},
            onPreviousChapter = {},
            onNextChapter = {},
        )
    }
}
