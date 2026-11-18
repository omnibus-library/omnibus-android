package com.omnibus.omnibus.feature.shell.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerActions
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerContent
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerFloatingPlacement
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerHost
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerUIState
import com.omnibus.omnibus.feature.shell.ShellNavigationLayout
import com.omnibus.omnibus.ui.components.OmnibusNavigationRail
import com.omnibus.omnibus.ui.components.OmnibusTabBar
import com.omnibus.omnibus.ui.components.OmnibusTabBarItem
import com.omnibus.omnibus.ui.theme.rememberReducedMotion

/** Test tags on the shell's chrome. */
internal object ShellTestTags {
    const val OVERLAY = "shell_bottom_overlay"
    const val TAB_BAR = "shell_tab_bar"
    const val RAIL = "shell_rail"
}

/**
 * The signed-in shell's chrome around [content]: the tab bar or rail for [navigationLayout], and the mini player for
 * [miniPlayer]. The mini player's state and actions come from the caller, which owns them above the layout switch, so
 * moving between the bar and the rail never re-creates them (#286 R17).
 */
@Composable
internal fun ShellScaffold(
    navigationLayout: ShellNavigationLayout,
    tabs: List<OmnibusTabBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    miniPlayer: MiniPlayerUIState,
    miniPlayerActions: MiniPlayerActions,
    showsMiniPlayer: Boolean,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReducedMotion(),
    content: @Composable () -> Unit,
) {
    ShellLayout(
        overlay = {
            ShellBottomOverlay(
                navigationLayout = navigationLayout,
                miniPlayer = { placement ->
                    if (showsMiniPlayer) {
                        MiniPlayerHost(uiState = miniPlayer, reduceMotion = reduceMotion) { shown ->
                            MiniPlayerFloatingPlacement(placement) {
                                MiniPlayerContent(
                                    uiState = shown,
                                    onExpand = miniPlayerActions.onExpand,
                                    onSkipBack = miniPlayerActions.onSkipBack,
                                    onPlayPauseToggled = miniPlayerActions.onPlayPauseToggled,
                                    onStop = miniPlayerActions.onStop,
                                    floating = true,
                                )
                            }
                        }
                    }
                },
                tabBar = { OmnibusTabBar(tabs, selectedIndex, onSelect, Modifier.testTag(ShellTestTags.TAB_BAR)) },
                modifier = Modifier.testTag(ShellTestTags.OVERLAY),
            )
        },
        modifier = modifier,
        navigationLayout = navigationLayout,
        rail = { OmnibusNavigationRail(tabs, selectedIndex, onSelect, Modifier.testTag(ShellTestTags.RAIL)) },
        content = content,
    )
}
