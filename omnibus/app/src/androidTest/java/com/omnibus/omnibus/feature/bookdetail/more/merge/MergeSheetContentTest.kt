package com.omnibus.omnibus.feature.bookdetail.more.merge

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.more.pressBackKey
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The sheet body's focus, typing, retry, choosing, confirming, disabling and back. MergeSheetScreenshotTest covers how it looks. */
class MergeSheetContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val target = mergeTarget()

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

    private val a = mergeBook(2, "Alpha", author = "Ann", year = null, formats = listOf("epub"))
    private val b = mergeBook(3, "Bravo", author = "Bea", year = "1902-01-01", formats = listOf("epub", "m4b"), seriesId = 5L)
    private val c = mergeBook(4, "Charlie", author = "Cy", year = null, formats = emptyList())

    // TC-51
    @Test
    fun issue120_tc51_fieldTakesFocusAndReportsTyping() {
        val state = mutableStateOf(MergeUIState(sheetOpen = true))
        val queries = mutableListOf<String>()
        setBody(state, MergeSheetActions(onQueryChange = {
            queries += it
            state.value = state.value.copy(query = it)
        }))
        rule.waitForIdle()

        rule.onNodeWithTag(MergeTestTags.FIELD).assertIsFocused()
        rule.onNodeWithTag(MergeTestTags.FIELD).performTextInput("dune")
        rule.waitForIdle()

        assertEquals("dune", queries.last())
        rule.onNodeWithTag(MergeTestTags.FIELD).assertTextEquals("dune")
    }

    // TC-53
    @Test
    fun issue120_tc53_searchErrorRetries() {
        val state = mutableStateOf(MergeUIState(sheetOpen = true, query = "dune", searchFailed = true, candidates = listOf(a)))
        val retries = mutableStateOf(0)
        setBody(state, MergeSheetActions(onRetrySearch = { retries.value++ }))

        rule.onNodeWithTag(MergeTestTags.RETRY).performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(1, retries.value)
    }

    // TC-54
    @Test
    fun issue120_tc54_tappingAResultRowChoosesIt() {
        val state = mutableStateOf(MergeUIState(sheetOpen = true, query = "x", candidates = listOf(a, b, c)))
        val chosen = mutableStateOf<List<Book>>(emptyList())
        setBody(state, MergeSheetActions(onChoose = { chosen.value = chosen.value + it }))

        rule.onNodeWithTag(MergeTestTags.candidate(b.uuid)).performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(listOf(b), chosen.value)
    }

    // TC-55
    @Test
    fun issue120_tc55_longResultListScrolls() {
        val many = (1..20).map { mergeBook(it + 100L, "Book $it", uuid = "many-$it") }
        val state = mutableStateOf(MergeUIState(sheetOpen = true, query = "book", candidates = many))
        setBody(state)

        rule.onNodeWithTag(MergeTestTags.candidate("many-20")).performScrollTo().assertIsDisplayed()
    }

    // TC-56
    @Test
    fun issue120_tc56_confirmStepControlsFireTheirActions() {
        val state = mutableStateOf(MergeUIState(sheetOpen = true, step = MergeStep.Confirm(b)))
        val backs = mutableStateOf(0)
        val confirms = mutableStateOf(0)
        val closes = mutableStateOf(0)
        setBody(
            state,
            MergeSheetActions(onBack = { backs.value++ }, onConfirm = { confirms.value++ }, onClose = { closes.value++ }),
        )

        rule.onNodeWithTag(MergeTestTags.BACK).performClick()
        rule.onNodeWithTag(MergeTestTags.MERGE).performClick()
        rule.onNodeWithTag(MergeTestTags.CLOSE).performClick()
        rule.waitForIdle()

        assertEquals(1, backs.value)
        assertEquals(1, confirms.value)
        assertEquals(1, closes.value)
    }

    // TC-57
    @Test
    fun issue120_tc57_offlineDisablesMerge() {
        val state = mutableStateOf(MergeUIState(sheetOpen = true, step = MergeStep.Confirm(b), isOnline = true))
        setBody(state)
        rule.onNodeWithTag(MergeTestTags.MERGE).assertIsEnabled()

        rule.step(state) { copy(isOnline = false) }

        rule.onNodeWithTag(MergeTestTags.MERGE).assertIsNotEnabled()
    }

    // TC-58
    @Test
    fun issue120_tc58_mergingDisablesEveryControl() {
        val state = mutableStateOf(MergeUIState(sheetOpen = true, step = MergeStep.Confirm(b), merging = true))
        val clicks = mutableStateOf(0)
        setBody(state, MergeSheetActions(onBack = { clicks.value++ }, onConfirm = { clicks.value++ }, onClose = { clicks.value++ }))

        rule.onNodeWithTag(MergeTestTags.MERGE).assertTextEquals(detailText(R.string.book_detail_merge_merging))
        rule.onNodeWithTag(MergeTestTags.MERGE).assertIsNotEnabled()
        rule.onNodeWithTag(MergeTestTags.BACK).assertIsNotEnabled()
        rule.onNodeWithTag(MergeTestTags.CLOSE).assertIsNotEnabled()

        rule.onNodeWithTag(MergeTestTags.MERGE).performClick()
        rule.onNodeWithTag(MergeTestTags.BACK).performClick()
        rule.onNodeWithTag(MergeTestTags.CLOSE).performClick()
        rule.waitForIdle()
        assertEquals(0, clicks.value)
    }

    // TC-61
    @Test
    fun issue120_tc61_backIsIgnoredOnlyWhileMerging() {
        val merging = mutableStateOf(true)
        val closes = mutableStateOf(0)
        val state = mutableStateOf(MergeUIState(sheetOpen = true, step = MergeStep.Confirm(b), isOnline = true))
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                MergeSheet(
                    state = state.value.copy(merging = merging.value),
                    target = target,
                    actions = MergeSheetActions(onClose = { closes.value++ }),
                )
            }
        }
        rule.waitForIdle()

        rule.pressBackKey()
        rule.onNodeWithTag(MergeTestTags.SHEET).assertExists()
        assertEquals(0, closes.value)

        rule.runOnIdle { merging.value = false }
        rule.pressBackKey()
        rule.waitUntil(5_000) { closes.value == 1 }
    }
}
