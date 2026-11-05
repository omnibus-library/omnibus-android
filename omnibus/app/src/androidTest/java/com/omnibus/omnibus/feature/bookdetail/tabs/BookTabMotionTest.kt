package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.stage.BookStageWashPose
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Panel entrance, ribbon motion and the wash pose, with motion on and off. */
class BookTabMotionTest {

    @get:Rule
    val rule = createComposeRule()

    private fun ComposeContentTestRule.dp(value: Dp): Float = with(density) { value.toPx() }

    private fun ComposeContentTestRule.hostLeftPlusInset(): Float =
        onNodeWithTag(BookDetailTestTags.TAB_AREA).bounds().left + dp(16.dp)

    private fun ComposeContentTestRule.fakeLeft(tag: String): Float = onNodeWithTag(tag).bounds().left

    private fun fourFakes() = BookDetailSlots(
        overviewSections = listOf(fakeSection("fake-overview")),
        reviewsSections = listOf(fakeSection("fake-reviews")),
        highlightsSections = listOf(fakeSection("fake-highlights")),
        filesSections = listOf(fakeSection("fake-files")),
    )

    // TC-54
    @Test
    fun issue94_tc54_rapidTabChangesLeaveOneSettledPanel() {
        rule.mainClock.autoAdvance = false
        rule.setTabHost(fourFakes(), reducedMotion = false)
        val rest = rule.hostLeftPlusInset()
        rule.tabNode(BookDetailTab.Reviews).performClick()
        rule.tabNode(BookDetailTab.Highlights).performClick()
        rule.mainClock.advanceTimeBy(2000)
        rule.waitForIdle()
        rule.onAllNodesWithTag("fake-reviews").assertCountEquals(0)
        rule.onAllNodesWithTag("fake-overview").assertCountEquals(0)
        rule.onAllNodesWithTag("fake-highlights").assertCountEquals(1)
        assertEquals(rest, rule.fakeLeft("fake-highlights"), 1f)
    }

    // TC-58
    @Test
    fun issue94_tc58_washPoseSnapsToTheTabValuesUnderReducedMotion() {
        val tab = mutableStateOf(BookDetailTab.Overview)
        lateinit var pose: BookStageWashPose
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                pose = rememberTabWashPose(selected = { tab.value }, reducedMotion = true)
            }
        }
        rule.waitForIdle()
        BookDetailTab.entries.forEach { next ->
            rule.runOnIdle { tab.value = next }
            rule.waitForIdle()
            assertEquals(tabWashPan(next), pose.pan(), 1e-6f)
            assertEquals(tabWashWarmth(next), pose.warmth(), 1e-6f)
        }
    }

    // TC-60
    @Test
    fun issue94_tc60_tabChangeDoesNotRecomposeTheStage() {
        var stageCompositions = 0
        val slots = fourFakes().copy(
            stageStatusStart = listOf(BookDetailSection { _, modifier ->
                SideEffect { stageCompositions++ }
                Box(modifier.testTag("stage-counter"))
            }),
        )
        rule.setDetailScreen(
            com.omnibus.omnibus.feature.bookdetail.BookDetailUIState(book = testDetailBook(), isLoading = false),
            slots = slots,
        )
        rule.waitForIdle()
        val before = stageCompositions
        listOf(BookDetailTab.Reviews, BookDetailTab.Highlights, BookDetailTab.Files, BookDetailTab.Overview).forEach {
            rule.tabNode(it).performClick()
            rule.waitForIdle()
        }
        assertEquals(before, stageCompositions)
        rule.onNodeWithTag(BookDetailTestTags.STAGE).assertExists()
        rule.onNodeWithTag(BookDetailTestTags.WASH).assertExists()
    }
}
