package com.omnibus.omnibus.feature.bookdetail.more.merge

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MergeSheetAccessibilityTest {

    @get:Rule
    val rule = createComposeRule()

    private val target = mergeTarget()
    private val a = mergeBook(2, "Alpha", author = "Ann", year = null, formats = listOf("epub"))
    private val b = mergeBook(3, "Bravo", author = "Bea", year = "1902-01-01", formats = listOf("epub", "m4b"), seriesId = 5L)

    private val title get() = detailText(R.string.book_detail_merge_menu_row)
    private val closeText get() = detailText(R.string.book_detail_merge_close)
    private val searchLabel get() = detailText(R.string.book_detail_merge_search_label)
    private val searchHint get() = detailText(R.string.book_detail_merge_search_hint)

    /** Renders the sheet body from [state], which the test changes. */
    private fun setBody(state: MutableState<MergeUIState>, actions: MergeSheetActions = MergeSheetActions()) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                MergeSheetContent(state = state.value, target = target, actions = actions)
            }
        }
    }

    /** Applies [change] to the state from the test thread, then waits for the sheet to settle. */
    private fun ComposeContentTestRule.step(state: MutableState<MergeUIState>, change: MergeUIState.() -> MergeUIState) {
        runOnIdle { state.value = state.value.change() }
        waitForIdle()
    }

    private fun ComposeContentTestRule.assertNotePolite() {
        onNodeWithTag(MergeTestTags.NOTE).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
    }

    // TC-64
    @Test
    fun issue120_tc64_paneTitleHeadingAndClose() {
        val state = mutableStateOf(MergeUIState(sheetOpen = true))
        setBody(state)

        rule.onNodeWithTag(MergeTestTags.SHEET).assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title))
        rule.onNodeWithTag(MergeTestTags.TITLE).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithTag(MergeTestTags.CLOSE)
            .assert(hasContentDescription(closeText))
            .assertIsEnabled()
    }

    // TC-65
    @Test
    fun issue120_tc65_fieldLabelHintAndSearchAction() {
        val retries = mutableStateOf(0)
        setBody(mutableStateOf(MergeUIState(sheetOpen = true)), MergeSheetActions(onRetrySearch = { retries.value++ }))

        rule.onNodeWithTag(MergeTestTags.FIELD, useUnmergedTree = true).assert(hasContentDescription(searchLabel))
        rule.onNodeWithText(searchHint, useUnmergedTree = true).assertExists()

        rule.onNodeWithTag(MergeTestTags.FIELD).performImeAction()
        rule.waitForIdle()
        assertEquals(1, retries.value)
    }

    // TC-66
    @Test
    fun issue120_tc66_noteIsAPoliteLiveRegion() {
        setBody(mutableStateOf(MergeUIState(sheetOpen = true)))

        rule.assertNotePolite()
    }

    // TC-67
    @Test
    fun issue120_tc67_resultRowIsOneLabelledButton() {
        setBody(mutableStateOf(MergeUIState(sheetOpen = true, query = "x", candidates = listOf(a))))

        val row = rule.onNodeWithTag(MergeTestTags.candidate(a.uuid))
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        row.assertHasClickAction()
        row.assertTextEquals("Alpha", "Ann", "EPUB")
        assertEquals(
            detailText(R.string.book_detail_merge_choose_action),
            row.fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )

        // Cover and chevron are decorative: no description may survive inside the row.
        rule.onAllNodes(
            hasAnyAncestor(hasTestTag(MergeTestTags.candidate(a.uuid))) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
        ).assertCountEquals(0)
    }

    // TC-68
    @Test
    fun issue120_tc68_confirmStepSemantics() {
        val state = mutableStateOf(MergeUIState(sheetOpen = true, step = MergeStep.Confirm(b), merging = true, isOnline = true))
        setBody(state)
        rule.waitForIdle()

        // The covers are cleared from the merged tree that TalkBack reads, so nothing below them is announced.
        val covers = rule.onNodeWithTag(MergeTestTags.CONFIRM_COVERS, useUnmergedTree = true)
        covers.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        covers.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        rule.onAllNodes(hasAnyAncestor(hasTestTag(MergeTestTags.CONFIRM_COVERS))).assertCountEquals(0)

        rule.onNodeWithTag(MergeTestTags.CONFIRM_BODY).assertIsFocused()
        rule.onNodeWithTag(MergeTestTags.MERGE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertTextEquals(detailText(R.string.book_detail_merge_merging))

        rule.step(state) { copy(merging = false, mergeError = MergeFailure.Generic) }
        rule.onNodeWithTag(MergeTestTags.MERGE_ERROR)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }
}
