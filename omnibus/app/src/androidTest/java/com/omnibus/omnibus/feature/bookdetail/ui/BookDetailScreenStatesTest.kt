package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.feature.bookdetail.BookDetailError
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class BookDetailScreenStatesTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-73
    @Test
    fun issue93_tc73_loadingShowsLoadingViewAndBackDisc() {
        rule.setDetailScreen(BookDetailUIState())

        rule.onNodeWithText(detailText(R.string.book_detail_loading)).assertIsDisplayed()
        rule.onNodeWithContentDescription(detailText(R.string.book_detail_back)).assertIsDisplayed()
        rule.onAllNodesWithText(detailText(R.string.book_detail_read)).assertCountEquals(0)
        rule.onNodeWithTag(BookDetailTestTags.TOP_BAR)
            .assert(SemanticsMatcher.expectValue(BookDetailTopBarFilledKey, false))
    }

    // TC-74
    @Test
    fun issue93_tc74_errorShowsMessageAndRetry() {
        val notFound = detailText(R.string.book_detail_error_not_found)
        val unreachable = detailText(R.string.library_error_unreachable)
        val state = mutableStateOf(BookDetailUIState(error = BookDetailError.NotFound, isLoading = false))
        var retries = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDetailScreenContent(
                    uiState = state.value,
                    onNavigate = {},
                    onClose = {},
                    onRetry = { retries++ },
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

        rule.onNodeWithText(detailText(R.string.book_detail_error_title)).assertIsDisplayed()
        rule.onNodeWithText(notFound).assertIsDisplayed()
        rule.onNodeWithContentDescription(detailText(R.string.book_detail_back)).assertIsDisplayed()

        rule.onNodeWithText(detailText(R.string.book_detail_retry)).performClick()
        rule.waitForIdle()
        assertEquals(1, retries)

        rule.runOnIdle {
            state.value = BookDetailUIState(error = BookDetailError.Failed(LibraryError.Unreachable), isLoading = false)
        }
        rule.waitForIdle()
        rule.onNodeWithText(unreachable).assertIsDisplayed()
        rule.onAllNodesWithText(notFound).assertCountEquals(0)
    }

    // TC-76
    @Test
    fun issue93_tc76_contentShowsCoverTitleAuthor() {
        val book = testDetailBook()
        var closes = 0
        rule.setDetailScreen(BookDetailUIState(book = book, isLoading = false), onClose = { closes++ })

        rule.onNodeWithText(book.displayTitle).assertIsDisplayed()
        rule.onNodeWithText(book.authorDisplay).assertIsDisplayed()
        rule.onNodeWithContentDescription(detailText(R.string.book_detail_cover_description, book.displayTitle))
            .assertIsDisplayed()
        rule.onAllNodesWithText(detailText(R.string.book_detail_loading)).assertCountEquals(0)
        rule.onAllNodesWithText(detailText(R.string.book_detail_error_title)).assertCountEquals(0)

        val root = rule.onRoot().bounds()
        val cover = rule.onNodeWithTag(BookDetailTestTags.COVER).bounds()
        val ratio = cover.height / cover.width
        assertTrue("cover is 2:3, was $ratio", abs(ratio - 1.5f) <= 1.5f * 0.02f)
        assertEquals(root.center.x, cover.center.x, with(rule.density) { 2.dp.toPx() })
        assertTrue(cover.width < root.width)

        val back = rule.onNodeWithContentDescription(detailText(R.string.book_detail_back))
        back.assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
        back.performClick()
        rule.waitForIdle()
        assertEquals(1, closes)
    }
}
