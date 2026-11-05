package com.omnibus.omnibus.feature.bookdetail.more.merge

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.SemanticsProperties
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.more.pressBackKey
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MergeSheetContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val target = mergeTarget()
    private val idle get() = detailText(R.string.book_detail_merge_note_idle)
    private val needsConnection get() = detailText(R.string.book_detail_more_needs_connection)

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

    // TC-52
    @Test
    fun issue120_tc52_noteFollowsTheSearchState() {
        val state = mutableStateOf(MergeUIState(sheetOpen = true))
        setBody(state)
        rule.onNodeWithTag(MergeTestTags.NOTE).assertTextEquals(idle)

        rule.step(state) { copy(query = "dune", searching = true) }
        rule.onNodeWithTag(MergeTestTags.SPINNER, useUnmergedTree = true).assertExists()
        rule.onAllNodesWithText(idle).assertCountEquals(0)

        rule.step(state) { copy(searching = false, query = "dune", candidates = listOf(a)) }
        rule.onNodeWithTag(MergeTestTags.NOTE).assertTextEquals(noteCount(1))

        rule.step(state) { copy(searching = false, query = "dune", candidates = listOf(a, b, c)) }
        rule.onNodeWithTag(MergeTestTags.NOTE).assertTextEquals(noteCount(3))

        rule.step(state) { copy(searching = false, query = "zzz", candidates = emptyList()) }
        rule.onNodeWithTag(MergeTestTags.NOTE).assertTextEquals(detailText(R.string.book_detail_merge_note_none, "zzz"))

        rule.step(state) { copy(searching = false, query = "dune", candidates = emptyList(), isOnline = false) }
        rule.onNodeWithTag(MergeTestTags.NOTE).assertTextEquals(needsConnection)
    }

    // TC-53
    @Test
    fun issue120_tc53_searchErrorShowsRetry() {
        val state = mutableStateOf(MergeUIState(sheetOpen = true, query = "dune", searchFailed = true, candidates = listOf(a)))
        val retries = mutableStateOf(0)
        setBody(state, MergeSheetActions(onRetrySearch = { retries.value++ }))

        rule.onNodeWithText(detailText(R.string.book_detail_merge_search_failed)).assertExists()
        rule.onNodeWithTag(MergeTestTags.RETRY).assertExists()
        rule.onAllNodesWithTag(MergeTestTags.candidate(a.uuid)).assertCountEquals(0)

        rule.onNodeWithTag(MergeTestTags.RETRY).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(1, retries.value)

        rule.step(state) { copy(searchFailed = false) }
        rule.onAllNodesWithText(detailText(R.string.book_detail_merge_search_failed)).assertCountEquals(0)
        rule.onAllNodesWithTag(MergeTestTags.RETRY).assertCountEquals(0)
    }

    // TC-54
    @Test
    fun issue120_tc54_resultRowsShowContentAndChoose() {
        val state = mutableStateOf(MergeUIState(sheetOpen = true, query = "x", candidates = listOf(a, b, c)))
        val chosen = mutableStateOf<List<Book>>(emptyList())
        setBody(state, MergeSheetActions(onChoose = { chosen.value = chosen.value + it }))

        val rowA = rule.onNodeWithTag(MergeTestTags.candidate(a.uuid))
        val rowB = rule.onNodeWithTag(MergeTestTags.candidate(b.uuid))
        val rowC = rule.onNodeWithTag(MergeTestTags.candidate(c.uuid))
        assertTrue("rows out of order", rowA.top() < rowB.top() && rowB.top() < rowC.top())

        rowA.assertTextEquals("Alpha", "Ann", "EPUB")
        rowB.assertTextEquals("Bravo", "Bea · 1902", "Same series · EPUB, M4B")
        rowC.assertTextEquals("Charlie", "Cy")
        rule.onAllNodesWithText(target.displayTitle).assertCountEquals(1)

        rowB.performScrollTo().performClick()
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
    fun issue120_tc56_confirmStepNamesBothBooksAndFiresIntents() {
        val state = mutableStateOf(MergeUIState(sheetOpen = true, step = MergeStep.Confirm(b)))
        val backs = mutableStateOf(0)
        val confirms = mutableStateOf(0)
        setBody(state, MergeSheetActions(onBack = { backs.value++ }, onConfirm = { confirms.value++ }))

        val body = rule.onNodeWithTag(MergeTestTags.CONFIRM_BODY).fetchSemanticsNode()
            .config[SemanticsProperties.Text].single()
        assertEquals(detailText(R.string.book_detail_merge_confirm_body, b.displayTitle, target.displayTitle), body.text)
        val bold = body.spanStyles
            .filter { it.item.fontWeight == FontWeight.Bold }
            .map { body.text.substring(it.start, it.end) }
        assertEquals(listOf(b.displayTitle, target.displayTitle), bold)

        rule.onAllNodesWithTag(MergeTestTags.FIELD).assertCountEquals(0)
        rule.onAllNodesWithTag(MergeTestTags.candidate(b.uuid)).assertCountEquals(0)
        rule.onNodeWithTag(MergeTestTags.BACK).assertIsEnabled()
        rule.onNodeWithTag(MergeTestTags.MERGE).assertIsEnabled()
        rule.onAllNodesWithText(needsConnection).assertCountEquals(0)

        rule.onNodeWithTag(MergeTestTags.BACK).performClick()
        rule.onNodeWithTag(MergeTestTags.MERGE).performClick()
        rule.waitForIdle()
        assertEquals(1, backs.value)
        assertEquals(1, confirms.value)
    }

    // TC-57
    @Test
    fun issue120_tc57_confirmLayoutAndOffline() {
        val state = mutableStateOf(MergeUIState(sheetOpen = true, step = MergeStep.Confirm(b), isOnline = true))
        setBody(state)

        val covers = rule.onNodeWithTag(MergeTestTags.CONFIRM_COVERS, useUnmergedTree = true)
        val body = rule.onNodeWithTag(MergeTestTags.CONFIRM_BODY)
        val back = rule.onNodeWithTag(MergeTestTags.BACK)
        val merge = rule.onNodeWithTag(MergeTestTags.MERGE)
        assertTrue("covers must sit above the body", covers.top() < body.top())
        assertTrue("body must sit above the buttons", body.top() < back.top())
        assertTrue(
            "Back must sit before Merge",
            back.fetchSemanticsNode().positionInRoot.x < merge.fetchSemanticsNode().positionInRoot.x,
        )
        merge.assertIsEnabled()
        rule.onAllNodesWithTag(MergeTestTags.OFFLINE_NOTE).assertCountEquals(0)

        rule.step(state) { copy(isOnline = false) }
        rule.onNodeWithTag(MergeTestTags.MERGE).assertIsNotEnabled()
        val offlineNote = rule.onNodeWithTag(MergeTestTags.OFFLINE_NOTE)
        offlineNote.assertTextEquals(needsConnection)
        assertTrue(
            "offline note must sit between the body and the buttons",
            body.top() < offlineNote.top() && offlineNote.top() < back.top(),
        )
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

    // TC-59
    @Test
    fun issue120_tc59_mergeErrorLine() {
        val state = mutableStateOf(MergeUIState(sheetOpen = true, step = MergeStep.Confirm(b), mergeError = MergeFailure.Generic))
        setBody(state)

        val error = rule.onNodeWithTag(MergeTestTags.MERGE_ERROR)
        error.assertTextEquals(detailText(R.string.book_detail_merge_failed))
        assertTrue("error must sit below the body", rule.onNodeWithTag(MergeTestTags.CONFIRM_BODY).top() < error.top())
        rule.onNodeWithTag(MergeTestTags.BACK).assertIsEnabled()
        rule.onNodeWithTag(MergeTestTags.MERGE).assertIsEnabled()

        rule.step(state) { copy(mergeError = MergeFailure.Gone) }
        rule.onNodeWithTag(MergeTestTags.MERGE_ERROR).assertTextEquals(detailText(R.string.book_detail_merge_failed_gone))

        rule.step(state) { copy(mergeError = null) }
        rule.onAllNodesWithTag(MergeTestTags.MERGE_ERROR, useUnmergedTree = true).assertCountEquals(0)
    }

    // TC-60
    @Test
    fun issue120_tc60_backRestoresSearchAndCloseCloses() {
        val state = mutableStateOf(MergeUIState(sheetOpen = true, query = "dune", candidates = listOf(a)))
        val closes = mutableStateOf(0)
        setBody(
            state,
            MergeSheetActions(
                onChoose = { state.value = state.value.copy(step = MergeStep.Confirm(it)) },
                onBack = { state.value = state.value.copy(step = MergeStep.Search, mergeError = null) },
                onClose = { closes.value++ },
            ),
        )

        rule.onNodeWithTag(MergeTestTags.candidate(a.uuid)).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(MergeTestTags.BACK).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(MergeTestTags.FIELD).assertTextEquals("dune")
        rule.onNodeWithTag(MergeTestTags.candidate(a.uuid)).assertExists()

        rule.onNodeWithTag(MergeTestTags.CLOSE).performClick()
        rule.waitForIdle()
        assertEquals(1, closes.value)
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
                    actions = MergeSheetActions(onClose = { if (!merging.value) closes.value++ }),
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
