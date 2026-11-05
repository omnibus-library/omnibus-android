package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot

/**
 * All five cells in order, Speed showing the rate, at equal widths; then only Speed and Bookmark, sharing the width
 * between them.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun PlayerToolRowCells() {
    Shot {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            PlayerToolRow(
                rate = 1.25,
                hasEbook = true,
                actions = PlayerActions(
                    onOpenSpeed = {},
                    onOpenCarMode = {},
                    onOpenSleep = {},
                    onAddBookmark = {},
                    syncHere = PlayerToolButton(label = "Sync here", enabled = true, onClick = {}),
                ),
            )
            PlayerToolRow(
                rate = 1.0,
                hasEbook = true,
                actions = PlayerActions(onOpenSpeed = {}, onAddBookmark = {}),
            )
        }
    }
}
