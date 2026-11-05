package com.omnibus.omnibus.feature.bookdetail.overview.series

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.tabs.BookDetailTabsTestTags
import com.omnibus.omnibus.feature.bookdetail.tabs.fakeSection
import com.omnibus.omnibus.feature.bookdetail.tabs.tabNode
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailScreenContent
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The stage series line scrolls the page to the Series section, which lands below the pinned tab strip (AC5).
 * Taps use a semantics click because the stage can be scrolled off screen after a tab change.
 */
class SeriesAnchorScrollTest {

    @get:Rule
    val rule = createComposeRule()

    private fun ComposeContentTestRule.dp(value: androidx.compose.ui.unit.Dp): Float = with(density) { value.toPx() }

    /** A filler panel twice as tall as the window, so the page scrolls well past the strip. */
    private fun windowTallSection(tag: String): BookDetailSection = BookDetailSection { _, modifier ->
        val windowHeightPx = LocalWindowInfo.current.containerSize.height
        val heightDp = with(LocalDensity.current) { (windowHeightPx * 2).toDp() }
        Box(modifier.fillMaxWidth().height(heightDp).testTag(tag))
    }

    private fun seriesBookAt(position: Int) = seriesBook(position = position)

    private fun setPage(
        book: Book,
        slots: BookDetailSlots,
        scroll: ScrollState,
        list: List<SeriesMember>? = members(5),
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                CompositionLocalProvider(LocalSeriesSource provides SeriesSource { _, _ -> list }) {
                    BookDetailScreenContent(
                        uiState = BookDetailUIState(book = book, isLoading = false),
                        onNavigate = {},
                        onClose = {},
                        onRetry = {},
                        onRefresh = {},
                        onReloadBook = {},
                        onBookChanged = {},
                        onTransientErrorShown = {},
                        modifier = Modifier.fillMaxSize(),
                        slots = slots,
                        scrollState = scroll,
                        reducedMotion = true,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun tapStageLine() {
        rule.onNodeWithTag(BookDetailTestTags.SERIES).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    /** The heading lands 16 dp below the pinned strip, or at the page's end when the page cannot scroll that far. */
    private fun assertLanded(scroll: ScrollState) {
        val headingTop = rule.onNodeWithTag(SeriesFanTestTags.HEADING_ROW).bounds().top
        val target = rule.onNodeWithTag(BookDetailTabsTestTags.STRIP).bounds().bottom + rule.dp(16.dp)
        if (scroll.value == scroll.maxValue) {
            assertTrue("heading at $headingTop, expected at or below $target", headingTop >= target - 1f)
        } else {
            assertEquals(target, headingTop, 1f)
            rule.onNodeWithTag(SeriesFanTestTags.HEADING_ROW).assertIsDisplayed()
        }
    }

    private fun overviewSlots(): BookDetailSlots = BookDetailSlots(
        overviewSections = listOf(windowTallSection("filler"), SeriesFanSection),
        filesSections = listOf(fakeSection("files")),
    )

    // TC-116
    @Test
    fun issue104_tc116_headingLandsBelowThePinnedStrip() {
        val scroll = ScrollState(0)
        setPage(seriesBookAt(position = 1), overviewSlots(), scroll)
        tapStageLine()
        assertLanded(scroll)
    }

    // TC-116 (no filler: the same check on a page that cannot scroll far)
    @Test
    fun issue104_tc116_headingLandsWithoutAFiller() {
        val scroll = ScrollState(0)
        setPage(seriesBookAt(position = 1), BookDetailSlots(overviewSections = listOf(SeriesFanSection)), scroll)
        tapStageLine()
        assertLanded(scroll)
    }

    // TC-117
    @Test
    fun issue104_tc117_switchesToOverview() {
        val scroll = ScrollState(0)
        setPage(seriesBookAt(position = 1), overviewSlots(), scroll)
        rule.tabNode(BookDetailTab.Files).performClick()
        rule.waitForIdle()
        tapStageLine()
        rule.tabNode(BookDetailTab.Overview).assertIsSelected()
        assertLanded(scroll)
    }
}
