package com.omnibus.omnibus.feature.audiobook.mini

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.components.CoverIdentity

private val playing = MiniPlayerUIState(
    visible = true,
    bookUuid = "book-1",
    cover = CoverIdentity(uuid = "book-1", title = "Plate", creator = "An Author", hasCover = true),
    title = "Mini Title",
    subtitle = "Chapter 7: The Question of Sex",
    progress = 0.35f,
    isPlaying = true,
)

/** The bar with a title and subtitle, then with the subtitle left out. */
@PreviewTest
@PreviewLightDark
@Composable
fun MiniPlayerSubtitles() {
    Shot {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MiniPlayerBar(playing)
            MiniPlayerBar(playing.copy(subtitle = null))
        }
    }
}

@Composable
private fun MiniPlayerBar(uiState: MiniPlayerUIState) {
    MiniPlayerContent(uiState = uiState, onExpand = {}, onSkipBack = {}, onPlayPauseToggled = {}, onStop = {})
}
