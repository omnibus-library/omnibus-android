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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

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
    fun tc48_loadingShowsTheHeadingOnly() {
        setContent(JournalUIContent.Loading)

        rule.onNodeWithTag(JournalTestTags.HEADING)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertTextEquals(detailText(R.string.book_detail_journal_heading))
        rule.onNodeWithTag(JournalTestTags.COUNT).assertDoesNotExist()
        rule.onNodeWithTag(JournalTestTags.EMPTY).assertDoesNotExist()
        rule.onNodeWithTag(JournalTestTags.FAILED).assertDoesNotExist()
        rule.onNodeWithTag(JournalTestTags.RETRY).assertDoesNotExist()
        rule.onNodeWithTag(JournalTestTags.MORE).assertDoesNotExist()
        rule.onAllNodes(journalCardMatcher).assertCountEquals(0)
    }

    // TC-51
    @Test
    fun tc51_bylinesShowAuthorDateAndDraft() {
        val draft = detailText(R.string.book_detail_journal_draft)
        setContent(
            JournalUIContent.Loaded(
                listOf(
                    testCard(1, authorName = "Ann", isOwn = false, createdAtMillis = OCT_24_2025),
                    testCard(2, authorName = "Ann", isOwn = true, createdAtMillis = OCT_15_2025),
                    testCard(3, authorName = "Ann", isOwn = true, isDraft = true, createdAtMillis = OCT_9_2025),
                    testCard(4, authorName = "", isOwn = true, createdAtMillis = null),
                ),
            ),
        )

        rule.onNodeWithTag(JournalTestTags.byline(1), useUnmergedTree = true)
            .assertTextEquals("Ann · ${dateText(OCT_24_2025)}")
        rule.onNodeWithTag(JournalTestTags.byline(2), useUnmergedTree = true)
            .assertTextEquals(dateText(OCT_15_2025))
        rule.onNodeWithTag(JournalTestTags.byline(3), useUnmergedTree = true)
            .assertTextEquals("${dateText(OCT_9_2025)} · $draft")
        rule.onAllNodes(hasTestTag(JournalTestTags.byline(4)), useUnmergedTree = true).assertCountEquals(0)
    }

    // TC-52
    @Test
    fun tc52_emptyShowsTheEmptyLineFourDpBelowTheHeading() {
        setContent(JournalUIContent.Loaded(emptyList()))

        rule.onNodeWithTag(JournalTestTags.EMPTY).assertTextEquals(detailText(R.string.book_detail_journal_empty))
        rule.onNodeWithTag(JournalTestTags.COUNT).assertDoesNotExist()
        rule.onAllNodes(journalCardMatcher).assertCountEquals(0)
        rule.onNodeWithTag(JournalTestTags.RETRY).assertDoesNotExist()

        val heading = rule.onNodeWithTag(JournalTestTags.HEADING).bounds()
        val empty = rule.onNodeWithTag(JournalTestTags.EMPTY).bounds()
        val gap = rule.dpOf(empty.top - heading.bottom)
        assertTrue("the empty line must sit 4 dp under the heading, was $gap dp", abs(gap - 4f) <= 1f)
    }

    // TC-53
    @Test
    fun tc53_failedShowsTheMessageAndTryAgainCallsRetry() {
        var retries = 0
        val state = setContent(JournalUIContent.Failed, onRetry = { retries++ })

        rule.onNodeWithTag(JournalTestTags.FAILED).assertTextEquals(detailText(R.string.book_detail_journal_failed))
        rule.onNodeWithTag(JournalTestTags.COUNT).assertDoesNotExist()
        val heading = rule.onNodeWithTag(JournalTestTags.HEADING).bounds()
        val failed = rule.onNodeWithTag(JournalTestTags.FAILED).bounds()
        val retry = rule.onNodeWithTag(JournalTestTags.RETRY).bounds()
        val failedGap = rule.dpOf(failed.top - heading.bottom)
        assertTrue("the message must sit 4 dp under the heading, was $failedGap dp", abs(failedGap - 4f) <= 1f)
        val retryGap = rule.dpOf(retry.top - failed.bottom)
        assertTrue("Try again must sit 12 dp under the message, was $retryGap dp", abs(retryGap - 12f) <= 1f)

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
