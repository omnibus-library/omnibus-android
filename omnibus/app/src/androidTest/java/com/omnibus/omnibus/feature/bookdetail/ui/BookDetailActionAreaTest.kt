package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
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

class BookDetailActionAreaTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-110
    @Test
    fun issue93_tc110_readPillOpensTheReader() {
        val book = testDetailBook(formats = listOf("epub"))
        val navigations = mutableListOf<BookDetailDestination>()
        rule.setDetailScreen(
            BookDetailUIState(book = book, isLoading = false),
            onNavigate = { navigations += it },
        )

        val read = rule.onNodeWithText(detailText(R.string.book_detail_read))
        read.assertIsDisplayed()
        rule.onNodeWithTag(BookDetailTestTags.READ_PILL).assertHeightIsAtLeast(52.dp)
        val root = rule.onRoot().bounds()
        val pill = rule.onNodeWithTag(BookDetailTestTags.READ_PILL).bounds()
        assertTrue(pill.width > root.width * 0.8f)
        val actionArea = rule.onNodeWithTag(BookDetailTestTags.ACTION_AREA).bounds()
        assertEquals(root.bottom, actionArea.bottom, 1f)
        assertTrue(actionArea.top > rule.onNodeWithTag(BookDetailTestTags.TITLE).bounds().bottom)

        read.performClick()
        rule.waitForIdle()
        assertEquals(listOf(BookDetailDestination.Reader(book.uuid)), navigations)
    }

    // TC-111
    @Test
    fun issue93_tc111_pillOnlyForEpubWithNoActionBarSection() {
        val read = detailText(R.string.book_detail_read)
        val state = mutableStateOf(BookDetailUIState(book = testDetailBook(formats = listOf("pdf")), isLoading = false))
        val slots = mutableStateOf(BookDetailSlots())
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDetailScreenContent(
                    uiState = state.value,
                    onNavigate = {},
                    onClose = {},
                    onRetry = {},
                    onRefresh = {},
                    onReloadBook = {},
                    onBookChanged = {},
                    onTransientErrorShown = {},
                    modifier = Modifier.fillMaxSize(),
                    slots = slots.value,
                )
            }
        }
        rule.onAllNodesWithText(read).assertCountEquals(0)

        rule.runOnIdle { state.value = BookDetailUIState() }
        rule.waitForIdle()
        rule.onAllNodesWithText(read).assertCountEquals(0)

        rule.runOnIdle {
            state.value = BookDetailUIState(book = testDetailBook(formats = listOf("epub")), isLoading = false)
            slots.value = BookDetailSlots(
                actionBar = listOf(BookDetailSection { _, modifier ->
                    Text(text = "fake-action", modifier = modifier.testTag("fake-action"))
                }),
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("fake-action").assertIsDisplayed()
        rule.onAllNodesWithText(read).assertCountEquals(0)
    }
}
