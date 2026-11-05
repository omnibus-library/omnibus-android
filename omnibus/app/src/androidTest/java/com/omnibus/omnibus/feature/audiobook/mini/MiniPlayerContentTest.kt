package com.omnibus.omnibus.feature.audiobook.mini

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

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

/** Callbacks and semantics. MiniPlayerScreenshotTest covers how the bar looks. */
class MiniPlayerContentTest {

    @get:Rule
    val rule = createComposeRule()

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
}
