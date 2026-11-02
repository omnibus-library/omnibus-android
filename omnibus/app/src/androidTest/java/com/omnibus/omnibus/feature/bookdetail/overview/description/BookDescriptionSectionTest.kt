package com.omnibus.omnibus.feature.bookdetail.overview.description

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.feature.bookdetail.textLayout
import com.omnibus.omnibus.feature.bookdetail.tabs.tabNode
import com.omnibus.omnibus.feature.bookdetail.tabs.BookDetailTabsTestTags
import com.omnibus.omnibus.feature.bookdetail.tabs.assertExists
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab

/** The description section in the Overview panel: stripped text, clamp, toggle, width and motion. */
class BookDescriptionSectionTest {

    @get:Rule
    val rule = createComposeRule()

    // Wraps well past three lines at any width, yet stays inside the test window once expanded.
    private val longText = List(12) { "lorem ipsum dolor sit amet" }.joinToString(" ")

    private val tags = BookDescriptionTestTags

    private fun ComposeContentTestRule.dp(value: androidx.compose.ui.unit.Dp): Float = with(density) { value.toPx() }

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

    // TC-71
    @Test
    fun issue94_tc71_descriptionShowsTheStrippedTextInTheBodyStyle() {
        rule.setSection("<p>First paragraph &amp; more.</p><p>Second<br>line</p>")
        rule.onNodeWithTag(tags.DESCRIPTION_TEXT).assertTextEquals("First paragraph & more.\n\nSecond\nline")
        val style = rule.onNodeWithTag(tags.DESCRIPTION_TEXT).textLayout().layoutInput.style
        assertEquals(16f, style.fontSize.value, 0.01f)
        assertEquals(24f, style.lineHeight.value, 0.01f)
        assertEquals(FontWeight.Normal, style.fontWeight)
        assertEquals(OmnibusPalette.Atrium.ink1, style.color)
    }

    // TC-72
    @Test
    fun issue94_tc72_longDescriptionIsClampedToThreeLinesWithShowMore() {
        rule.setSection(longText)
        val layout = rule.onNodeWithTag(tags.DESCRIPTION_TEXT).textLayout()
        assertEquals(3, layout.lineCount)
        assertTrue(layout.hasVisualOverflow)
        rule.onNodeWithTag(tags.DESCRIPTION_TOGGLE).assertExists()
        rule.onNodeWithText(detailText(R.string.book_detail_tabs_show_more)).assertExists()
    }

    // TC-73
    @Test
    fun issue94_tc73_showMoreExpandsAndShowLessCollapses() {
        rule.setSection(longText)
        val collapsedHeight = rule.onNodeWithTag(tags.DESCRIPTION).bounds().height

        rule.onNodeWithTag(tags.DESCRIPTION_TOGGLE).performClick()
        rule.waitForIdle()
        val expanded = rule.onNodeWithTag(tags.DESCRIPTION_TEXT).textLayout()
        assertTrue(expanded.lineCount > 3)
        assertFalse(expanded.hasVisualOverflow)
        rule.onNodeWithText(detailText(R.string.book_detail_tabs_show_less)).assertExists()
        val expandedHeight = rule.onNodeWithTag(tags.DESCRIPTION).bounds().height
        assertTrue(expandedHeight > collapsedHeight)

        rule.onNodeWithTag(tags.DESCRIPTION_TOGGLE).performClick()
        rule.waitForIdle()
        assertEquals(3, rule.onNodeWithTag(tags.DESCRIPTION_TEXT).textLayout().lineCount)
        rule.onNodeWithText(detailText(R.string.book_detail_tabs_show_more)).assertExists()
        assertEquals(collapsedHeight, rule.onNodeWithTag(tags.DESCRIPTION).bounds().height, 1f)
    }

