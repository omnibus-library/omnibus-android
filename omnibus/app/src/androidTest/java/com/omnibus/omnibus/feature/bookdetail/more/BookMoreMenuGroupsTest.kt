package com.omnibus.omnibus.feature.bookdetail.more

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookMoreMenuGroupsTest {

    @get:Rule
    val rule = createComposeRule()

    /** The menu rule nodes that were placed, not the surplus ones composed but left unplaced. */
    private fun placedRules(): Int = rule.onAllNodesWithTag(BookMoreTestTags.MENU_RULE, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .count { it.layoutInfo.isPlaced }

    /** Sets a menu that is always open, with [entries] read at each recomposition. */
    private fun setMenu(entries: () -> List<BookMoreMenuEntry>) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().testTag(MORE_HOST)) {
                    BookMoreMenu(
                        expanded = true,
                        onDismiss = {},
                        context = testSectionContext(),
                        entries = entries(),
                    )
                }
            }
        }
    }

    private fun groups() = rule.onAllNodesWithTag(BookMoreTestTags.GROUP, useUnmergedTree = true)
        .fetchSemanticsNodes()

    // TC-10
    @Test
    fun issue116_tc10_entryEmittingNothingLeavesOneRuleBetweenItsNeighbours() {
        setMenu {
            listOf(TestEntry("Alpha row"), TestEntry(label = null), TestEntry("Gamma row"))
        }
        rule.onNodeWithTag(BookMoreTestTags.MENU).assertIsDisplayed()

        val groups = groups()
        assertEquals(3, groups.size)
        assertEquals(1, placedRules())

        val alpha = groups[0].boundsInRoot
        val gamma = groups[2].boundsInRoot
        val placed = rule.onAllNodesWithTag(BookMoreTestTags.MENU_RULE, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .single { it.layoutInfo.isPlaced }
            .boundsInRoot
        val ruleHeight = with(rule.density) { 8.dp.toPx() }

        assertEquals("rule height", ruleHeight, gamma.top - alpha.bottom, 1f)
        assertEquals("the rule fills the gap", ruleHeight, placed.height, 1f)
        assertTrue(
            "the rule sits between the entries, not above the first",
            placed.top >= alpha.bottom - 1f && placed.bottom <= gamma.top + 1f,
        )
    }

    // TC-11
    @Test
    fun issue116_tc11_rulesAppearOnlyBetweenEntriesThatEmittedRows() {
        var entries by mutableStateOf(
            listOf<BookMoreMenuEntry>(TestEntry("Alpha row"), TestEntry("Beta row"), TestEntry("Gamma row")),
        )
        setMenu { entries }
        rule.onNodeWithTag(BookMoreTestTags.MENU).assertIsDisplayed()
        assertEquals(2, placedRules())

        entries = listOf(TestEntry("Alpha row"), TestEntry(null), TestEntry(null))
        rule.waitForIdle()
        assertEquals(0, placedRules())

        entries = listOf(TestEntry(null), TestEntry(null), TestEntry("Gamma row"))
        rule.waitForIdle()
        assertEquals(0, placedRules())

        entries = listOf(TestEntry(null), TestEntry(null), TestEntry(null))
        rule.waitForIdle()
        assertEquals(0, placedRules())
    }

    // TC-12
    @Test
    fun issue116_tc12_menuMeasuresWithIntrinsicSizeMaxWithoutCrashing() {
        setMenu {
            listOf(
                TestEntry("A label long enough to be the widest row in this menu"),
                TestEntry("Short"),
            )
        }
        rule.onNodeWithTag(BookMoreTestTags.MENU).assertIsDisplayed()

        val widths = groups().map { it.boundsInRoot.width }
        assertEquals(2, widths.size)
        assertEquals("every group is as wide as the menu content", widths[0], widths[1], 1f)
    }

    // TC-68
    @Test
    fun issue116_tc68_emptyRegistryOpensAnEmptyMenuSafely() {
        rule.setMoreButton(emptyList())

        rule.onNodeWithTag(BookMoreTestTags.MORE_BUTTON).performClick()
        rule.waitForIdle()
        assertEquals(0, placedRules())

        rule.pressBackKey()
        rule.onNodeWithTag(BookMoreTestTags.MORE_BUTTON).performClick()
        rule.waitForIdle()
        assertEquals(0, placedRules())
    }
}
