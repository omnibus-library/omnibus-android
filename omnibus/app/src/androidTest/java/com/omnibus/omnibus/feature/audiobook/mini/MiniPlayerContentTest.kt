package com.omnibus.omnibus.feature.audiobook.mini

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Callback counts for [MiniPlayerContent], so each test can see which button, if any, was pressed. */
private class Calls {
    var expands = 0
    var expandedUuid: String? = null
    var skips = 0
    var toggles = 0
    var stops = 0

    /** True when no callback has run. */
    fun none() = expands == 0 && skips == 0 && toggles == 0 && stops == 0
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

private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

/** Sets the bar alone, read from [state] on every composition, so a change to it shows without another setContent. */
private fun ComposeContentTestRule.setBar(state: () -> MiniPlayerUIState, calls: Calls) {
    setContent {
        OmnibusTheme {
            Box(Modifier.fillMaxSize()) {
                MiniPlayerContent(
                    uiState = state(),
                    onExpand = { uuid ->
                        calls.expands++
                        calls.expandedUuid = uuid
                    },
                    onSkipBack = { calls.skips++ },
                    onPlayPauseToggled = { calls.toggles++ },
                    onStop = { calls.stops++ },
                )
            }
        }
    }
}

/** Every test tag under this node in the unmerged tree. */
private fun SemanticsNode.descendantTags(): List<String> = children.flatMap { child ->
    listOfNotNull(child.config.getOrNull(SemanticsProperties.TestTag)) + child.descendantTags()
}

class MiniPlayerContentTest {

    @get:Rule
    val rule = createComposeRule()

