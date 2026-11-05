package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailScreenContent
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
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
