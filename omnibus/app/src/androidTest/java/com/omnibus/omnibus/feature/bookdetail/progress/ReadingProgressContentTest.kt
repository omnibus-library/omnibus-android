package com.omnibus.omnibus.feature.bookdetail.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A bar of [fraction], with equal segments unless [weights] say otherwise. */
private fun bar(
    fraction: Float,
    percent: Int,
    isAudio: Boolean = false,
    weights: List<Float> = listOf(.25f, .25f, .25f, .25f),
    flag: FlagLabel = FlagLabel.Chapter(6),
    tail: LineTail? = LineTail.LeftAtPace(6780),
) = ProgressDisplay.Bar(fraction, percent, isAudio, barSegments(weights, fraction), flag, tail)

class ReadingProgressContentTest {

    @get:Rule
    val rule = createComposeRule()

    private fun ComposeContentTestRule.setProgress(
        state: ReadingProgressUIState,
    ) = setContent {
        OmnibusTheme(darkTheme = true) {
            ReadingProgressContent(state, testSectionContext().accent, Modifier.fillMaxWidth())
        }
    }

    /** A part of the bar block, which the merged tree does not expose. */
    private fun part(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)

    // TC-81
    @Test
    fun tc81_barFlagAndLineAreShown() {
        rule.setProgress(ReadingProgressUIState(bar(.62f, 62)))

        rule.onNodeWithTag(ReadingProgressTestTags.BAR).assertIsDisplayed()
        part(ReadingProgressTestTags.FLAG).assertIsDisplayed()
        part(ReadingProgressTestTags.STEM).assertIsDisplayed()
        part(ReadingProgressTestTags.TRACK).assertIsDisplayed()
        rule.onNodeWithTag(ReadingProgressTestTags.LINE).assertTextEquals("62% · about 1 h 53 m left at your pace")
        rule.onNodeWithText("Ch. 6", useUnmergedTree = true).assertIsDisplayed()

        val bar = rule.onNodeWithTag(ReadingProgressTestTags.BAR).bounds()
        val flag = part(ReadingProgressTestTags.FLAG).bounds()
        val track = part(ReadingProgressTestTags.TRACK).bounds()
        val stem = part(ReadingProgressTestTags.STEM).bounds()
        assertTrue("the pill is horizontally inside the bar", flag.left >= bar.left && flag.right <= bar.right)
        assertTrue("the pill sits above the track", flag.bottom <= track.top)
        assertTrue("the stem reaches into the track", stem.bottom > track.top)
    }

    // TC-86
    @Test
    fun tc86_barIsOneProgressIndicator() {
        var state by mutableStateOf(ReadingProgressUIState(bar(.62f, 62)))
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ReadingProgressContent(state, testSectionContext().accent, Modifier.fillMaxWidth())
            }
        }

        rule.onNodeWithTag(ReadingProgressTestTags.BAR)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.62f, 0f..1f)))
            .assertContentDescriptionEquals("Reading progress")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "62 percent"))
        rule.onNodeWithTag(ReadingProgressTestTags.FLAG).assertDoesNotExist()

        rule.runOnIdle { state = ReadingProgressUIState(bar(.52f, 52, isAudio = true, flag = FlagLabel.Elapsed(9060), tail = null)) }
        rule.onNodeWithTag(ReadingProgressTestTags.BAR).assertContentDescriptionEquals("Listening progress")
    }

    // TC-87
    @Test
    fun tc87_audioFlagAndLine() {
        rule.setProgress(
            ReadingProgressUIState(
                bar(.52f, 52, isAudio = true, flag = FlagLabel.Elapsed(9060), tail = LineTail.LeftOf(6540, 17340)),
            ),
        )

        rule.onNodeWithText("2 h 31 m", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(ReadingProgressTestTags.LINE).assertTextEquals("52% · 1 h 49 m left of 4 h 49 m")
        rule.onAllNodesWithText("Ch.", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    // TC-88
    @Test
    fun tc88_inProgressWithoutABar() {
        rule.setProgress(ReadingProgressUIState(ProgressDisplay.InProgress))

        rule.onNodeWithText("In progress").assertIsDisplayed()
        rule.onNodeWithTag(ReadingProgressTestTags.BAR).assertDoesNotExist()
        rule.onNodeWithTag(ReadingProgressTestTags.FLAG).assertDoesNotExist()
        rule.onNodeWithTag(ReadingProgressTestTags.LINE).assertDoesNotExist()
    }

    // TC-89
    @Test
    fun tc89_nothingForHidden() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.testTag("host")) {
                    ReadingProgressContent(ReadingProgressUIState(), testSectionContext().accent, Modifier.fillMaxWidth())
                }
            }
        }

        rule.onNodeWithTag(ReadingProgressTestTags.BAR).assertDoesNotExist()
        rule.onNodeWithTag(ReadingProgressTestTags.IN_PROGRESS).assertDoesNotExist()
        rule.onNodeWithTag(ReadingProgressTestTags.OFFLINE_NOTE).assertDoesNotExist()
        rule.onNodeWithTag("host").assertHeightIsEqualTo(0.dp)
    }

    // TC-90
    @Test
    fun tc90_offlineNoteShowsWithAndWithoutABar() {
        var state by mutableStateOf(ReadingProgressUIState(bar(.62f, 62), showOfflineNote = true))
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ReadingProgressContent(state, testSectionContext().accent, Modifier.fillMaxWidth())
            }
        }

        rule.onNodeWithTag(ReadingProgressTestTags.OFFLINE_NOTE).assertTextEquals("Offline. This book isn't downloaded.")
        val line = rule.onNodeWithTag(ReadingProgressTestTags.LINE).bounds()
        val note = rule.onNodeWithTag(ReadingProgressTestTags.OFFLINE_NOTE).bounds()
        assertTrue("the note sits below the line", note.top >= line.bottom)

        rule.runOnIdle { state = ReadingProgressUIState(ProgressDisplay.InProgress, showOfflineNote = true) }
        rule.onNodeWithTag(ReadingProgressTestTags.OFFLINE_NOTE).assertTextEquals("Offline. This book isn't downloaded.")

        rule.runOnIdle { state = ReadingProgressUIState(ProgressDisplay.Hidden, showOfflineNote = true) }
        rule.onNodeWithTag(ReadingProgressTestTags.OFFLINE_NOTE).assertTextEquals("Offline. This book isn't downloaded.")

        rule.runOnIdle { state = ReadingProgressUIState(bar(.62f, 62), showOfflineNote = false) }
        rule.onNodeWithTag(ReadingProgressTestTags.OFFLINE_NOTE).assertDoesNotExist()
    }
}
