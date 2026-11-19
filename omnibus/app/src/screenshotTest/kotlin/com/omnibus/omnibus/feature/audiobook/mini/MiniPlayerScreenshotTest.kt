package com.omnibus.omnibus.feature.audiobook.mini

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.OmnibusNavigationRail
import com.omnibus.omnibus.ui.components.OmnibusTabBar
import com.omnibus.omnibus.ui.components.OmnibusTabBarItem

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

/** The shell's docked strip over the tab bar (#286): expanded, then collapsed. */
@PreviewTest
@PreviewLightDark
@Composable
fun DockedMiniPlayer() {
    Shot {
        val tabs = ShellTab.entries.map { OmnibusTabBarItem(stringResource(it.label), it.icon, it.selectedIcon) }
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            listOf(false, true).forEach { collapsed ->
                Column {
                    ShellMiniPlayerStrip(
                        uiState = playing.copy(percentListened = 35),
                        collapsed = collapsed,
                        reduceMotion = true,
                        actions = MiniPlayerActions({}, {}, {}, {}),
                    )
                    OmnibusTabBar(items = tabs, selectedIndex = 0, onSelect = {})
                }
            }
        }
    }
}

/** The rail's mini player block (#286): full in a full-height rail, compact in a 440dp one. */
@PreviewTest
@Preview(name = "Light", widthDp = 200, heightDp = 640)
@Preview(name = "Dark", widthDp = 200, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RailMiniPlayer() {
    Shot {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.fillMaxHeight()) { RailWithBlock() }
            Box(Modifier.height(440.dp)) { RailWithBlock() }
        }
    }
}

@Composable
private fun RailWithBlock() {
    val tabs = ShellTab.entries.map { OmnibusTabBarItem(stringResource(it.label), it.icon, it.selectedIcon) }
    OmnibusNavigationRail(
        items = tabs,
        selectedIndex = 0,
        onSelect = {},
        footer = { space ->
            ShellMiniPlayerRailBlock(playing.copy(percentListened = 35), space, MiniPlayerActions({}, {}, {}, {}))
        },
    )
}

@Composable
private fun MiniPlayerBar(uiState: MiniPlayerUIState) {
    MiniPlayerContent(uiState = uiState, onExpand = {}, onSkipBack = {}, onPlayPauseToggled = {}, onStop = {})
}
