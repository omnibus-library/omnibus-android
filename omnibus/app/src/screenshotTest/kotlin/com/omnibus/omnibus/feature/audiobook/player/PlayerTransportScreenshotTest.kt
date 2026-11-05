package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot

/** Previous, back 15, the larger play disc, forward 30 and next, on one row. */
@PreviewTest
@PreviewLightDark
@Composable
fun PlayerTransportControls() {
    Shot {
        PlayerTransport(
            isPlaying = true,
            canPreviousChapter = true,
            canNextChapter = true,
            onPreviousChapter = {},
            onSkipBack = {},
            onPlayPauseToggled = {},
            onSkipForward = {},
            onNextChapter = {},
        )
    }
}