    // P-MINI-1, P-MINI-AC1
    // TC-28 (P-MINI-1)
    @Test
    fun showsTheTitleAndSubtitleAndOmitsAMissingSubtitle() {
        val state = mutableStateOf(playing)
        rule.setBar({ state.value }, Calls())
        rule.waitForIdle()

        rule.onNodeWithText("Mini Title", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Chapter 7: The Question of Sex", useUnmergedTree = true).assertIsDisplayed()

        rule.runOnIdle { state.value = playing.copy(subtitle = null) }
        rule.waitForIdle()

        rule.onNodeWithText("Mini Title", useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithText("Chapter 7", useUnmergedTree = true).assertCountEquals(0)
    }

    // P-MINI-1
    // TC-29 (P-MINI-1)
    @Test
    fun longTitlesStayOnOneLine() {
        val longTitle = "A title far longer than the widest cell can hold, so it has to be cut short with an ellipsis"
        val title = mutableStateOf("Short")
        rule.setBar({ playing.copy(title = title.value) }, Calls())
        rule.waitForIdle()
        val shortBar = rule.onNodeWithTag(MiniPlayerTestTags.BAR).fetchSemanticsNode().size.height
        val shortTitle = rule.onNodeWithText("Short", useUnmergedTree = true).fetchSemanticsNode().size.height

        rule.runOnIdle { title.value = longTitle }
        rule.waitForIdle()
        val longBar = rule.onNodeWithTag(MiniPlayerTestTags.BAR).fetchSemanticsNode().size.height
        val longNode = rule.onNodeWithText(longTitle, useUnmergedTree = true).fetchSemanticsNode()

        assertTrue(abs(shortBar - longBar) <= 1)
        assertTrue(abs(shortTitle - longNode.size.height) <= 1)
        val body = rule.onNodeWithTag(MiniPlayerTestTags.BODY).fetchSemanticsNode().size.width
        assertTrue(longNode.size.width <= body + 1)
    }

    // P-MINI-1 (whole-book line)
    // TC-30 (P-MINI-1)
    @Test
    fun progressFillIsTheStateRatioOfTheTrack() {
        val progress = mutableStateOf(0f)
        rule.setBar({ playing.copy(progress = progress.value) }, Calls())

        for (value in listOf(0f, 0.25f, 1f)) {
            rule.runOnIdle { progress.value = value }
            rule.waitForIdle()
            val track = rule.onNodeWithTag(MiniPlayerTestTags.PROGRESS_TRACK, useUnmergedTree = true)
                .fetchSemanticsNode().size.width
            val fill = rule.onNodeWithTag(MiniPlayerTestTags.PROGRESS_FILL, useUnmergedTree = true)
                .fetchSemanticsNode().size.width
            val bar = rule.onNodeWithTag(MiniPlayerTestTags.BAR).fetchSemanticsNode().size.width

            assertTrue("track spans the bar at $value", abs(track - bar) <= 1)
            assertTrue("fill is $value of the track", abs(fill - value * track) <= 1f)
        }
    }

    // P-MINI-AC2
    // TC-31 (P-MINI-1)
    @Test
    fun theControlsSitInOrderToTheRightOfTheTitle() {
        rule.setBar({ playing }, Calls())
        rule.waitForIdle()

        val title = rule.onNodeWithText("Mini Title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val back = rule.onNodeWithTag(MiniPlayerTestTags.BACK_15, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val playPause = rule.onNodeWithTag(MiniPlayerTestTags.PLAY_PAUSE, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val stop = rule.onNodeWithTag(MiniPlayerTestTags.STOP, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val track = rule.onNodeWithTag(MiniPlayerTestTags.PROGRESS_TRACK, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        assertTrue(title.right <= back.left + 1f)
        assertTrue(back.right <= playPause.left + 1f)
        assertTrue(playPause.right <= stop.left + 1f)
        assertTrue("title and buttons share a band", title.top < back.bottom && back.top < title.bottom)
        assertTrue("the track sits above the row", track.bottom <= back.top + 1f)
    }

    // P-MINI-AC5
    // TC-32 (P-MINI-2)
    @Test
    fun playPauseLabelFollowsThePlayingState() {
        val isPlaying = mutableStateOf(false)
        rule.setBar({ playing.copy(isPlaying = isPlaying.value) }, Calls())
        val play = context.getString(R.string.audiobook_play)
        val pause = context.getString(R.string.audiobook_pause)

        rule.onNodeWithContentDescription(play).assertIsDisplayed()
        rule.onAllNodesWithText(pause).assertCountEquals(0)

        rule.runOnIdle { isPlaying.value = true }
        rule.waitForIdle()

        rule.onNodeWithContentDescription(pause).assertIsDisplayed()
        rule.onAllNodesWithText(play).assertCountEquals(0)
    }

    // P-MINI-AC5, P-A11Y-1
    // TC-33 (P-MINI-AC2, AC5)
    @Test
    fun buttonsHaveTheirAccessibilityLabels() {
        rule.setBar({ playing }, Calls())
        rule.waitForIdle()

        rule.onNodeWithContentDescription(context.getString(R.string.audiobook_back_15)).assertHasClickAction()
        rule.onNodeWithContentDescription(context.getString(R.string.audiobook_pause)).assertHasClickAction()
        rule.onNodeWithContentDescription(context.getString(R.string.audiobook_mini_stop)).assertHasClickAction()
        rule.onNodeWithTag(MiniPlayerTestTags.STOP).assertIsDisplayed()
    }

    // P-MINI-AC5
    // TC-34 (P-MINI-2)
    @Test
    fun theBodyIsOneLabelledClickTarget() {
        rule.setBar({ playing }, Calls())
        rule.waitForIdle()

        val body = rule.onNodeWithTag(MiniPlayerTestTags.BODY)
        val clickLabel = body.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label
        assertEquals(context.getString(R.string.audiobook_mini_open_player), clickLabel)
        val merged = body.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertTrue("the body reads the title: $merged", "Mini Title" in merged)
        assertTrue("the body reads the subtitle: $merged", merged.any { it.startsWith("Chapter 7") })

        val unmerged = rule.onNodeWithTag(MiniPlayerTestTags.BODY, useUnmergedTree = true).fetchSemanticsNode()
        val inside = unmerged.descendantTags()
        assertTrue(MiniPlayerTestTags.BACK_15 !in inside)
        assertTrue(MiniPlayerTestTags.PLAY_PAUSE !in inside)
        assertTrue(MiniPlayerTestTags.STOP !in inside)
    }

    // P-MINI-AC5, P-A11Y-1 (48 dp targets)
    // TC-35 (P-A11Y-1)
    @Test
    fun buttonsMeetTheMinimumTouchTarget() {
        rule.setBar({ playing }, Calls())
        rule.waitForIdle()
        val minimum = with(rule.density) { 48.dp.roundToPx() }

        listOf(MiniPlayerTestTags.BACK_15, MiniPlayerTestTags.PLAY_PAUSE, MiniPlayerTestTags.STOP).forEach { tag ->
            val size = rule.onNodeWithTag(tag).fetchSemanticsNode().size
            assertTrue("$tag is at least 48 dp wide", size.width >= minimum - 1)
            assertTrue("$tag is at least 48 dp tall", size.height >= minimum - 1)
        }
    }

    // P-MINI-AC5
    // TC-36 (P-A11Y-1)
    @Test
    fun progressAndCoverAreDecorative() {
        rule.setBar({ playing }, Calls())
        rule.waitForIdle()

        listOf(MiniPlayerTestTags.PROGRESS_TRACK, MiniPlayerTestTags.PROGRESS_FILL).forEach { tag ->
            val node = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("$tag has no content description", !node.config.contains(SemanticsProperties.ContentDescription))
            assertTrue("$tag is not a click target", !node.config.contains(SemanticsActions.OnClick))
        }
        // The title text appears once in the unmerged tree: the cover adds no second copy of it.
        rule.onAllNodesWithText("Mini Title", useUnmergedTree = true).assertCountEquals(1)
    }

    // P-MINI-2, P-MINI-4
    // TC-37 (P-MINI-2)
    @Test
    fun tappingTheBodyExpandsWithTheBookUuid() {
        val calls = Calls()
        rule.setBar({ playing }, calls)
        rule.waitForIdle()

        rule.onNodeWithTag(MiniPlayerTestTags.BODY).performClick()
        rule.waitForIdle()

        assertEquals(1, calls.expands)
        assertEquals("book-1", calls.expandedUuid)
        assertEquals(0, calls.skips + calls.toggles + calls.stops)
    }

    // P-MINI-AC2
    // TC-38 (P-MINI-AC2)
    @Test
    fun backFifteenCallsOnlySkipBack() {
        val calls = Calls()
        rule.setBar({ playing }, calls)
        rule.waitForIdle()

        rule.onNodeWithTag(MiniPlayerTestTags.BACK_15).performClick()
        rule.waitForIdle()

        assertEquals(1, calls.skips)
        assertEquals(0, calls.expands)
        assertEquals(0, calls.toggles + calls.stops)
    }

    // P-MINI-AC2
    // TC-39 (P-MINI-AC2)
    @Test
    fun playPauseCallsOnlyTheToggle() {
        val calls = Calls()
        rule.setBar({ playing }, calls)
        rule.waitForIdle()

        rule.onNodeWithTag(MiniPlayerTestTags.PLAY_PAUSE).performClick()
        rule.waitForIdle()

        assertEquals(1, calls.toggles)
        assertEquals(0, calls.expands)
        assertEquals(0, calls.skips + calls.stops)
    }

    // P-MINI-3, P-MINI-AC3
    // TC-40 (P-MINI-3)
    @Test
    fun stopCallsOnlyStop() {
        val calls = Calls()
        rule.setBar({ playing }, calls)
        rule.waitForIdle()

        rule.onNodeWithTag(MiniPlayerTestTags.STOP).performClick()
        rule.waitForIdle()

        assertEquals(1, calls.stops)
        assertEquals(0, calls.expands)
        assertEquals(0, calls.skips + calls.toggles)
        assertTrue(calls.expandedUuid == null)
    }

    // P-MINI-1, P-MINI-AC1
    // TC-50 (P-MINI-1)
    @Test
    fun theBarSitsBetweenTheContentAndTheTabBar() {
        rule.setContent {
            OmnibusTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f).testTag("stand_in_content"))
                    MiniPlayerContent(
                        uiState = playing,
                        onExpand = {},
                        onSkipBack = {},
                        onPlayPauseToggled = {},
                        onStop = {},
                    )
                    Box(Modifier.fillMaxWidth().fillMaxHeight(0.08f).testTag("stand_in_tab_bar"))
                }
            }
        }
        rule.waitForIdle()

        val content = rule.onNodeWithTag("stand_in_content").fetchSemanticsNode().boundsInRoot
        val bar = rule.onNodeWithTag(MiniPlayerTestTags.BAR).fetchSemanticsNode().boundsInRoot
        val tabBar = rule.onNodeWithTag("stand_in_tab_bar").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().size.width

        assertTrue(content.bottom <= bar.top + 1f)
        assertTrue(bar.bottom <= tabBar.top + 1f)
        assertTrue(abs((bar.right - bar.left) - root) <= 1f)
        assertTrue(abs(bar.left - tabBar.left) <= 1f)
    }
}
