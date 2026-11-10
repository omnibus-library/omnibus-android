package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.forcedTwoPanes
import com.omnibus.omnibus.feature.bookdetail.scrollTo
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.tabs.tallFakeSection
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailScreenContent
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookDetailTopBarTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    // TC-104
    @Test
    fun issue93_tc104_pageDrawsUnderTheStatusBar() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        var statusTop = 0
        rule.setContent {
            statusTop = WindowInsets.statusBars.getTop(LocalDensity.current)
            OmnibusTheme(darkTheme = true) {
                BookDetailScreenContent(
                    uiState = BookDetailUIState(book = testDetailBook(), isLoading = false),
                    onNavigate = {},
                    onClose = {},
                    onRetry = {},
                    onRefresh = {},
                    onReloadBook = {},
                    onBookChanged = {},
                    onTransientErrorShown = {},
                    modifier = Modifier.fillMaxSize(),
                    slots = BookDetailSlots(),
                    scrollState = ScrollState(0),
                )
            }
        }
        rule.waitForIdle()

        val root = rule.onRoot().bounds()
        val stage = rule.onNodeWithTag(BookDetailTestTags.STAGE).bounds()
        val disc = rule.onNodeWithContentDescription(detailText(R.string.navigation_back)).bounds()
        val cover = rule.onNodeWithTag(BookDetailTestTags.COVER).bounds()
        assertEquals(root.top, stage.top, 1f)
        assertTrue("disc at ${disc.top}, status bar $statusTop", disc.top >= statusTop - 1f)
        assertTrue(cover.top > stage.top)
    }

    // TC-98
    @Test
    fun issue93_tc98_barFillsPastTheCoverAndClears() {
        val title = "The Dispossessed and the Other Side of the Sky"
        val scroll = ScrollState(0)
        rule.setDetailScreen(
            BookDetailUIState(book = testDetailBook(title = title), isLoading = false),
            scrollState = scroll,
        )
        rule.onNodeWithTag(BookDetailTestTags.TOP_BAR)
            .assert(SemanticsMatcher.expectValue(BookDetailTopBarFilledKey, false))

        rule.scrollTo(scroll)

        rule.onNodeWithTag(BookDetailTestTags.TOP_BAR)
            .assert(SemanticsMatcher.expectValue(BookDetailTopBarFilledKey, true))

        rule.scrollTo(scroll, 0)
        rule.onNodeWithTag(BookDetailTestTags.TOP_BAR)
            .assert(SemanticsMatcher.expectValue(BookDetailTopBarFilledKey, false))
    }

    // TC-43
    @Test
    fun issue211_tc43_twoPaneTopBarNeverFillsOrShowsTheTitle() {
        val stage = ScrollState(0)
        val panel = ScrollState(0)
        rule.setDetailScreen(
            BookDetailUIState(book = testDetailBook(), isLoading = false),
            slots = BookDetailSlots(
                stageProgress = listOf(tallFakeSection("fake-stage")),
                overviewSections = listOf(tallFakeSection("fake-overview")),
            ),
            stageScrollState = stage,
            panelScrollState = panel,
            paneSplit = forcedTwoPanes,
        )
        assertTrue("stage needs range", stage.maxValue > 0)
        rule.scrollTo(stage)
        assertTrue("panel needs range", panel.maxValue > 0)
        rule.scrollTo(panel)

        rule.onAllNodesWithTag(BookDetailTestTags.BAR_TITLE).assertCountEquals(0)
        rule.onNodeWithTag(BookDetailTestTags.TOP_BAR)
            .assert(SemanticsMatcher.expectValue(BookDetailTopBarFilledKey, false))
    }

    // TC-44
    @Test
    fun issue211_tc44_backAndMoreSitAtTheTopCornersAboveThePanes() {
        rule.setDetailScreen(
            BookDetailUIState(book = testDetailBook(), isLoading = false),
            slots = BookDetailSlots(topBarActions = listOf(moreAction(onTap = {}))),
            paneSplit = forcedTwoPanes,
        )

        val back = rule.onNodeWithContentDescription(detailText(R.string.navigation_back)).bounds()
        val more = rule.onNodeWithTag(MORE_TAG).bounds()
        val scroll = rule.onNodeWithTag(BookDetailTestTags.SCROLL).bounds()
        assertTrue(back.left < more.left)
        assertTrue(back.left >= scroll.left - 1f)
        assertTrue(more.right <= scroll.right + 1f)
    }

    // TC-44
    @Test
    fun issue211_tc44_backAndMoreRespondToTaps() {
        var backTaps = 0
        var moreTaps = 0
        rule.setDetailScreen(
            BookDetailUIState(book = testDetailBook(), isLoading = false),
            slots = BookDetailSlots(topBarActions = listOf(moreAction(onTap = { moreTaps++ }))),
            onClose = { backTaps++ },
            paneSplit = forcedTwoPanes,
        )

        rule.onNodeWithContentDescription(detailText(R.string.navigation_back)).performClick()
        rule.onNodeWithTag(MORE_TAG).performClick()
        rule.waitForIdle()
        assertEquals(1, backTaps)
        assertEquals(1, moreTaps)
    }

    private fun moreAction(onTap: () -> Unit): BookDetailSection = BookDetailSection { _, modifier ->
        Box(modifier.size(44.dp).testTag(MORE_TAG).clickable(onClick = onTap))
    }

    private companion object {
        const val MORE_TAG = "book-detail-test-more"
    }
}
