package com.omnibus.omnibus.feature.bookdetail.more

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
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
}
