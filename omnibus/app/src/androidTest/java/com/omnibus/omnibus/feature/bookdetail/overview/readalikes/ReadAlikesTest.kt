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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

/** Semantics, taps, the spinner and live region. ReadAlikesScreenshotTest covers how each state looks. */
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
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ReadAlikes(
                    bookTitle = title,
                    state = state,
                    onOpen = onOpen,
                    reducedMotion = reducedMotion,
                )
            }
        }
    }

    /** The text of the node with [tag], one entry per Text in its merged config. */
    private fun textsOf(tag: String): List<String> =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private fun spinnerRange(): ProgressBarRangeInfo =
        rule.onNodeWithTag(ReadAlikesTestTags.SPINNER, useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ProgressBarRangeInfo)!!

    private fun liveRegionOf(tag: String): LiveRegionMode? =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion)

    // #106 TC-57 (AC1)
    @Test
    fun tc57_theHeadingIsAHeading() {
        setState(threeCards())

        assertTrue(rule.onNodeWithTag(ReadAlikesTestTags.HEADING).fetchSemanticsNode().config.contains(SemanticsProperties.Heading))
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

    // #106 TC-66 (AC6)
    @Test
    fun tc66_reducedMotionShowsAStaticSpinner() {
        setState(ReadAlikesUiState.Searching, reducedMotion = true)

        assertNotEquals(ProgressBarRangeInfo.Indeterminate, spinnerRange())
    }

    // #106 TC-67 (AC6)
    @Test
    fun tc67_withMotionTheSpinnerIsIndeterminate() {
        setState(ReadAlikesUiState.Searching, reducedMotion = false)

        assertEquals(ProgressBarRangeInfo.Indeterminate, spinnerRange())
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
}
