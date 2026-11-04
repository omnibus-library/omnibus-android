package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class PlayerScreenContentTest {

    @get:Rule
    val rule = createComposeRule()

    // R3, P-SCR-AC6 gate
    @Test
    fun checkingGateShowsNoControlsAndNoDialog() {
        var closed = 0
        rule.setPlayer(fullUiState(gate = PlayerGate.Checking), onClose = { closed++ })

        rule.onNodeWithTag(PlayerTestTags.ROOT).assertIsDisplayed()
        rule.onAllNodesWithTag(PlayerTestTags.TRANSPORT).assertCountEquals(0)
        rule.onAllNodesWithTag(PlayerTestTags.SCRUBBER).assertCountEquals(0)
        rule.onAllNodesWithText("Can't play offline").assertCountEquals(0)
        assertEquals(0, closed)
    }

    // R20 (P-SCR edge case: unloaded book closes the player)
    @Test
    fun leaveFlagClosesPlayerOnce() {
        val state = mutableStateOf(fullUiState(leave = false))
        var closed = 0
        rule.setPlayer(state, onClose = { closed++ })
        rule.waitForIdle()
        assertEquals(0, closed)

        rule.runOnIdle { state.value = state.value.copy(leave = true) }
        rule.waitForIdle()
        assertEquals(1, closed)

        // Recomposition with the flag still set must not close the player again.
        rule.runOnIdle { state.value = state.value.copy(isPlaying = false) }
        rule.waitForIdle()
        assertEquals(1, closed)
    }

    // R7, P-A11Y-6 (paused cover eases to 94%)
    @Test
    fun coverScalesDownWhilePausedWithReducedMotion() {
        val state = mutableStateOf(fullUiState(isPlaying = true))
        rule.setPlayer(state, reducedMotion = true)
        val playing = rule.boundsOf(PlayerTestTags.COVER)

        rule.runOnIdle { state.value = state.value.copy(isPlaying = false) }
        rule.waitForIdle()
        val paused = rule.boundsOf(PlayerTestTags.COVER)

        assertTrue("the paused cover should be smaller than the playing one", paused.width < playing.width)
        assertTrue("the paused cover should be shorter than the playing one", paused.height < playing.height)
        assertTrue("the cover should be shorter than the stage", paused.height < rule.boundsOf(PlayerTestTags.STAGE).height)
        assertTrue("the cover should be narrower than the screen", paused.width < rule.boundsOf(PlayerTestTags.ROOT).width)
    }

    // R7, P-SCR-5
    @Test
    fun coverIsCentredAboveTitlesAndNarrowerThanScreen() {
        rule.setPlayer(fullUiState(title = LONG_TITLE, author = LONG_AUTHOR))

        val root = rule.boundsOf(PlayerTestTags.ROOT)
        val cover = rule.boundsOf(PlayerTestTags.COVER)
        val title = rule.boundsOf(PlayerTestTags.TITLE)
        val stage = rule.boundsOf(PlayerTestTags.STAGE)

        assertTrue("the cover should be centred", abs(cover.center.x - root.center.x) <= root.width * 0.01f)
        assertTrue("the cover should be above the title", cover.top < title.top)
        assertTrue("the cover should end before the title starts", cover.bottom <= title.top + 1f)
        assertTrue("the cover should be narrower than the screen", cover.width < root.width)
        assertTrue("the cover should be shorter than the stage", cover.height < stage.height)
    }

    // P-SCR-3, AND-RUSE-4 (the wash and the floor fill the window)
    @Test
    fun washAndFloorFillTheWindowBehindTheControls() {
        rule.setPlayer(fullUiState())

        val root = rule.boundsOf(PlayerTestTags.ROOT)
        val window = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("the floor should fill the window width", abs(root.width - window.width) <= 1f)
        assertTrue("the floor should fill the window height", abs(root.height - window.height) <= 1f)
        assertTrue(
            "the controls should sit inside the window",
            rule.boundsOf(PlayerTestTags.CONTROLS).bottom <= root.bottom + 1f,
        )
    }

    // P-SCR-AC2 order
    @Test
    fun screenShowsCoverTitleAuthorChapterScrubberTransportToolRowInOrder() {
        val actions = RecordedActions()
        rule.setPlayer(fullUiState(), actions = actions.actions())

        val root = rule.boundsOf(PlayerTestTags.ROOT)
        val tops = listOf(
            PlayerTestTags.COVER,
            PlayerTestTags.TITLE,
            PlayerTestTags.AUTHOR,
            PlayerTestTags.CHAPTER_ROW,
            PlayerTestTags.SCRUBBER,
            PlayerTestTags.PLAY_PAUSE,
            PlayerTestTags.TOOL_ROW,
        ).map { rule.boundsOf(it) }

        tops.zipWithNext().forEach { (above, below) ->
            assertTrue("each row should sit below the one before it", above.top < below.top)
        }
        assertTrue("the tool row should end inside the window", tops.last().bottom <= root.bottom + 1f)
    }

    // P-SCR-AC2, R8: long titles clamp to two lines, the author to one
    @Test
    fun longTitleClampsToTwoLinesAndAuthorToOne() {
        rule.setPlayer(fullUiState(title = LONG_TITLE, author = LONG_AUTHOR))

        val title = rule.onNodeWithTag(PlayerTestTags.TITLE).textLayout()
        assertTrue("the title should wrap to at most two lines", title.lineCount <= 2)
        assertTrue("the title should be ellipsized", title.hasVisualOverflow)

        val author = rule.onNodeWithTag(PlayerTestTags.AUTHOR).textLayout()
        assertEquals(1, author.lineCount)

        val root = rule.boundsOf(PlayerTestTags.ROOT)
        assertTrue("the title should stay inside the window", rule.boundsOf(PlayerTestTags.TITLE).right <= root.right)
    }

    // P-SCR-7, P-A11Y-1: the chapter row shows its title and opens the chapter list
    @Test
    fun chapterRowShowsTitleAndOpensChapters() {
        val actions = RecordedActions()
        rule.setPlayer(fullUiState(chapterTitle = "004"), actions = actions.actions())

        // The clickable row merges its children, so the title's own tag lives in the unmerged tree.
        rule.onNodeWithTag(PlayerTestTags.CHAPTER_TITLE, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("004").assertIsDisplayed()

        val root = rule.boundsOf(PlayerTestTags.ROOT)
        val chapter = rule.onNodeWithTag(PlayerTestTags.CHAPTER_TITLE, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the chapter title should be left-aligned", chapter.left < root.width / 4f)

        val label = rule.onNodeWithTag(PlayerTestTags.CHAPTER_ROW).fetchSemanticsNode()
            .config[SemanticsActions.OnClick].label
        assertEquals("Open chapters", label)

        rule.onNodeWithTag(PlayerTestTags.CHAPTER_ROW).performClick()
        rule.waitForIdle()
        assertEquals(1, actions.chapters)
    }

    // P-SCR-7, P-SCR-AC5: the row needs the action, and chapters
    @Test
    fun chapterRowHiddenWithoutActionOrChapters() {
        val actions = mutableStateOf(PlayerActions())
        val state = mutableStateOf(fullUiState(hasChapters = true))
        rule.setPlayer(state, actions = actions)

        rule.onAllNodesWithTag(PlayerTestTags.CHAPTER_ROW).assertCountEquals(0)
        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).assertIsDisplayed()
        rule.onNodeWithTag(PlayerTestTags.TRANSPORT).assertIsDisplayed()
        rule.onNodeWithTag(PlayerTestTags.TOOL_ROW).assertIsDisplayed()

        rule.runOnIdle { actions.value = PlayerActions(onOpenChapters = {}) }
        rule.waitForIdle()
        rule.onNodeWithTag(PlayerTestTags.CHAPTER_ROW).assertIsDisplayed()

        rule.runOnIdle { state.value = state.value.copy(hasChapters = false) }
        rule.waitForIdle()
        rule.onAllNodesWithTag(PlayerTestTags.CHAPTER_ROW).assertCountEquals(0)
        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).assertIsDisplayed()
        rule.onNodeWithTag(PlayerTestTags.TRANSPORT).assertIsDisplayed()
        rule.onNodeWithTag(PlayerTestTags.TOOL_ROW).assertIsDisplayed()
    }

    // R9: a very long chapter title stays on one line inside the window
    @Test
    fun longChapterTitleStaysOnOneLine() {
        val actions = RecordedActions()
        rule.setPlayer(fullUiState(chapterTitle = LONG_CHAPTER_TITLE), actions = actions.actions())

        val chapter = rule.onNodeWithTag(PlayerTestTags.CHAPTER_TITLE, useUnmergedTree = true).textLayout()
        assertEquals(1, chapter.lineCount)
        assertTrue("the long chapter title should be ellipsized", chapter.hasVisualOverflow)

        val root = rule.boundsOf(PlayerTestTags.ROOT)
        assertTrue("the chapter row should stay inside the window", rule.boundsOf(PlayerTestTags.CHAPTER_ROW).right <= root.right + 1f)
    }

    // R15, P-SCR-12: the overlays appear one at a time, over the cover
    @Test
    fun preparingAndBufferingOverlaysSitOverTheCover() {
        val state = mutableStateOf(fullUiState(isPreparing = true, isBuffering = false))
        rule.setPlayer(state)

        val cover = rule.boundsOf(PlayerTestTags.COVER)
        rule.onNodeWithText("Preparing audio").assertIsDisplayed()
        assertTrue("the preparing label should be over the cover", isWithin(rule.onNodeWithText("Preparing audio").fetchSemanticsNode().boundsInRoot, cover))
        rule.onAllNodesWithText("Buffering").assertCountEquals(0)

        rule.runOnIdle { state.value = state.value.copy(isPreparing = false, isBuffering = true) }
        rule.waitForIdle()
        rule.onNodeWithText("Buffering").assertIsDisplayed()
        rule.onAllNodesWithText("Preparing audio").assertCountEquals(0)

        rule.runOnIdle { state.value = state.value.copy(isBuffering = false) }
        rule.waitForIdle()
        rule.onAllNodesWithText("Buffering").assertCountEquals(0)
        rule.onAllNodesWithText("Preparing audio").assertCountEquals(0)
    }

    // R16: the banner sits above the cover, below the top bar
    @Test
    fun errorBannerSitsAboveTheCoverBelowTheTopBar() {
        val error = PlaybackError.StoppedAfterRetries
        rule.setPlayer(
            fullUiState(error = error),
            banner = { PlayerErrorBanner(error = error, onRetry = {}) },
        )

        val banner = rule.boundsOf(PlayerTestTags.ERROR_BANNER)
        assertTrue("the banner should sit above the cover", banner.bottom <= rule.boundsOf(PlayerTestTags.COVER).top + 1f)
        assertTrue("the banner should sit below the top bar", banner.top >= rule.boundsOf(PlayerTestTags.TOP_BAR).bottom - 1f)
        rule.onNodeWithText("Retry").assertIsDisplayed()
    }
}