    // TC-74
    @Test
    fun issue94_tc74_toggleAppearsOnlyAboveThreeLines() {
        val text = mutableStateOf("One\nTwo\nThree")
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDescriptionSection.Content(
                    testSectionContext(book = testDetailBook().copy(description = text.value)),
                    Modifier.fillMaxWidth(),
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(tags.DESCRIPTION_TEXT).assertTextEquals("One\nTwo\nThree")
        assertEquals(3, rule.onNodeWithTag(tags.DESCRIPTION_TEXT).textLayout().lineCount)
        rule.onAllNodesWithTag(tags.DESCRIPTION_TOGGLE).assertCountEquals(0)

        rule.runOnIdle { text.value = "One\nTwo\nThree\nFour" }
        rule.waitForIdle()
        val layout = rule.onNodeWithTag(tags.DESCRIPTION_TEXT).textLayout()
        assertEquals(3, layout.lineCount)
        assertTrue(layout.hasVisualOverflow)
        rule.onNodeWithTag(tags.DESCRIPTION_TOGGLE).assertExists()
    }

    // TC-75
    @Test
    fun issue94_tc75_shortDescriptionShowsNoToggleAndNoExtraSpace() {
        rule.setSection("A short sentence about the book.")
        rule.onNodeWithTag(tags.DESCRIPTION_TEXT).assertExists()
        rule.onAllNodesWithTag(tags.DESCRIPTION_TOGGLE).assertCountEquals(0)
        assertEquals(
            rule.onNodeWithTag(tags.DESCRIPTION_TEXT).bounds().height,
            rule.onNodeWithTag(tags.DESCRIPTION).bounds().height,
            1f,
        )
    }

    // TC-76
    @Test
    fun issue94_tc76_toggleIsA44DpButtonLabelledByItsText() {
        rule.setSection(longText)
        val toggle = rule.onNodeWithTag(tags.DESCRIPTION_TOGGLE)
        assertEquals(Role.Button, toggle.fetchSemanticsNode().config[SemanticsProperties.Role])
        toggle.assertTextEquals(detailText(R.string.book_detail_tabs_show_more))
        assertTrue(toggle.bounds().height >= rule.dp(44.dp) - 1f)

        val label = rule.onNodeWithText(detailText(R.string.book_detail_tabs_show_more), useUnmergedTree = true)
        assertEquals(15f, label.textLayout().layoutInput.style.fontSize.value, 0.01f)
        assertEquals(FontWeight.SemiBold, label.textLayout().layoutInput.style.fontWeight)
        assertEquals(toggle.bounds().left, label.bounds().left, 1f)
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

    // TC-79
    @Test
    fun issue94_tc79_nullOrBlankDescriptionRendersNothing() {
        val description = mutableStateOf<String?>(null)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.fillMaxWidth().testTag("container")) {
                    BookDescriptionSection.Content(
                        testSectionContext(book = testDetailBook().copy(description = description.value)),
                        Modifier.fillMaxWidth(),
                    )
                    Text("sibling", modifier = Modifier.testTag("sibling"))
                }
            }
        }
        listOf<String?>(null, "", "   ", "<p></p>", "<br>&nbsp;").forEach { value ->
            rule.runOnIdle { description.value = value }
            rule.waitForIdle()
            rule.onAllNodesWithTag(tags.DESCRIPTION).assertCountEquals(0)
            rule.onAllNodesWithTag(tags.DESCRIPTION_TOGGLE).assertCountEquals(0)
            assertEquals(
                rule.onNodeWithTag("container").bounds().top,
                rule.onNodeWithTag("sibling").bounds().top,
                1f,
            )
        }
    }

    // TC-80 (reduced motion)
    @Test
    fun issue94_tc80_expansionIsImmediateUnderReducedMotion() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDescription(
                    testSectionContext(book = testDetailBook().copy(description = longText)),
                    Modifier.fillMaxWidth(),
                    reducedMotion = true,
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(tags.DESCRIPTION_TOGGLE).performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        val immediate = rule.onNodeWithTag(tags.DESCRIPTION).bounds().height
        rule.mainClock.advanceTimeBy(2000)
        assertEquals(rule.onNodeWithTag(tags.DESCRIPTION).bounds().height, immediate, 1f)
    }

    // TC-80 (motion on)
    @Test
    fun issue94_tc80_expansionAnimatesWithMotionOn() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDescription(
                    testSectionContext(book = testDetailBook().copy(description = longText)),
                    Modifier.fillMaxWidth(),
                    reducedMotion = false,
                )
            }
        }
        rule.waitForIdle()
        val collapsed = rule.onNodeWithTag(tags.DESCRIPTION).bounds().height
        rule.onNodeWithTag(tags.DESCRIPTION_TOGGLE).performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(60)
        val mid = rule.onNodeWithTag(tags.DESCRIPTION).bounds().height
        rule.mainClock.advanceTimeBy(2000)
        val final = rule.onNodeWithTag(tags.DESCRIPTION).bounds().height
        assertTrue("mid $mid between $collapsed and $final", mid > collapsed + 0.5f && mid < final - 0.5f)
        rule.mainClock.advanceTimeBy(2000)
        assertEquals(final, rule.onNodeWithTag(tags.DESCRIPTION).bounds().height, 1f)
    }

    // TC-81
    @Test
    fun issue94_tc81_descriptionIsTheFirstElementOfTheOverviewPanel() {
        rule.setDetailScreen(
            BookDetailUIState(book = testDetailBook().copy(description = longText), isLoading = false),
            slots = BookDetailSlots(overviewSections = listOf(BookDescriptionSection)),
        )
        rule.waitForIdle()
        val panelTop = rule.onNodeWithTag(BookDetailTabsTestTags.PANEL, useUnmergedTree = true).bounds().top
        assertEquals(panelTop + rule.dp(46.dp), rule.onNodeWithTag(tags.DESCRIPTION).bounds().top, 1f)

        rule.tabNode(BookDetailTab.Reviews).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag(tags.DESCRIPTION).assertCountEquals(0)

        rule.tabNode(BookDetailTab.Overview).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(tags.DESCRIPTION).assertExists()
    }
}
