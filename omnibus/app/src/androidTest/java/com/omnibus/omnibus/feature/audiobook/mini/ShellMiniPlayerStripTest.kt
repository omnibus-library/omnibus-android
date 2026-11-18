package com.omnibus.omnibus.feature.audiobook.mini

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Callback counts for the docked strip, so each test can see which action, if any, was taken. */
private class StripCalls {
    var expands = 0
    var expandedUuid: String? = null
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

private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

/** Sets the strip alone, with [collapsed] read on every composition so a change to it shows without another setContent. */
private fun ComposeContentTestRule.setStrip(state: MiniPlayerUIState, collapsed: State<Boolean>, calls: StripCalls) {
    setContent {
        OmnibusTheme {
            Box(Modifier.fillMaxSize()) {
                ShellMiniPlayerStrip(
                    uiState = state,
                    collapsed = collapsed.value,
                    reduceMotion = false,
                    actions = MiniPlayerActions(
                        onExpand = { uuid ->
                            calls.expands++
                            calls.expandedUuid = uuid
                        },
                        onSkipBack = { calls.skips++ },
                        onPlayPauseToggled = { calls.toggles++ },
                        onStop = { calls.stops++ },
                    ),
                )
            }
        }
    }
}

/** Every control in [tags] is at least 48dp wide and tall, in the window's density. */
private fun ComposeContentTestRule.assertTargetsAtLeast48dp(vararg tags: String) {
    val minimum = with(density) { 48.dp.toPx() } - 1f
    tags.forEach { tag ->
        val bounds = onNodeWithTag(tag).bounds()
        assertTrue("$tag is ${bounds.width} x ${bounds.height}px", bounds.width >= minimum && bounds.height >= minimum)
    }
}

/** The docked strip on its own, in both states: what it shows, what its controls do, and how big they are (#286 R1-R3, R12, R18, R19). */
class ShellMiniPlayerStripTest {

    @get:Rule
    val rule = createComposeRule()

    // #286 TC-12 (R2)
    @Test
    fun issue286_tc12_expandedShowsEveryControl() {
        rule.setStrip(playing.copy(percentListened = 35), mutableStateOf(false), StripCalls())
        rule.waitForIdle()

        rule.onAllNodesWithTag(MiniPlayerTestTags.STRIP_EXPANDED).assertCountEquals(1)
        rule.onAllNodesWithTag(MiniPlayerTestTags.STRIP_COLLAPSED).assertCountEquals(0)
        listOf(MiniPlayerTestTags.BACK_15, MiniPlayerTestTags.PLAY_PAUSE, MiniPlayerTestTags.STOP).forEach {
            rule.onAllNodesWithTag(it).assertCountEquals(1)
        }
        val text = rule.onNodeWithTag(MiniPlayerTestTags.BODY).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertTrue("the body reads the title: $text", "Mini Title" in text)
        assertTrue("the body reads the subtitle: $text", text.any { it.startsWith("Chapter 7") })
    }

    // #286 TC-13 (R3)
    @Test
    fun issue286_tc13_collapsedShowsTitlePlayAndClose() {
        rule.setStrip(playing.copy(percentListened = 35), mutableStateOf(true), StripCalls())
        rule.waitForIdle()

        rule.onAllNodesWithTag(MiniPlayerTestTags.STRIP_COLLAPSED).assertCountEquals(1)
        rule.onAllNodesWithTag(MiniPlayerTestTags.STRIP_EXPANDED).assertCountEquals(0)
        rule.onAllNodesWithTag(MiniPlayerTestTags.BACK_15).assertCountEquals(0)
        listOf(MiniPlayerTestTags.PLAY_PAUSE, MiniPlayerTestTags.STOP).forEach {
            rule.onAllNodesWithTag(it).assertCountEquals(1)
        }
        val text = rule.onNodeWithTag(MiniPlayerTestTags.BODY).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertTrue("the body reads the title: $text", "Mini Title" in text)
        assertTrue("the body reads no subtitle: $text", text.none { it.startsWith("Chapter 7") })
    }

