package com.omnibus.omnibus.feature.bookdetail.overview.series

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.stage.BookByline
import com.omnibus.omnibus.feature.bookdetail.stage.bylineModel
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailScreenContent
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The stage series line: plain without a link state, a link with one, and the page-level wiring (CC3).
 * BookBylineScreenshotTest covers how the plain line and the link look.
 */
class SeriesLineLinkTest {

    @get:Rule
    val rule = createComposeRule()

    private var book by mutableStateOf(testDetailBook(series = "Dune", seriesIndex = "2"))
    private var line by mutableStateOf<SeriesLineState?>(null)
    private var clicks = 0
    private val navigations = mutableListOf<BookDetailDestination>()

    @Composable
    private fun Byline() {
        OmnibusTheme(darkTheme = true) {
            BookByline(
                bylineModel(book),
                onNavigate = { navigations += it },
                seriesLine = line,
            )
        }
    }

    private fun setByline() {
        rule.setContent { Byline() }
        rule.waitForIdle()
    }

    private fun linkState(total: Int = 5) = SeriesLineState(total) { clicks++ }

    private fun ComposeContentTestRule.series() = onNodeWithTag(BookDetailTestTags.SERIES)

    // TC-105
    @Test
    fun issue104_tc105_plainLineWithoutState() {
        setByline()
        rule.series().assertTextEquals("Dune, book 2")
        rule.series().assertHasNoClickAction()
        rule.series().assertHeightIsAtLeast(48.dp)

        rule.runOnIdle { book = testDetailBook(series = "Dune", seriesIndex = "") }
        rule.waitForIdle()
        rule.series().assertTextEquals("Dune")
        rule.series().assertHasNoClickAction()
    }

    // TC-107
    @Test
    fun issue104_tc107_linkWithTotal() {
        line = linkState(5)
        setByline()
        rule.series().assertTextEquals("Dune, book 2 of 5")
        rule.series().assertHasClickAction()
        assertEquals(Role.Button, rule.series().fetchSemanticsNode().config[SemanticsProperties.Role])
        assertEquals(detailText(R.string.book_detail_series_line_click_label), rule.series().clickLabel())
    }

    // TC-108
    @Test
    fun issue104_tc108_tapInvokesOnClick() {
        line = linkState(5)
        setByline()
        rule.series().performClick()
        rule.waitForIdle()
        assertEquals(1, clicks)
        assertTrue(navigations.isEmpty())
    }

    // TC-110
    @Test
    fun issue104_tc110_outOfRangeIndexLeavesOutTotal() {
        line = linkState(5)
        book = testDetailBook(series = "Dune", seriesIndex = "7")
        setByline()
        rule.series().assertTextEquals("Dune, book 7")
        rule.series().assertHasClickAction()

        rule.runOnIdle { book = testDetailBook(series = "Dune", seriesIndex = "II") }
        rule.waitForIdle()
        rule.series().assertTextEquals("Dune, book II")
        rule.series().assertHasClickAction()

        rule.runOnIdle { book = testDetailBook(series = "Dune", seriesIndex = null) }
        rule.waitForIdle()
        rule.series().assertTextEquals("Dune")
        rule.series().assertHasClickAction()
    }

    // TC-112
    @Test
    fun issue104_tc112_tagAndDescriptionKept() {
        setByline()
        val expected = detailText(R.string.book_detail_series_description, "Dune, book 2")
        rule.series().assertIsDisplayed()
        assertEquals(expected, rule.series().description())

        rule.runOnIdle { line = linkState(5) }
        rule.waitForIdle()
        rule.series().assertIsDisplayed()
        assertEquals(detailText(R.string.book_detail_series_description, "Dune, book 2 of 5"), rule.series().description())
    }

    // TC-113
    @Test
    fun issue104_tc113_pageWithoutSeriesIdIsPlain() {
        var sourceCalls = 0
        val source = SeriesSource { _, _ ->
            sourceCalls++
            members(5)
        }
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                CompositionLocalProvider(LocalSeriesSource provides source) {
                    PageContent(book.copy(seriesId = null), scrollState = ScrollState(0))
                }
            }
        }
        rule.waitForIdle()
        assertEquals("the source is never asked", 0, sourceCalls)
        rule.series().assertTextEquals("Dune, book 2")
        rule.series().assertHasNoClickAction()
    }

    // TC-114
    @Test
    fun issue104_tc114_pageLineFollowsTheMembers() {
        var membersState by mutableStateOf<List<SeriesMember>?>(members(5))
        val source = SeriesSource { _, _ -> membersState }
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                CompositionLocalProvider(LocalSeriesSource provides source) {
                    PageContent(book.copy(seriesId = 4L), scrollState = ScrollState(0))
                }
            }
        }
        rule.waitForIdle()
        rule.series().assertTextEquals("Dune, book 2 of 5")
        rule.series().assertHasClickAction()

        rule.runOnIdle { membersState = null }
        rule.waitForIdle()
        rule.series().assertTextEquals("Dune, book 2")
        rule.series().assertHasNoClickAction()

        rule.runOnIdle { membersState = members(1) }
        rule.waitForIdle()
        rule.series().assertTextEquals("Dune, book 2")
        rule.series().assertHasNoClickAction()
    }

    @Composable
    private fun PageContent(page: Book, scrollState: ScrollState) {
        OmnibusTheme(darkTheme = true) {
            BookDetailScreenContent(
                uiState = BookDetailUIState(book = page, isLoading = false),
                onNavigate = {},
                onClose = {},
                onRetry = {},
                onRefresh = {},
                onReloadBook = {},
                onBookChanged = {},
                onTransientErrorShown = {},
                modifier = Modifier.fillMaxSize(),
                slots = BookDetailSlots(),
                scrollState = scrollState,
            )
        }
    }
}
