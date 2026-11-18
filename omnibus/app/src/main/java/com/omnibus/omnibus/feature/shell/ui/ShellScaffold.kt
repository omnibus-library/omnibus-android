package com.omnibus.omnibus.feature.shell.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerActions
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerHost
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerUIState
import com.omnibus.omnibus.feature.audiobook.mini.ShellMiniPlayerRailBlock
import com.omnibus.omnibus.feature.audiobook.mini.ShellMiniPlayerStrip
import com.omnibus.omnibus.feature.audiobook.mini.rememberMiniPlayerCollapseState
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
 * moving between the bar and the rail never re-creates them (#286 R17). In the bar layout the mini player is a docked
 * strip that collapses while [content] scrolls; in the rail it is the block at the foot of the rail. A change to
 * [collapseResetKey] expands the strip again (#286 R4, R7).
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
    collapseResetKey: Any? = null,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReducedMotion(),
    content: @Composable () -> Unit,
) {
    val collapse = rememberMiniPlayerCollapseState()
    val miniPlayerShown = showsMiniPlayer && miniPlayer.visible
    LaunchedEffect(collapseResetKey) { collapse.expand() }
    LaunchedEffect(miniPlayerShown) { if (miniPlayerShown) collapse.expand() }
    val bar = navigationLayout == ShellNavigationLayout.Bar
    ShellLayout(
        overlay = {
            ShellBottomOverlay(
                navigationLayout = navigationLayout,
                miniPlayer = {
                    if (showsMiniPlayer) {
                        MiniPlayerHost(uiState = miniPlayer, reduceMotion = reduceMotion) { shown ->
                            ShellMiniPlayerStrip(
                                uiState = shown,
                                collapsed = collapse.collapsed,
                                reduceMotion = reduceMotion,
                                actions = miniPlayerActions,
                            )
                        }
                    }
                },
                tabBar = { OmnibusTabBar(tabs, selectedIndex, onSelect, Modifier.testTag(ShellTestTags.TAB_BAR)) },
                modifier = Modifier.testTag(ShellTestTags.OVERLAY),
            )
        },
        modifier = modifier,
        navigationLayout = navigationLayout,
        rail = {
            OmnibusNavigationRail(
                tabs,
                selectedIndex,
                onSelect,
                Modifier.testTag(ShellTestTags.RAIL),
                footer = { space ->
                    if (showsMiniPlayer) {
                        MiniPlayerHost(uiState = miniPlayer, reduceMotion = reduceMotion) { shown ->
                            ShellMiniPlayerRailBlock(uiState = shown, space = space, actions = miniPlayerActions)
                        }
                    }
                },
            )
        },
        // The rail has no strip to collapse, so the content's scrolling only reaches the shell in the bar layout.
        contentNestedScroll = if (bar) collapse.connection else null,
        content = content,
    )
}
