package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tab strip, ribbon and panel with the host rendered alone. */
class BookTabStripTest {

    @get:Rule
    val rule = createComposeRule()

    private fun fourFakes() = BookDetailSlots(
        overviewSections = listOf(fakeSection("fake-overview")),
        reviewsSections = listOf(fakeSection("fake-reviews")),
        highlightsSections = listOf(fakeSection("fake-highlights")),
        filesSections = listOf(fakeSection("fake-files")),
    )

    // TC-31
    @Test
    fun issue94_tc31_tappingATabSwapsThePanel() {
        rule.setTabHost(fourFakes())
        listOf(BookDetailTab.Reviews, BookDetailTab.Files, BookDetailTab.Overview).forEach { target ->
            rule.tapTab(target)
            BookDetailTab.entries.forEach { tab ->
                val tag = "fake-${tab.name.lowercase()}"
                rule.onAllNodesWithTag(tag).assertCountEquals(if (tab == target) 1 else 0)
            }
            rule.tabNode(target).assertIsSelected()
        }
    }

    // TC-36
    @Test
    fun issue94_tc36_tabsExposeRoleTabWithSelectedState() {
        rule.setTabHost(fourFakes())
        BookDetailTab.entries.forEach { tab ->
            val node = rule.tabNode(tab).fetchSemanticsNode()
            assertEquals(Role.Tab, node.config[SemanticsProperties.Role])
        }
        rule.tabNode(BookDetailTab.Overview).assertIsSelected()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertCountEquals(1)

        rule.tabNode(BookDetailTab.Reviews).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Reviews).assertIsSelected()
        rule.onAllNodesWithTag("fake-reviews").assertCountEquals(1)
    }

    // TC-37
    @Test
    fun issue94_tc37_ribbonIsHiddenFromAccessibilityAndTakesNoTouches() {
        rule.setTabHost(fourFakes())
        val ribbon = rule.ribbonNode()
        val config = ribbon.fetchSemanticsNode().config
        assertTrue(config.contains(SemanticsProperties.HideFromAccessibility))
        assertFalse(config.contains(SemanticsActions.OnClick))

        val centre = ribbon.bounds().center
        rule.onRoot().performTouchInput { click(centre) }
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Overview).assertIsSelected()
    }

    // TC-40
    @Test
    fun issue94_tc40_panelRendersItsSlotListInOrderWithTheSharedContext() {
        val seen = mutableListOf<BookDetailSectionContext>()
        fun recorder(tag: String) = com.omnibus.omnibus.feature.bookdetail.BookDetailSection { ctx, modifier ->
            SideEffect { seen += ctx }
            androidx.compose.material3.Text(tag, modifier = modifier.testTag(tag))
        }
        val handle = rule.setTabHost(BookDetailSlots(overviewSections = listOf(recorder("first"), recorder("second"))))
        val first = rule.onNodeWithTag("first").bounds()
        val second = rule.onNodeWithTag("second").bounds()
        assertTrue(first.top < second.top)
        assertTrue(seen.isNotEmpty() && seen.all { it === handle.context })
    }
}
