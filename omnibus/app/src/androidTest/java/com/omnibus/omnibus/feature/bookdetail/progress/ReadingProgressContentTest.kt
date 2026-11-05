package com.omnibus.omnibus.feature.bookdetail.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
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

/** How the block looks in each state is covered by ReadingProgressScreenshotTest. */
class ReadingProgressContentTest {

    @get:Rule
    val rule = createComposeRule()

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
}
