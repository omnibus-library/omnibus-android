package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
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
import org.junit.Assert.assertTrue
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

    // TC-50
    @Test
    fun issue94_tc50_reducedMotionShowsTheNewPanelAtRestImmediately() {
        rule.mainClock.autoAdvance = false
        rule.setTabHost(fourFakes(), reducedMotion = true)
        val rest = rule.hostLeftPlusInset()
        rule.tabNode(BookDetailTab.Reviews).performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        assertEquals(rest, rule.fakeLeft("fake-reviews"), 1f)
        rule.onAllNodesWithTag("fake-overview").assertCountEquals(0)
    }

    // TC-51
    @Test
    fun issue94_tc51_panelEntersFromTheRightWhenMovingToAHigherTab() {
        rule.mainClock.autoAdvance = false
        rule.setTabHost(fourFakes(), reducedMotion = false)
        val rest = rule.hostLeftPlusInset()
        rule.tabNode(BookDetailTab.Highlights).performClick()
        rule.mainClock.advanceTimeBy(50)
        val offset = rule.fakeLeft("fake-highlights") - rest
        assertTrue("offset $offset should be to the right of rest", offset > 0.5f)
        assertTrue("offset $offset should be within 40 dp", offset < rule.dp(40.dp))

        rule.mainClock.advanceTimeBy(2000)
        assertEquals(rest, rule.fakeLeft("fake-highlights"), 1f)
    }

    // TC-52
    @Test
    fun issue94_tc52_panelEntersFromTheLeftWhenMovingToALowerTab() {
        rule.mainClock.autoAdvance = false
        rule.setTabHost(fourFakes(), reducedMotion = false)
        val rest = rule.hostLeftPlusInset()
        rule.tapTab(BookDetailTab.Files)
        rule.mainClock.advanceTimeBy(2000)

        rule.tabNode(BookDetailTab.Reviews).performClick()
        rule.mainClock.advanceTimeBy(50)
        val offset = rule.fakeLeft("fake-reviews") - rest
        assertTrue("offset $offset should be to the left of rest", offset < -0.5f)

        rule.mainClock.advanceTimeBy(2000)
        assertEquals(rest, rule.fakeLeft("fake-reviews"), 1f)
    }

    // TC-53
    @Test
    fun issue94_tc53_firstPanelOnOpenOrRestoreDoesNotAnimate() {
        rule.mainClock.autoAdvance = false
        val tester = StateRestorationTester(rule)
        val handle = TabHostHandle()
        tester.setContent {
            OmnibusTheme(darkTheme = true) {
                TabHostUnderTest(handle, fourFakes(), reducedMotion = false, book = testDetailBook(), accent = null)
            }
        }
        rule.waitForIdle()
        val rest = rule.hostLeftPlusInset()
        assertEquals(rest, rule.fakeLeft("fake-overview"), 1f)

        rule.tabNode(BookDetailTab.Highlights).performClick()
        rule.mainClock.advanceTimeBy(2000)
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Highlights).assertIsSelected()
        rule.onNodeWithTag("fake-highlights").assertExists()
        assertEquals(rest, rule.fakeLeft("fake-highlights"), 1f)
    }

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

    // TC-56
    @Test
    fun issue94_tc56_ribbonSlidesToTheNewTabWithMotionOn() {
        rule.mainClock.autoAdvance = false
        rule.setTabHost(fourFakes(), reducedMotion = false)
        rule.tabNode(BookDetailTab.Files).performClick()
        rule.mainClock.advanceTimeBy(100)
        val mid = rule.ribbonNode().bounds().center.x
        val overview = rule.tabNode(BookDetailTab.Overview).bounds().center.x
        val files = rule.tabNode(BookDetailTab.Files).bounds().center.x
        assertTrue("mid $mid between $overview and $files", mid > overview + 0.5f && mid < files - 0.5f)

        rule.mainClock.advanceTimeBy(2000)
        assertEquals(files, rule.ribbonNode().bounds().center.x, 1f)
    }

    // TC-57
    @Test
    fun issue94_tc57_ribbonSwingsOnceWithMotionOn() {
        rule.mainClock.autoAdvance = false
        rule.setTabHost(fourFakes(), reducedMotion = false)
        rule.tabNode(BookDetailTab.Reviews).performClick()
        rule.waitForIdle()
        val reviewsCentre = rule.tabNode(BookDetailTab.Reviews).bounds().center.x
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(200)
        val swinging = rule.ribbonNode().bounds().width
        assertTrue("ribbon width $swinging should be wider while swinging", swinging > rule.dp(22.dp))

        rule.mainClock.advanceTimeBy(2000)
        val settled = rule.ribbonNode().bounds()
        assertEquals(rule.dp(20.dp), settled.width, 1f)
        assertEquals(reviewsCentre, settled.center.x, 1f)
    }

    // TC-57 (reduced motion)
    @Test
    fun issue94_tc57_ribbonIsStillUnderReducedMotion() {
        rule.mainClock.autoAdvance = false
        rule.setTabHost(fourFakes(), reducedMotion = true)
        rule.tabNode(BookDetailTab.Reviews).performClick()
        rule.waitForIdle()
        repeat(25) {
            rule.mainClock.advanceTimeBy(16)
            assertEquals(rule.dp(20.dp), rule.ribbonNode().bounds().width, 1f)
        }
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

    // TC-59
    @Test
    fun issue94_tc59_washPoseGlidesBetweenTabsWithMotionOn() {
        rule.mainClock.autoAdvance = false
        val tab = mutableStateOf(BookDetailTab.Overview)
        lateinit var pose: BookStageWashPose
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                pose = rememberTabWashPose(selected = { tab.value }, reducedMotion = false)
            }
        }
        rule.waitForIdle()
        rule.runOnIdle { tab.value = BookDetailTab.Files }
        rule.mainClock.advanceTimeBy(100)
        val mid = pose.pan()
        assertTrue("mid pan $mid", mid < 0.165f - 1e-3f && mid > -0.165f + 1e-3f)

        rule.mainClock.advanceTimeBy(3000)
        assertEquals(-0.165f, pose.pan(), 1e-3f)
        assertEquals(0.1f, pose.warmth(), 1e-3f)
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
