package com.omnibus.omnibus.feature.audiobook.player

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.components.CoverIdentity

/**
 * The whole player over a bright book's backdrop (#197), in light and dark. A screenshot has no cover image to blur, so
 * the shot shows the tone bloom and the fade that sit under it.
 */
@PreviewTest
@Preview(widthDp = 411, heightDp = 891, uiMode = UI_MODE_NIGHT_NO)
@Preview(widthDp = 411, heightDp = 891, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PlayerBackdropBrightCover() {
    Shot {
        PlayerScreenContent(
            uiState = PlayerUIState(
                gate = PlayerGate.Open,
                cover = CoverIdentity(
                    uuid = "bright",
                    title = "The Sunlit Orchard",
                    creator = "Marguerite Vale",
                    accent = "oklch(0.85 0.17 85)",
                    hasCover = false,
                ),
                title = "The Sunlit Orchard",
                author = "Marguerite Vale",
                chapterTitle = "Chapter 4",
                hasChapters = true,
                spanLengthSeconds = 600.0,
                spanPositionSeconds = 230.0,
                durationSeconds = 7200.0,
                canPreviousChapter = true,
                canNextChapter = true,
                isPreparing = false,
            ),
            actions = PlayerActions(onOpenChapters = {}, onOpenSpeed = {}, onOpenSleep = {}, onAddBookmark = {}),
            onClose = {},
            onPlayPauseToggled = {},
            onSkipBack = {},
            onSkipForward = {},
            onPreviousChapter = {},
            onNextChapter = {},
            onSeek = {},
            reducedMotion = true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
