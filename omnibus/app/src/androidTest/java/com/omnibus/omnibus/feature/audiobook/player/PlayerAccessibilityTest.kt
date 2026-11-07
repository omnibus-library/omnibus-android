package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasContentDescription
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerAccessibilityTest {

    @get:Rule
    val rule = createComposeRule()

    // R18, P-A11Y-1: every control has a spoken label
    @Test
    fun everyControlHasASpokenLabel() {
        rule.setPlayer(fullUiState(hasEbook = true), actions = RecordedActions().actions())

        listOf(
            "Close player",
            "Previous chapter",
            "Back 15 seconds",
            "Pause",
            "Forward 30 seconds",
            "Next chapter",
            "Open the ebook",
            "Bookmarks",
            "Chapter position",
        ).forEach { rule.onNodeWithContentDescription(it).assertIsDisplayed() }
        listOf("Speed", "Car Mode", "Sleep", "Bookmark").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    // R18, P-A11Y-2, AC7: the scrubber reads its span time and adjusts through SetProgress
    @Test
    fun scrubberIsAdjustableWithSpanTimeAsState() {
        var seeked = Double.NaN
        rule.setPlayer(fullUiState(spanPositionSeconds = 100.0), onSeek = { seeked = it })

        val scrubber = rule.onNodeWithTag(PlayerTestTags.SCRUBBER)
        scrubber.assert(hasContentDescription("Chapter position"))
        scrubber.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1:40"))
        val range = scrubber.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(100f, range.current, 0.01f)
        assertEquals(600f, range.range.endInclusive, 0.01f)

        scrubber.performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        rule.waitForIdle()
        assertEquals(1030.0, seeked, 0.01)

        scrubber.performSemanticsAction(SemanticsActions.SetProgress) { it(9999f) }
        rule.waitForIdle()
        assertEquals(1600.0, seeked, 0.01)
    }
}
