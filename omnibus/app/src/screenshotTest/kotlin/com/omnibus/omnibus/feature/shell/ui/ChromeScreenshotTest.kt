package com.omnibus.omnibus.feature.shell.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerContent
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerUIState
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.feature.shell.chrome.FabSpec
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.OmnibusTabBar
import com.omnibus.omnibus.ui.components.OmnibusTabBarItem

/** Six fixed cover tones, so the floating margins and shadows read against content without loading any image. */
private val Tones = listOf(
    Color(0xFF3D5A6C),
    Color(0xFF7A6A4F),
    Color(0xFF5B3B4A),
    Color(0xFF2F4A3A),
    Color(0xFFA0662F),
    Color(0xFF6B7F99),
)

private val playing = MiniPlayerUIState(
    visible = true,
    bookUuid = "book-1",
    cover = CoverIdentity(uuid = "book-1", title = "Plate", creator = "An Author", hasCover = false),
    title = "The Left Hand of Darkness",
    subtitle = "Chapter 7: The Question of Sex",
    progress = 0.35f,
    isPlaying = true,
)

/** Narrow: 360 wide, with Library selected (FAB shown). */
@PreviewTest
@Preview(name = "Light", widthDp = 360, heightDp = 640)
@Preview(name = "Dark", widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ChromeNarrowLibrary() {
    ChromeShot(ShellTab.Library)
}

/** Narrow with Search selected, so the FAB is gone. */
@PreviewTest
@Preview(name = "Light", widthDp = 360, heightDp = 640)
@Preview(name = "Dark", widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ChromeNarrowSearch() {
    ChromeShot(ShellTab.Search)
}

/** Wide: 841 wide, the bar capped at the start and the FAB in the corner. */
@PreviewTest
@Preview(name = "Light", widthDp = 841, heightDp = 500)
@Preview(name = "Dark", widthDp = 841, heightDp = 500, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ChromeWideLibrary() {
    ChromeShot(ShellTab.Library)
}

/** Wide with Stats selected, so the FAB is gone and the bar keeps its place. */
@PreviewTest
@Preview(name = "Light", widthDp = 841, heightDp = 500)
@Preview(name = "Dark", widthDp = 841, heightDp = 500, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ChromeWideStats() {
    ChromeShot(ShellTab.Stats)
}

/** Narrow with the mini player in its slot, the FAB stacked above it. */
@PreviewTest
@Preview(name = "Light", widthDp = 360, heightDp = 640)
@Preview(name = "Dark", widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ChromeNarrowMiniPlayer() {
    ChromeShot(ShellTab.Library, miniPlayer = true)
}

/** Wide with the mini player in its slot, the FAB centred on the bar. */
@PreviewTest
@Preview(name = "Light", widthDp = 841, heightDp = 500)
@Preview(name = "Dark", widthDp = 841, heightDp = 500, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ChromeWideMiniPlayer() {
    ChromeShot(ShellTab.Library, miniPlayer = true)
}

/** The Add books icon on its accent FAB: a book outline with the "+" cut out of its badge. */
@PreviewTest
@PreviewLightDark
@Composable
fun ShellFabAddBook() {
    Shot {
        Box(Modifier.padding(24.dp)) {
            ShellFab(FabSpec(R.drawable.ic_add_book, R.string.masthead_add_books) {})
        }
    }
}

@Composable
private fun ChromeShot(selected: ShellTab, miniPlayer: Boolean = false) {
    Shot {
        val items = ShellTab.entries.map { OmnibusTabBarItem(stringResource(it.label), it.icon, it.selectedIcon) }
        ChromeScaffold(
            chromeVisible = true,
            fabVisible = selected == ShellTab.Library,
            bar = { OmnibusTabBar(items, ShellTab.entries.indexOf(selected), {}) },
            fab = { ShellFab(FabSpec(R.drawable.ic_add_book, R.string.masthead_add_books) {}) },
            miniPlayer = {
                if (miniPlayer) {
                    MiniPlayerContent(uiState = playing, onExpand = {}, onSkipBack = {}, onPlayPauseToggled = {}, onStop = {})
                }
            },
        ) {
            // Cover-like tiles behind the chrome, so the floating margins and shadows have something to sit on.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 20.dp,
                    bottom = LocalShellContentPadding.current.calculateBottomPadding(),
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(24) { i ->
                    Box(
                        Modifier
                            .aspectRatio(2f / 3f)
                            .background(Tones[i % Tones.size], RoundedCornerShape(6.dp)),
                    )
                }
            }
        }
    }
}
