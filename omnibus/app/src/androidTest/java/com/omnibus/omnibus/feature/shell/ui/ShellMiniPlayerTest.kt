package com.omnibus.omnibus.feature.shell.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerActions
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerTestTags
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.shell.ShellNavigationLayout
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.OmnibusTabBarItem
import com.omnibus.omnibus.ui.components.bottomOverlay
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

/** A column of [rows] short rows filling the content, so a swipe over it scrolls the list. */
@Composable
private fun RowList(rows: Int) {
    LazyColumn(Modifier.fillMaxSize().testTag(LIST_TAG)) {
        items(rows) { Text("Row $it", Modifier.fillMaxWidth().padding(16.dp)) }
    }
}

private const val LIST_TAG = "shell-mini-player-list"

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

    // #286 TC-11 (R1)
    @Test
    fun issue286_tc11_stripIsFlushOnTheTabBar() {
        setShell(mutableStateOf(ShellNavigationLayout.Bar))

        val root = rule.onRoot().bounds()
        val overlay = rule.onNodeWithTag(ShellTestTags.OVERLAY).bounds()
        val strip = rule.onNodeWithTag(MiniPlayerTestTags.BAR).bounds()
        val tabBar = rule.onNodeWithTag(ShellTestTags.TAB_BAR).bounds()
        assertEquals("the strip's left edge", root.left, strip.left, 1f)
        assertEquals("the strip's right edge", root.right, strip.right, 1f)
        assertEquals("the strip's top edge", overlay.top, strip.top, 1f)
        assertEquals("the seam between the strip and the tab bar", tabBar.top, strip.bottom, 1f)
    }

    // #286 TC-14a (R4)
    @Test
    fun issue286_tc14a_scrollingTheListCollapsesAndExpands() {
        setShell(mutableStateOf(ShellNavigationLayout.Bar), content = { RowList(100) })

        swipeListUp()
        rule.onAllNodesWithTag(MiniPlayerTestTags.STRIP_COLLAPSED).assertCountEquals(1)
        rule.onAllNodesWithTag(MiniPlayerTestTags.STRIP_EXPANDED).assertCountEquals(0)

        swipeListDown()
        rule.onAllNodesWithTag(MiniPlayerTestTags.STRIP_EXPANDED).assertCountEquals(1)
        rule.onAllNodesWithTag(MiniPlayerTestTags.STRIP_COLLAPSED).assertCountEquals(0)
    }

    // #286 TC-14b (R4)
    @Test
    fun issue286_tc14b_aListThatCannotScrollNeverCollapses() {
        setShell(mutableStateOf(ShellNavigationLayout.Bar), content = { RowList(3) })

        swipeListUp()
        rule.onAllNodesWithTag(MiniPlayerTestTags.STRIP_EXPANDED).assertCountEquals(1)
        rule.onAllNodesWithTag(MiniPlayerTestTags.STRIP_COLLAPSED).assertCountEquals(0)
    }

    // #286 TC-18a (R7)
    @Test
    fun issue286_tc18a_newScreenResetsToExpanded() {
        val resetKey = mutableStateOf(0)
        setShell(mutableStateOf(ShellNavigationLayout.Bar), resetKey = resetKey, content = { RowList(100) })
        swipeListUp()
        rule.onAllNodesWithTag(MiniPlayerTestTags.STRIP_COLLAPSED).assertCountEquals(1)

        rule.runOnIdle { resetKey.value++ }
        rule.waitForIdle()

        rule.onAllNodesWithTag(MiniPlayerTestTags.STRIP_EXPANDED).assertCountEquals(1)
    }

    // #286 TC-18b (R7)
    @Test
    fun issue286_tc18b_reappearingResetsToExpanded() {
        val state = mutableStateOf(playing)
        setShell(mutableStateOf(ShellNavigationLayout.Bar), state = state, content = { RowList(100) })
        swipeListUp()
        rule.onAllNodesWithTag(MiniPlayerTestTags.STRIP_COLLAPSED).assertCountEquals(1)

        rule.runOnIdle { state.value = playing.copy(visible = false) }
        rule.waitForIdle()
        rule.runOnIdle { state.value = playing }
        rule.waitForIdle()

        rule.onAllNodesWithTag(MiniPlayerTestTags.STRIP_EXPANDED).assertCountEquals(1)
    }

    // #286 TC-19a (R14)
    @Test
    fun issue286_tc19a_bookDetailHidesTheStrip() {
        setShell(mutableStateOf(ShellNavigationLayout.Bar), shows = mutableStateOf(false))

        rule.onAllNodesWithTag(MiniPlayerTestTags.BAR).assertCountEquals(0)
        val overlay = rule.onNodeWithTag(ShellTestTags.OVERLAY).bounds().height
        val tabBar = rule.onNodeWithTag(ShellTestTags.TAB_BAR).bounds().height
        assertEquals("the overlay is the tab bar alone", tabBar, overlay, 1f)
    }

    // #286 TC-20 (R16)
    @Test
    fun issue286_tc20_overlayInsetTracksTheStrip() {
        var inset = 0
        setShell(
            mutableStateOf(ShellNavigationLayout.Bar),
            content = {
                inset = WindowInsets.bottomOverlay.getBottom(LocalDensity.current)
                RowList(100)
            },
        )
        val expandedOverlay = rule.onNodeWithTag(ShellTestTags.OVERLAY).bounds().height
        assertEquals("the expanded inset", expandedOverlay, inset.toFloat(), 1f)

        swipeListUp()
        val collapsedOverlay = rule.onNodeWithTag(ShellTestTags.OVERLAY).bounds().height
        assertEquals("the collapsed inset", collapsedOverlay, inset.toFloat(), 1f)
        assertTrue("the collapsed overlay $collapsedOverlay is smaller than $expandedOverlay", collapsedOverlay < expandedOverlay)
    }

    /** Swipes up over the list, from the middle toward the top, so the gesture never starts on the bottom overlay. */
    private fun swipeListUp() {
        rule.onNodeWithTag(LIST_TAG).performTouchInput {
            swipe(Offset(centerX, height * 0.5f), Offset(centerX, height * 0.1f), 300)
        }
        rule.waitForIdle()
    }

    /** Swipes down over the list, the reverse of [swipeListUp]. */
    private fun swipeListDown() {
        rule.onNodeWithTag(LIST_TAG).performTouchInput {
            swipe(Offset(centerX, height * 0.1f), Offset(centerX, height * 0.5f), 300)
        }
        rule.waitForIdle()
    }

    /**
     * Renders [ShellScaffold] edge to edge with the mini player from [state] (shown while [shows]), and the tab bar or rail
     * of [layout]. Every action counts into [calls]. [resetKey] is the shell's collapse reset key.
     */
    private fun setShell(
        layout: MutableState<ShellNavigationLayout>,
        state: MutableState<MiniPlayerUIState> = mutableStateOf(playing),
        shows: MutableState<Boolean> = mutableStateOf(true),
        calls: Calls = Calls(),
        resetKey: MutableState<Int> = mutableStateOf(0),
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
                    collapseResetKey = resetKey.value,
                    modifier = Modifier.fillMaxSize(),
                    reduceMotion = false,
                    content = content,
                )
            }
        }
        rule.waitForIdle()
    }
}
