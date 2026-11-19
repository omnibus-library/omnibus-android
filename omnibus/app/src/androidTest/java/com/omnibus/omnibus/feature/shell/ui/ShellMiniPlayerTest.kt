package com.omnibus.omnibus.feature.shell.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerActions
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerTestTags
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerUIState
import com.omnibus.omnibus.feature.shell.ShellNavigationLayout
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.OmnibusTabBarItem
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Callback counts for the mini player's actions, so a test can see whether any of them fired. */
private class Calls {
    var expands = 0
    var skips = 0
    var toggles = 0
    var stops = 0
}

private val playing = MiniPlayerUIState(
    visible = true,
    bookUuid = "book-1",
    cover = CoverIdentity(uuid = "book-1", title = "Plate", creator = "An Author", hasCover = true),
    title = "Mini Title",
    subtitle = "Chapter 7: The Question of Sex",
    progress = 0.35f,
    isPlaying = true,
)

private fun items() = ShellTab.entries.map {
    OmnibusTabBarItem(InstrumentationRegistry.getInstrumentation().targetContext.getString(it.label), it.icon, it.selectedIcon)
}

/** The mini player inside [ShellScaffold], which the shell hands its state and actions to (#286 R17). */
class ShellMiniPlayerTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    // #286 TC-8 (R17)
    @Test
    fun issue286_tc8_switchingLayoutKeepsTheMiniPlayerWithoutCallbacks() {
        val layout = mutableStateOf(ShellNavigationLayout.Bar)
        val calls = Calls()
        setShell(layout, calls = calls)

        rule.onAllNodesWithTag(MiniPlayerTestTags.BAR).assertCountEquals(1)
        rule.runOnIdle { layout.value = ShellNavigationLayout.Rail }
        rule.waitForIdle()
        rule.onAllNodesWithTag(MiniPlayerTestTags.BAR).assertCountEquals(1)
        rule.runOnIdle { layout.value = ShellNavigationLayout.Bar }
        rule.waitForIdle()
        rule.onAllNodesWithTag(MiniPlayerTestTags.BAR).assertCountEquals(1)

        assertEquals(
            "expands, skips, toggles and stops after two layout switches",
            listOf(0, 0, 0, 0),
            listOf(calls.expands, calls.skips, calls.toggles, calls.stops),
        )
    }

    /**
     * Renders [ShellScaffold] edge to edge with the mini player from [state] (shown while [shows]), and the tab bar or rail
     * of [layout]. Every action counts into [calls].
     */
    private fun setShell(
        layout: MutableState<ShellNavigationLayout>,
        state: MutableState<MiniPlayerUIState> = mutableStateOf(playing),
        shows: MutableState<Boolean> = mutableStateOf(true),
        calls: Calls = Calls(),
        content: @Composable () -> Unit = { Box(Modifier.fillMaxSize()) },
    ) {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ShellScaffold(
                    navigationLayout = layout.value,
                    tabs = items(),
                    selectedIndex = 0,
                    onSelect = {},
                    miniPlayer = state.value,
                    miniPlayerActions = MiniPlayerActions(
                        onExpand = { calls.expands++ },
                        onSkipBack = { calls.skips++ },
                        onPlayPauseToggled = { calls.toggles++ },
                        onStop = { calls.stops++ },
                    ),
                    showsMiniPlayer = shows.value,
                    modifier = Modifier.fillMaxSize(),
                    reduceMotion = false,
                    content = content,
                )
            }
        }
        rule.waitForIdle()
    }
}
