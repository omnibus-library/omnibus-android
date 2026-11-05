package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.suggestions.BookSuggestion
import com.omnibus.omnibus.data.suggestions.suggestion
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.overview.shelf.BookShelfTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReadAlikesTest {

    @get:Rule
    val rule = createComposeRule()

    private val title = "The Left Hand of Darkness"

    private fun readyOf(items: List<BookSuggestion>): ReadAlikesUiState.Ready =
        ReadAlikesUiState.Ready(readAlikesOf(items))

    /** Three suggestions, "Book 1" to "Book 3", each by "Author 1" to "Author 3". */
    private fun threeCards(): ReadAlikesUiState.Ready =
        readyOf(listOf(suggestion(1), suggestion(2), suggestion(3)))

    private fun setState(
        state: ReadAlikesUiState,
        onOpen: (ReadAlike) -> Unit = {},
        reducedMotion: Boolean = true,
        darkTheme: Boolean = true,
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = darkTheme) {
                ReadAlikes(
                    bookTitle = title,
                    state = state,
                    onOpen = onOpen,
                    reducedMotion = reducedMotion,
                )
            }
        }
    }

    /** The text of the node with [tag], one entry per Text in its (merged, unless [useUnmergedTree]) config. */
    private fun textsOf(tag: String, useUnmergedTree: Boolean = false): List<String> =
        rule.onNodeWithTag(tag, useUnmergedTree).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    /** The text of each card, in order: its merged title and author. */
    private fun cardTexts(): List<List<String>> =
        rule.onAllNodesWithTag(BookShelfTestTags.CARD).fetchSemanticsNodes().map { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        }

    private fun spinnerRange(): ProgressBarRangeInfo =
        rule.onNodeWithTag(ReadAlikesTestTags.SPINNER, useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ProgressBarRangeInfo)!!

    private fun liveRegionOf(tag: String): LiveRegionMode? =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion)

    // #106 TC-57 (AC1)
    @Test
    fun tc57_readyShowsTheHeadingTheCaptionAndOneCardPerSuggestion() {
        setState(threeCards())

        rule.onNodeWithTag(ReadAlikesTestTags.HEADING).assertIsDisplayed()
        assertEquals(detailText(R.string.book_detail_readalikes_heading, title), textsOf(ReadAlikesTestTags.HEADING).single())
        assertTrue(rule.onNodeWithTag(ReadAlikesTestTags.HEADING).fetchSemanticsNode().config.contains(SemanticsProperties.Heading))
        assertEquals(detailText(R.string.book_detail_readalikes_source), textsOf(ReadAlikesTestTags.SOURCE).single())
        rule.onAllNodesWithTag(BookShelfTestTags.CARD).assertCountEquals(3)
        rule.onNodeWithTag(ReadAlikesTestTags.STATUS).assertDoesNotExist()
        rule.onAllNodesWithTag(ReadAlikesTestTags.SPINNER, useUnmergedTree = true).assertCountEquals(0)
    }

    // #106 TC-59 (AC1, AC6)
    @Test
    fun tc59_eachCardIsOneNodeAnnouncingItsTitleAndAuthorInServerOrder() {
        setState(threeCards())

        assertEquals(
            listOf(listOf("Book 1", "Author 1"), listOf("Book 2", "Author 2"), listOf("Book 3", "Author 3")),
            cardTexts(),
        )
    }

    // #106 TC-61 (AC1)
    @Test
    fun tc61_aBlankAuthorDrawsNoAuthorLine() {
        setState(readyOf(listOf(suggestion(1, author = ""))))

        rule.onAllNodesWithTag(BookShelfTestTags.SUBTITLE, useUnmergedTree = true).assertCountEquals(0)
        assertEquals(listOf(listOf("Book 1")), cardTexts())
    }

    // #106 TC-62 (AC1)
    @Test
    fun tc62_aLongShelfScrollsToItsLastCard() {
        setState(readyOf((1..12).map { suggestion(it.toLong()) }))

        rule.onNodeWithTag(BookShelfTestTags.ROW).performScrollToIndex(11)

        rule.onNodeWithText("Book 12").assertIsDisplayed()
    }

    // #106 TC-63 (AC5)
    @Test
    fun tc63_tappingACardCallsOnOpenWithThatSuggestion() {
        val state = threeCards()
        val opened = mutableListOf<ReadAlike>()
        setState(state, onOpen = { opened += it })

        rule.onAllNodesWithTag(BookShelfTestTags.CARD)[1].performClick()

        assertEquals(listOf(state.items[1]), opened)
    }

    // #106 TC-64 (AC6)
    @Test
    fun tc64_theCardExposesAnOpenOnHardcoverClickLabelAndButtonRole() {
        setState(threeCards())

        val card = rule.onAllNodesWithTag(BookShelfTestTags.CARD)[0].fetchSemanticsNode().config
        assertEquals(detailText(R.string.book_detail_readalikes_open), card.getOrNull(SemanticsActions.OnClick)?.label)
        assertEquals(Role.Button, card.getOrNull(SemanticsProperties.Role))
    }

    // #106 TC-65 (AC2)
    @Test
    fun tc65_searchingShowsTheHeadingTheCaptionAndASpinnerLine() {
        setState(ReadAlikesUiState.Searching, reducedMotion = false)

        rule.onNodeWithTag(ReadAlikesTestTags.HEADING).assertIsDisplayed()
        assertEquals(detailText(R.string.book_detail_readalikes_source), textsOf(ReadAlikesTestTags.SOURCE).single())
        assertEquals(
            listOf(detailText(R.string.book_detail_readalikes_searching)),
            textsOf(ReadAlikesTestTags.STATUS_TEXT, useUnmergedTree = true),
        )
        rule.onNodeWithTag(ReadAlikesTestTags.SPINNER, useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag(BookShelfTestTags.CARD).assertCountEquals(0)
    }

    // #106 TC-66 (AC6)
    @Test
    fun tc66_reducedMotionShowsAStaticThreeQuarterRing() {
        setState(ReadAlikesUiState.Searching, reducedMotion = true)

        val range = spinnerRange()
        assertEquals(0.75f, range.current, 0f)
        assertNotEquals(ProgressBarRangeInfo.Indeterminate, range)

        rule.mainClock.advanceTimeBy(2_000)

        assertEquals(0.75f, spinnerRange().current, 0f)
    }

    // #106 TC-67 (AC6)
    @Test
    fun tc67_withMotionTheSpinnerIsIndeterminate() {
        setState(ReadAlikesUiState.Searching, reducedMotion = false)

        assertEquals(ProgressBarRangeInfo.Indeterminate, spinnerRange())
    }

    // #106 TC-68 (AC3)
    @Test
    fun tc68_unavailableShowsTheMessageWithoutASpinner() {
        setState(ReadAlikesUiState.Unavailable)

        rule.onNodeWithTag(ReadAlikesTestTags.HEADING).assertIsDisplayed()
        assertEquals(detailText(R.string.book_detail_readalikes_source), textsOf(ReadAlikesTestTags.SOURCE).single())
        assertEquals(
            listOf(detailText(R.string.book_detail_readalikes_unavailable)),
            textsOf(ReadAlikesTestTags.STATUS_TEXT, useUnmergedTree = true),
        )
        rule.onAllNodesWithTag(ReadAlikesTestTags.SPINNER, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag(BookShelfTestTags.CARD).assertCountEquals(0)
    }

    // #106 TC-69 (AC4)
    @Test
    fun tc69_hiddenDrawsNothing() {
        setState(ReadAlikesUiState.Hidden)

        listOf(
            ReadAlikesTestTags.SECTION,
            ReadAlikesTestTags.HEADING,
            ReadAlikesTestTags.SOURCE,
            ReadAlikesTestTags.STATUS,
            ReadAlikesTestTags.STATUS_TEXT,
            BookShelfTestTags.CARD,
        ).forEach { tag ->
            rule.onAllNodesWithTag(tag, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // #106 TC-70 (AC6)
    @Test
    fun tc70_theStatusLineIsOneMergedPoliteLiveRegion() {
        var state by mutableStateOf<ReadAlikesUiState>(ReadAlikesUiState.Searching)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ReadAlikes(bookTitle = title, state = state, onOpen = {}, reducedMotion = true)
            }
        }

        rule.onAllNodesWithTag(ReadAlikesTestTags.STATUS).assertCountEquals(1)
        assertEquals(LiveRegionMode.Polite, liveRegionOf(ReadAlikesTestTags.STATUS))
        assertTrue(textsOf(ReadAlikesTestTags.STATUS).contains(detailText(R.string.book_detail_readalikes_searching)))

        rule.runOnIdle { state = ReadAlikesUiState.Unavailable }
        rule.waitForIdle()

        assertEquals(LiveRegionMode.Polite, liveRegionOf(ReadAlikesTestTags.STATUS))
        assertTrue(textsOf(ReadAlikesTestTags.STATUS).contains(detailText(R.string.book_detail_readalikes_unavailable)))
    }

    // #106 TC-71 (AC2, AC3, AC6)
    @Test
    fun tc71_searchingMovesToUnavailableInPlace() {
        var state by mutableStateOf<ReadAlikesUiState>(ReadAlikesUiState.Searching)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ReadAlikes(bookTitle = title, state = state, onOpen = {}, reducedMotion = true)
            }
        }
        rule.onNodeWithTag(ReadAlikesTestTags.SPINNER, useUnmergedTree = true).assertExists()

        rule.runOnIdle { state = ReadAlikesUiState.Unavailable }
        rule.waitForIdle()

        assertEquals(
            listOf(detailText(R.string.book_detail_readalikes_unavailable)),
            textsOf(ReadAlikesTestTags.STATUS_TEXT, useUnmergedTree = true),
        )
        rule.onAllNodesWithTag(ReadAlikesTestTags.SPINNER, useUnmergedTree = true).assertCountEquals(0)
        assertEquals(LiveRegionMode.Polite, liveRegionOf(ReadAlikesTestTags.STATUS))
    }

    // #106 TC-72 (AC1)
    @Test
    fun tc72_aCardWithAPathButNoFetcherStillShowsItsPlate() {
        setState(readyOf(listOf(suggestion(1, coverPath = "/api/suggestions/u/1/cover"))))

        rule.onNodeWithTag(BookShelfTestTags.CARD).assertIsDisplayed()
        assertEquals(listOf(listOf("Book 1", "Author 1")), cardTexts())
    }

    // #106 TC-73 (AC1)
    @Test
    fun tc73_theShelfRendersInLightTheme() {
        setState(threeCards(), darkTheme = false)

        rule.onAllNodesWithTag(BookShelfTestTags.CARD).assertCountEquals(3)
        assertEquals(
            listOf(listOf("Book 1", "Author 1"), listOf("Book 2", "Author 2"), listOf("Book 3", "Author 3")),
            cardTexts(),
        )
    }
}
