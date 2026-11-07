package com.omnibus.omnibus.feature.bookdetail.journal

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.omnibus.omnibus.feature.bookdetail.scrollTo
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun cards(count: Int): List<JournalCardModel> =
    (1..count).map { testCard(it.toLong(), authorName = "Ann", createdAtMillis = null, preview = listOf(PreviewRun.Text("Passage $it"))) }

class JournalLongListTest {

    @get:Rule
    val rule = createComposeRule()

    private fun ComposeContentTestRule.setList(state: JournalUIState, scroll: ScrollState) {
        setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.verticalScroll(scroll)) {
                    JournalContent(state, onRetry = {})
                }
            }
        }
    }

    private fun composedCards(): Int = rule.onAllNodes(journalCardMatcher).fetchSemanticsNodes().size

    /** Whether the sentinel node is in the tree. */
    private fun ComposeContentTestRule.hasSentinel(): Boolean =
        onAllNodes(hasTestTag(JournalTestTags.MORE)).fetchSemanticsNodes().isNotEmpty()

    /** Scrolls the page to its end, one round at a time, until the list has no more pages or [maxRounds] pass. */
    private fun scrollToEnd(scroll: ScrollState, maxRounds: Int = 100): Int {
        var rounds = 0
        while (rounds < maxRounds && rule.hasSentinel()) {
            rule.scrollTo(scroll)
            rounds++
        }
        return rounds
    }

    // TC-59
    @Test
    fun tc59_scrollingComposesMoreAndReachesTheLastCard() {
        val scroll = ScrollState(0)
        rule.setList(JournalUIState(JournalUIContent.Loaded(cards(1000)), 0L), scroll)
        val before = composedCards()

        rule.scrollTo(scroll)
        assertTrue("one scroll round must compose more cards: $before to ${composedCards()}", composedCards() > before)

        scrollToEnd(scroll)

        rule.onNodeWithTag(JournalTestTags.card(1000)).assertExists()
        rule.onNodeWithTag(JournalTestTags.card(1000)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(JournalTestTags.MORE).assertDoesNotExist()
    }

    // TC-61
    @Test
    fun tc61_composedPageSurvivesRestore() {
        val tester = StateRestorationTester(rule)
        val scroll = ScrollState(0)
        val state = JournalUIState(JournalUIContent.Loaded(cards(1000)), 0L)
        tester.setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.verticalScroll(scroll)) {
                    JournalContent(state, onRetry = {})
                }
            }
        }
        var rounds = 0
        while (rounds < 100 && rule.onAllNodes(hasTestTag(JournalTestTags.card(41))).fetchSemanticsNodes().isEmpty()) {
            rule.scrollTo(scroll)
            rounds++
        }
        rule.onNodeWithTag(JournalTestTags.card(41)).assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag(JournalTestTags.card(41)).assertExists()
    }
}
