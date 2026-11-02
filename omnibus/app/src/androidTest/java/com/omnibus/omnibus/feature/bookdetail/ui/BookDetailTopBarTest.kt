package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.scrollTo
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
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
        val disc = rule.onNodeWithContentDescription(detailText(R.string.book_detail_back)).bounds()
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
        assertTrue("scrolled ${scroll.value}px", scroll.value > with(rule.density) { 300.dp.toPx() })
        rule.onNodeWithTag(BookDetailTestTags.TOP_BAR)
            .assert(SemanticsMatcher.expectValue(BookDetailTopBarFilledKey, true))

        val root = rule.onRoot().bounds()
        val barTitle = rule.onNodeWithTag(BookDetailTestTags.BAR_TITLE).bounds()
        val disc = rule.onNodeWithContentDescription(detailText(R.string.book_detail_back)).bounds()
        assertEquals(root.center.x, barTitle.center.x, with(rule.density) { 4.dp.toPx() })
        assertTrue(barTitle.left >= disc.right)
        rule.onAllNodesWithText(title).assertCountEquals(1)

        rule.scrollTo(scroll, 0)
        rule.onNodeWithTag(BookDetailTestTags.TOP_BAR)
            .assert(SemanticsMatcher.expectValue(BookDetailTopBarFilledKey, false))
    }
}