    // #286 TC-16a (R6, R13)
    @Test
    fun issue286_tc16a_expandedTapsReachTheirOwnAction() {
        val calls = StripCalls()
        rule.setStrip(playing, mutableStateOf(false), calls)
        rule.waitForIdle()

        rule.onNodeWithTag(MiniPlayerTestTags.BODY).performClick()
        rule.onNodeWithTag(MiniPlayerTestTags.BACK_15).performClick()
        rule.onNodeWithTag(MiniPlayerTestTags.PLAY_PAUSE).performClick()
        rule.onNodeWithTag(MiniPlayerTestTags.STOP).performClick()
        rule.waitForIdle()

        assertEquals(1, calls.expands)
        assertEquals("book-1", calls.expandedUuid)
        assertEquals(1, calls.skips)
        assertEquals(1, calls.toggles)
        assertEquals(1, calls.stops)
    }

    // #286 TC-16b (R6, R13)
    @Test
    fun issue286_tc16b_collapsedTapsReachTheirOwnAction() {
        val calls = StripCalls()
        rule.setStrip(playing, mutableStateOf(true), calls)
        rule.waitForIdle()

        rule.onNodeWithTag(MiniPlayerTestTags.BODY).performClick()
        rule.onNodeWithTag(MiniPlayerTestTags.PLAY_PAUSE).performClick()
        rule.onNodeWithTag(MiniPlayerTestTags.STOP).performClick()
        rule.waitForIdle()

        assertEquals(1, calls.expands)
        assertEquals("book-1", calls.expandedUuid)
        assertEquals(0, calls.skips)
        assertEquals(1, calls.toggles)
        assertEquals(1, calls.stops)
    }

    // #286 TC-22 (R18)
    @Test
    fun issue286_tc22_bodyReadsProgressAndOpensThePlayer() {
        rule.setStrip(playing.copy(percentListened = 35), mutableStateOf(false), StripCalls())
        rule.waitForIdle()

        val body = rule.onNodeWithTag(MiniPlayerTestTags.BODY).fetchSemanticsNode()
        assertEquals(Role.Button, body.config.getOrNull(SemanticsProperties.Role))
        assertEquals(
            context.getString(R.string.audiobook_mini_open_player),
            body.config.getOrNull(SemanticsActions.OnClick)?.label,
        )
        assertEquals("35 percent listened", body.config.getOrNull(SemanticsProperties.StateDescription))
    }

    // #286 TC-22 (R12, R18)
    @Test
    fun issue286_tc22_unknownProgressHasNoStateDescription() {
        rule.setStrip(playing.copy(percentListened = null), mutableStateOf(false), StripCalls())
        rule.waitForIdle()

        val body = rule.onNodeWithTag(MiniPlayerTestTags.BODY).fetchSemanticsNode()
        assertNull(body.config.getOrNull(SemanticsProperties.StateDescription))
    }

    // #286 TC-23 (R19)
    @Test
    fun issue286_tc23_controlsAreAtLeast48dp() {
        val collapsed = mutableStateOf(false)
        rule.setStrip(playing, collapsed, StripCalls())
        rule.waitForIdle()
        rule.assertTargetsAtLeast48dp(MiniPlayerTestTags.BACK_15, MiniPlayerTestTags.PLAY_PAUSE, MiniPlayerTestTags.STOP)

        rule.runOnIdle { collapsed.value = true }
        rule.waitForIdle()
        rule.assertTargetsAtLeast48dp(MiniPlayerTestTags.PLAY_PAUSE, MiniPlayerTestTags.STOP)
    }

    // #286 TC-24 (R12)
    @Test
    fun issue286_tc24_unknownProgressHidesTheLine() {
        val collapsed = mutableStateOf(false)
        rule.setStrip(playing.copy(percentListened = null), collapsed, StripCalls())
        rule.waitForIdle()
        rule.onAllNodesWithTag(MiniPlayerTestTags.PROGRESS_FILL).assertCountEquals(0)
        rule.onAllNodesWithTag(MiniPlayerTestTags.PLAY_PAUSE).assertCountEquals(1)

        rule.runOnIdle { collapsed.value = true }
        rule.waitForIdle()
        rule.onAllNodesWithTag(MiniPlayerTestTags.PROGRESS_FILL).assertCountEquals(0)
        rule.onAllNodesWithTag(MiniPlayerTestTags.PLAY_PAUSE).assertCountEquals(1)
    }
}
