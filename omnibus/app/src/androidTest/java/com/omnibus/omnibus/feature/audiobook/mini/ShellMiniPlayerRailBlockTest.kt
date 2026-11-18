package com.omnibus.omnibus.feature.audiobook.mini

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Callback counts for the rail block, so each test can see which control, if any, was pressed. */
private class RailCalls {
    var expands = 0
    var expandedUuid: String? = null
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

/**
 * Sets the block alone in a rail-width column, with the state and the space read on every composition, so a change to
 * either shows without another setContent.
 */
private fun ComposeContentTestRule.setBlock(state: () -> MiniPlayerUIState, space: () -> Dp, calls: RailCalls) {
    setContent {
        OmnibusTheme {
            Box(Modifier.fillMaxSize()) {
                ShellMiniPlayerRailBlock(
                    uiState = state(),
                    space = space(),
                    actions = MiniPlayerActions(
                        onExpand = { uuid ->
                            calls.expands++
                            calls.expandedUuid = uuid
                        },
                        onSkipBack = {},
                        onPlayPauseToggled = { calls.toggles++ },
                        onStop = { calls.stops++ },
                    ),
                    modifier = Modifier.width(80.dp),
                )
            }
        }
    }
}

/**
 * Taps the cover near its bottom edge. The stop button overlaps the cover's top-right corner, so the centre is avoided.
 */
private fun ComposeContentTestRule.tapCover() {
    val inset = with(density) { 8.dp.toPx() }
    onNodeWithTag(MiniPlayerTestTags.RAIL_COVER).performTouchInput { click(Offset(centerX, bottom - inset)) }
    waitForIdle()
}

/** Every control in [tags] is at least 48dp wide and tall, in the window's density. */
private fun ComposeContentTestRule.assertTargetsAtLeast48dp(vararg tags: String) {
    val minimum = with(density) { 48.dp.toPx() } - 1f
    tags.forEach { tag ->
        val bounds = onNodeWithTag(tag).bounds()
        assertTrue("$tag is ${bounds.width} x ${bounds.height}px", bounds.width >= minimum && bounds.height >= minimum)
    }
}

/** The node reads its title, subtitle and percentage, and is a button whose tap is labelled "Open player". */
private fun SemanticsNode.assertOpensThePlayer(appContext: Context) {
    val description = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ").orEmpty()
    assertTrue("reads the title, subtitle and percentage: $description", "Mini Title" in description && "Chapter 7" in description)
    assertTrue(
        "reads the percentage: $description",
        appContext.getString(R.string.audiobook_mini_percent_listened, 35) in description,
    )
    assertEquals(Role.Button, config.getOrNull(SemanticsProperties.Role))
    assertEquals(appContext.getString(R.string.audiobook_mini_open_player), config.getOrNull(SemanticsActions.OnClick)?.label)
}

/** The rail's mini player block alone, in the full and compact layouts (#286 R8-R11, R18, R19). */
class ShellMiniPlayerRailBlockTest {

    @get:Rule
    val rule = createComposeRule()

    // #286 TC-27 (R10, R13)
    @Test
    fun issue286_tc27_coverOpensAndPillToggles() {
        val calls = RailCalls()
        rule.setBlock({ playing }, { Dp.Infinity }, calls)
        rule.waitForIdle()

        rule.tapCover()
        rule.onNodeWithTag(MiniPlayerTestTags.PLAY_PAUSE).performClick()
        rule.waitForIdle()

        assertEquals("the cover opens once with the book's uuid", 1, calls.expands)
        assertEquals("book-1", calls.expandedUuid)
        assertEquals("the pill toggles once", 1, calls.toggles)
        assertEquals("the stop button stays idle", 0, calls.stops)
    }

    // #286 TC-28a (R11)
    @Test
    fun issue286_tc28a_noSpaceDrawsTheCompactBlock() {
        val calls = RailCalls()
        rule.setBlock({ playing }, { 0.dp }, calls)
        rule.waitForIdle()

        rule.onAllNodesWithTag(MiniPlayerTestTags.RAIL_COMPACT).assertCountEquals(1)
        rule.onAllNodesWithTag(MiniPlayerTestTags.RAIL_COVER).assertCountEquals(0)
        rule.onAllNodesWithTag(MiniPlayerTestTags.PROGRESS_FILL).assertCountEquals(0)
        val stop = rule.onNodeWithTag(MiniPlayerTestTags.STOP).bounds()
        val playPause = rule.onNodeWithTag(MiniPlayerTestTags.PLAY_PAUSE).bounds()
        assertTrue("the stop button sits above play/pause: ${stop.top} vs ${playPause.top}", stop.top < playPause.top)

        rule.onNodeWithTag(MiniPlayerTestTags.RAIL_PERCENT).performClick()
        rule.onNodeWithTag(MiniPlayerTestTags.STOP).performClick()
        rule.onNodeWithTag(MiniPlayerTestTags.PLAY_PAUSE).performClick()
        rule.waitForIdle()

        assertEquals("the percentage opens once", 1, calls.expands)
        assertEquals("stop fires once", 1, calls.stops)
        assertEquals("play/pause toggles once", 1, calls.toggles)
    }

