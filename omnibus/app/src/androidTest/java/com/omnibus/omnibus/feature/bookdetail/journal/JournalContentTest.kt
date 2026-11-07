package com.omnibus.omnibus.feature.bookdetail.journal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
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

    /** Renders [JournalContent] in a scrolling column, as the panel does. */
    private fun setContent(content: JournalUIContent, onRetry: () -> Unit = {}) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    JournalContent(JournalUIState(content, NOW_MILLIS), onRetry = onRetry)
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
    fun tc53_tryAgainCallsRetry() {
        var retries = 0
        setContent(JournalUIContent.Failed, onRetry = { retries++ })

        rule.onNodeWithTag(JournalTestTags.RETRY).performClick()
        rule.waitForIdle()
        assertEquals(1, retries)
    }

    // TC-55
    @Test
    fun tc55_theSpokenCardHidesTheSpoiler() {
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
                ),
            ),
        )

        // The merged card is one item: its byline, then the spoken preview.
        rule.onNodeWithTag(JournalTestTags.card(1))
            .assertTextEquals("Ann · ${dateText(OCT_24_2025)}", "He was $spoiler.")
        rule.onAllNodes(hasText("Ares", substring = true), useUnmergedTree = true).assertCountEquals(0)
        // The filler is drawn, so its Text stays in the unmerged tree. The merged tree, which TalkBack reads, must not carry it.
        rule.onAllNodes(hasText("lore", substring = true)).assertCountEquals(0)
    }
}
