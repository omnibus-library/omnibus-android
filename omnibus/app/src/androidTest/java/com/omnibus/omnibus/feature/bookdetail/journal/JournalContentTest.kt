package com.omnibus.omnibus.feature.bookdetail.journal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Retry, semantics and TalkBack text. JournalScreenshotTest covers how each state looks. */
class JournalContentTest {

    @get:Rule
    val rule = createComposeRule()

    /** Renders [JournalContent] in a scrolling column, as the panel does, and returns its state. */
    private fun setContent(
        initial: JournalUIContent,
        onRetry: () -> Unit = {},
        darkTheme: Boolean = true,
    ) = mutableStateOf(JournalUIState(initial, NOW_MILLIS)).also { holder ->
        rule.setContent {
            OmnibusTheme(darkTheme = darkTheme) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    JournalContent(holder.value, onRetry = onRetry)
                }
            }
        }
    }

    // TC-48
    @Test
    fun tc48_theHeadingIsAHeading() {
        setContent(JournalUIContent.Loading)

        rule.onNodeWithTag(JournalTestTags.HEADING)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    // TC-53
    @Test
    fun tc53_tryAgainCallsRetryAndTheListComesBack() {
        var retries = 0
        val state = setContent(JournalUIContent.Failed, onRetry = { retries++ })

        rule.onNodeWithTag(JournalTestTags.RETRY).assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(1, retries)

        state.value = JournalUIState(JournalUIContent.Loading, NOW_MILLIS)
        rule.waitForIdle()
        rule.onNodeWithTag(JournalTestTags.FAILED).assertDoesNotExist()
        rule.onNodeWithTag(JournalTestTags.RETRY).assertDoesNotExist()
        rule.onNodeWithTag(JournalTestTags.EMPTY).assertDoesNotExist()

        state.value = JournalUIState(JournalUIContent.Loaded(listOf(testCard(1), testCard(2))), NOW_MILLIS)
        rule.waitForIdle()
        rule.onAllNodes(journalCardMatcher).assertCountEquals(2)
    }

    // TC-55
    @Test
    fun tc55_cardIsOneItemAndSpokenPreviewHidesTheSpoiler() {
        val spoiler = spoilerHiddenText()
        setContent(
            JournalUIContent.Loaded(
                listOf(
                    testCard(
                        1,
                        authorName = "Ann",
                        createdAtMillis = OCT_24_2025,
                        preview = journalPreview("He was ||Ares||."),
                    ),
                    testCard(2, authorName = "", isOwn = true, createdAtMillis = null, preview = journalPreview("||Ares||")),
                ),
            ),
        )

        // The merged card is one item: its byline, then the spoken preview.
        rule.onNodeWithTag(JournalTestTags.card(1))
            .assertTextEquals("Ann · ${dateText(OCT_24_2025)}", "He was $spoiler.")
        rule.onNodeWithTag(JournalTestTags.card(2)).assertTextEquals(spoiler)
        rule.onAllNodes(hasText("Ares", substring = true), useUnmergedTree = true).assertCountEquals(0)
        // The filler is drawn, so its Text stays in the unmerged tree. The merged tree, which TalkBack reads, must not carry it.
        rule.onAllNodes(hasText("lore", substring = true)).assertCountEquals(0)
    }

    // TC-56
    @Test
    fun tc56_cardIsNotClickable() {
        setContent(JournalUIContent.Loaded(listOf(testCard(1, preview = journalPreview("Plain text")))))

        rule.onNodeWithTag(JournalTestTags.card(1))
            .assert(hasClickAction().not())
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
        rule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(0)
    }
}
