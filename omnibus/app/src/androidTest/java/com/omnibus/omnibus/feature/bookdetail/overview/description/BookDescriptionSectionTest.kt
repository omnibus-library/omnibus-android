package com.omnibus.omnibus.feature.bookdetail.overview.description

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.feature.bookdetail.textLayout
import com.omnibus.omnibus.feature.bookdetail.tabs.assertExists
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The description section: toggle, saved state and width. BookDescriptionScreenshotTest covers how it looks. */
class BookDescriptionSectionTest {

    @get:Rule
    val rule = createComposeRule()

    // Wraps well past three lines at any width, yet stays inside the test window once expanded.
    private val longText = List(12) { "lorem ipsum dolor sit amet" }.joinToString(" ")

    private val tags = BookDescriptionTestTags

    private fun ComposeContentTestRule.setSection(description: String?) {
        setContent {
            OmnibusTheme(darkTheme = true) {
                BookDescriptionSection.Content(
                    testSectionContext(book = testDetailBook().copy(description = description)),
                    Modifier.fillMaxWidth(),
                )
            }
        }
        waitForIdle()
    }

    // TC-73
    @Test
    fun issue94_tc73_toggleExpandsThenCollapses() {
        rule.setSection(longText)

        rule.onNodeWithTag(tags.DESCRIPTION_TOGGLE).performClick()
        rule.waitForIdle()
        assertTrue(rule.onNodeWithTag(tags.DESCRIPTION_TEXT).textLayout().lineCount > 3)

        rule.onNodeWithTag(tags.DESCRIPTION_TOGGLE).performClick()
        rule.waitForIdle()
        assertEquals(3, rule.onNodeWithTag(tags.DESCRIPTION_TEXT).textLayout().lineCount)
    }

    // TC-76
    @Test
    fun issue94_tc76_toggleIsAButton() {
        rule.setSection(longText)

        val toggle = rule.onNodeWithTag(tags.DESCRIPTION_TOGGLE)
        assertEquals(Role.Button, toggle.fetchSemanticsNode().config[SemanticsProperties.Role])
    }

    // TC-77
    @Test
    fun issue94_tc77_expandedStateIsKeptPerBookAcrossRecreation() {
        val book = mutableStateOf(testDetailBook().copy(description = longText))
        val tester = StateRestorationTester(rule)
        tester.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDescriptionSection.Content(testSectionContext(book = book.value), Modifier.fillMaxWidth())
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(tags.DESCRIPTION_TOGGLE).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(detailText(R.string.book_detail_tabs_show_less)).assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithText(detailText(R.string.book_detail_tabs_show_less)).assertExists()

        rule.runOnIdle { book.value = testDetailBook(id = 2).copy(description = longText) }
        rule.waitForIdle()
        rule.onNodeWithText(detailText(R.string.book_detail_tabs_show_more)).assertExists()
    }

    // TC-78
    @Test
    fun issue94_tc78_toggleFollowsTheWidthTheTextIsLaidOutAt() {
        val fraction = mutableFloatStateOf(1f)
        val words = "one two three four five six seven eight nine ten eleven twelve"
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth(fraction.floatValue)) {
                    BookDescriptionSection.Content(
                        testSectionContext(book = testDetailBook().copy(description = words)),
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        rule.waitForIdle()

        fun checkStep() {
            rule.waitForIdle()
            val overflow = rule.onNodeWithTag(tags.DESCRIPTION_TEXT).textLayout().hasVisualOverflow
            val toggleShown = rule.onAllNodesWithTag(tags.DESCRIPTION_TOGGLE).fetchSemanticsNodes().isNotEmpty()
            assertEquals("toggle must follow overflow at fraction ${fraction.floatValue}", overflow, toggleShown)
        }

        checkStep()
        assertFalse(rule.onNodeWithTag(tags.DESCRIPTION_TEXT).textLayout().hasVisualOverflow)
        rule.onAllNodesWithTag(tags.DESCRIPTION_TOGGLE).assertCountEquals(0)

        rule.runOnIdle { fraction.floatValue = 0.15f }
        checkStep()
        assertTrue(rule.onNodeWithTag(tags.DESCRIPTION_TEXT).textLayout().hasVisualOverflow)
        rule.onNodeWithTag(tags.DESCRIPTION_TOGGLE).assertExists()

        rule.runOnIdle { fraction.floatValue = 1f }
        checkStep()
        rule.onAllNodesWithTag(tags.DESCRIPTION_TOGGLE).assertCountEquals(0)
    }
}
