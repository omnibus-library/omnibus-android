package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.scrollTo
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailScreenContent
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A fixed reading bar, so the screen tests do not need Hilt. */
private val TestBarSection = BookDetailSection { ctx, modifier ->
    BookActionBar(
        model = ActionBarModel.Primary(ActionLabel.ContinueReading(4), 0.4f, BookDetailDestination.Reader(ctx.book.uuid)),
        colors = actionBarColors(ctx.book),
        onNavigate = ctx.onNavigate,
        modifier = modifier,
        reducedMotion = true,
    )
}

class BookActionBarSectionTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-56
    @Test
    fun issue98_tc56_sectionShowsNoCopyWithoutHilt() {
        val recorder = SectionContextRecorder()
        val physical = mutableStateOf(true)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                // A book with no readable copy: the gate renders the note before any ViewModel, so no Hilt is needed.
                val book = testDetailBook(formats = emptyList()).copy(hasPhysical = physical.value)
                BookActionBarSection.Content(testSectionContext(book = book, recorder = recorder), Modifier.fillMaxWidth())
            }
        }

        rule.onNodeWithTag(ActionBarTestTags.NO_COPY)
            .assertTextEquals(detailText(R.string.book_detail_actionbar_physical_copy))
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(0)
        rule.onNodeWithTag(ActionBarTestTags.NO_COPY).performClick()
        rule.waitForIdle()
        assertTrue(recorder.navigations.isEmpty())

        rule.runOnIdle { physical.value = false }
        rule.onNodeWithTag(ActionBarTestTags.NO_COPY)
            .assertTextEquals(detailText(R.string.book_detail_actionbar_no_copy))
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(0)
    }

    // TC-57
    @Test
    fun issue98_tc57_barStaysPinnedWhileThePageScrolls() {
        val scroll = ScrollState(0)
        rule.setDetailScreen(
            BookDetailUIState(book = testDetailBook(), isLoading = false),
            slots = BookDetailSlots(actionBar = listOf(TestBarSection)),
            scrollState = scroll,
        )
        rule.waitForIdle()
        assertTrue("the page must scroll", scroll.maxValue > 0)

        val before = rule.onNodeWithTag(ActionBarTestTags.BUTTON).bounds()
        rule.scrollTo(scroll)
        val after = rule.onNodeWithTag(ActionBarTestTags.BUTTON).bounds()
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.bottom, after.bottom, 1f)

        val actionArea = rule.onNodeWithTag(BookDetailTestTags.ACTION_AREA).bounds()
        assertTrue(after.left >= actionArea.left && after.right <= actionArea.right)
        assertTrue(after.bottom <= actionArea.bottom + 1f)
        assertEquals(rule.onRoot().bounds().bottom, actionArea.bottom, 1f)
        assertTrue(rule.onNodeWithTag(BookDetailTestTags.TAB_AREA).bounds().bottom <= actionArea.top + 1f)
    }

    // TC-58
    @Test
    fun issue98_tc58_barSitsAboveTheTabBar() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.fillMaxSize()) {
                    BookDetailScreenContent(
                        uiState = BookDetailUIState(book = testDetailBook(), isLoading = false),
                        onNavigate = {},
                        onClose = {},
                        onRetry = {},
                        onRefresh = {},
                        onReloadBook = {},
                        onBookChanged = {},
                        onTransientErrorShown = {},
                        modifier = Modifier.weight(1f),
                        slots = BookDetailSlots(actionBar = listOf(TestBarSection)),
                    )
                    Box(Modifier.fillMaxWidth().testTag("tabbar")) { Text("Tabs") }
                }
            }
        }

        val bar = rule.onNodeWithTag(ActionBarTestTags.BUTTON)
        bar.assertIsDisplayed()
        val tabBar = rule.onNodeWithTag("tabbar").bounds()
        val root = rule.onRoot().bounds()
        val barBounds = bar.bounds()
        assertTrue("bar bottom ${barBounds.bottom} above tab bar top ${tabBar.top}", barBounds.bottom <= tabBar.top + 1f)
        assertTrue(barBounds.left >= root.left && barBounds.right <= root.right)
        rule.onNodeWithTag("tabbar").assertIsDisplayed()
    }

    // TC-59
    @Test
    fun issue98_tc59_readPillIsAbsentWhenTheSlotIsFilled() {
        val book = mutableStateOf(testDetailBook(formats = listOf("epub")))
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDetailScreenContent(
                    uiState = BookDetailUIState(book = book.value, isLoading = false),
                    onNavigate = {},
                    onClose = {},
                    onRetry = {},
                    onRefresh = {},
                    onReloadBook = {},
                    onBookChanged = {},
                    onTransientErrorShown = {},
                    modifier = Modifier.fillMaxSize(),
                    slots = BookDetailSlots(actionBar = listOf(TestBarSection)),
                )
            }
        }
        rule.onAllNodesWithTag(BookDetailTestTags.READ_PILL).assertCountEquals(0)
        rule.onNodeWithTag(BookDetailTestTags.ACTION_AREA).assertIsDisplayed()

        rule.runOnIdle { book.value = testDetailBook(formats = emptyList()) }
        rule.onAllNodesWithTag(BookDetailTestTags.READ_PILL).assertCountEquals(0)
        rule.onNodeWithTag(BookDetailTestTags.ACTION_AREA).assertIsDisplayed()
    }

    // TC-60
    @Test
    fun issue98_tc60_readPillIsPresentWhenTheSlotIsEmpty() {
        rule.setDetailScreen(BookDetailUIState(book = testDetailBook(formats = listOf("epub")), isLoading = false), slots = BookDetailSlots())
        rule.onNodeWithTag(BookDetailTestTags.READ_PILL).assertIsDisplayed()
        rule.onAllNodesWithTag(ActionBarTestTags.BUTTON).assertCountEquals(0)
    }
}