    // #286 TC-28b (R11)
    @Test
    fun issue286_tc28b_ampleSpaceDrawsTheFullBlock() {
        rule.setBlock({ playing }, { Dp.Infinity }, RailCalls())
        rule.waitForIdle()

        rule.onAllNodesWithTag(MiniPlayerTestTags.RAIL_FULL).assertCountEquals(1)
        rule.onAllNodesWithTag(MiniPlayerTestTags.RAIL_COMPACT).assertCountEquals(0)
    }

    // #286 TC-29 (R12)
    @Test
    fun issue286_tc29_unknownProgressHidesBarAndLabel() {
        val space = mutableStateOf<Dp>(Dp.Infinity)
        val calls = RailCalls()
        rule.setBlock({ playing.copy(percentListened = null) }, { space.value }, calls)
        rule.waitForIdle()

        rule.onAllNodesWithTag(MiniPlayerTestTags.PROGRESS_FILL).assertCountEquals(0)
        rule.tapCover()
        assertEquals("the full cover still opens", 1, calls.expands)

        rule.runOnIdle { space.value = 0.dp }
        rule.waitForIdle()
        rule.onAllNodesWithTag(MiniPlayerTestTags.RAIL_PERCENT).assertCountEquals(0)
        rule.onAllNodesWithTag(MiniPlayerTestTags.PROGRESS_FILL).assertCountEquals(0)
    }

    // #286 TC-30 (R18)
    @Test
    fun issue286_tc30_coverAndLabelReadAsOpenPlayer() {
        val space = mutableStateOf<Dp>(Dp.Infinity)
        rule.setBlock({ playing.copy(percentListened = 35) }, { space.value }, RailCalls())
        rule.waitForIdle()

        rule.onNodeWithTag(MiniPlayerTestTags.RAIL_COVER).fetchSemanticsNode().assertOpensThePlayer(context)
        rule.onNodeWithContentDescription(context.getString(R.string.audiobook_mini_stop)).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.audiobook_pause)).assertExists()

        rule.runOnIdle { space.value = 0.dp }
        rule.waitForIdle()
        rule.onNodeWithTag(MiniPlayerTestTags.RAIL_PERCENT).fetchSemanticsNode().assertOpensThePlayer(context)
        rule.onNodeWithContentDescription(context.getString(R.string.audiobook_mini_stop)).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.audiobook_pause)).assertExists()
    }

    // #286 TC-31 (R19)
    @Test
    fun issue286_tc31_targetsAre48dpAndCloseWinsOverTheCover() {
        val space = mutableStateOf<Dp>(Dp.Infinity)
        val calls = RailCalls()
        rule.setBlock({ playing.copy(percentListened = 35) }, { space.value }, calls)
        rule.waitForIdle()

        rule.assertTargetsAtLeast48dp(MiniPlayerTestTags.STOP, MiniPlayerTestTags.PLAY_PAUSE)
        val block = rule.onNodeWithTag(MiniPlayerTestTags.BAR).bounds()
        val stop = rule.onNodeWithTag(MiniPlayerTestTags.STOP).bounds()
        assertTrue("the stop button $stop is inside the block $block", stop.left >= block.left - 1f && stop.right <= block.right + 1f)
        assertTrue("the stop button $stop is inside the block $block vertically", stop.top >= block.top - 1f && stop.bottom <= block.bottom + 1f)

        rule.onNodeWithTag(MiniPlayerTestTags.STOP).performClick()
        rule.waitForIdle()
        assertEquals("the overlapping stop button wins over the cover", 1, calls.stops)
        assertEquals("the cover is not pressed", 0, calls.expands)

        rule.runOnIdle { space.value = 0.dp }
        rule.waitForIdle()
        rule.assertTargetsAtLeast48dp(MiniPlayerTestTags.STOP, MiniPlayerTestTags.PLAY_PAUSE, MiniPlayerTestTags.RAIL_PERCENT)
    }
}
