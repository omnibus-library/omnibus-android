package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.overview.description.BookDescriptionSection
import com.omnibus.omnibus.feature.bookdetail.scrollTo
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailScreenContent
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The strip pinned under the top bar on the full page, with a real scroll. */
class BookTabPinningTest {

    @get:Rule
    val rule = createComposeRule()

    private val linkedBook: Book = testDetailBook(creators = listOf(Creator("Ann Lee", "author", null, null)))

    private fun ComposeContentTestRule.stripTop(): Float = onNodeWithTag(BookDetailTabsTestTags.STRIP).bounds().top

    private fun ComposeContentTestRule.screen(scroll: ScrollState, slots: BookDetailSlots, book: Book = linkedBook) {
        setDetailScreen(BookDetailUIState(book = book, isLoading = false), slots = slots, scrollState = scroll)
        waitForIdle()
    }

    private fun allFakes(): BookDetailSlots = BookDetailSlots(
        overviewSections = listOf(tallFakeSection("fake-overview")),
        reviewsSections = listOf(fakeSection("fake-reviews")),
        highlightsSections = listOf(fakeSection("fake-highlights")),
        filesSections = listOf(tallFakeSection("fake-files")),
    )

    // TC-43
    @Test
    fun issue94_tc43_pinnedStripIsStillTappable() {
        val scroll = ScrollState(0)
        rule.screen(scroll, allFakes())
        rule.scrollTo(scroll)
        val pinnedTop = rule.stripTop()

        rule.tabNode(BookDetailTab.Reviews).performClick()
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Reviews).assertIsSelected()
        rule.onNodeWithTag("fake-reviews").assertExists()
        assertEquals(pinnedTop, rule.stripTop(), 1f)

        rule.tabNode(BookDetailTab.Files).performClick()
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Files).assertIsSelected()
        rule.onNodeWithTag("fake-files").assertExists()
        assertEquals(pinnedTop, rule.stripTop(), 1f)
    }

    // TC-84
    @Test
    fun issue94_tc84_contextStaysOneObjectAcrossScrollAndTabChanges() {
        val seenStart = mutableListOf<BookDetailSectionContext>()
        val seenPanel = mutableListOf<BookDetailSectionContext>()
        val slots = BookDetailSlots(
            stageStatusStart = listOf(BookDetailSection { ctx, modifier ->
                SideEffect { seenStart += ctx }
                Text("recorder", modifier = modifier.testTag("recorder"))
            }),
            overviewSections = listOf(BookDetailSection { ctx, modifier ->
                SideEffect { seenPanel += ctx }
                Box(modifier.fillMaxWidth().aspectRatio(0.25f).testTag("tall"))
            }),
            reviewsSections = listOf(BookDetailSection { ctx, modifier ->
                SideEffect { seenPanel += ctx }
                Text("reviews", modifier = modifier.testTag("reviews"))
            }),
        )
        val reloadKey = mutableIntStateOf(0)
        val scroll = ScrollState(0)
        val book = testDetailBook()
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDetailScreenContent(
                    uiState = BookDetailUIState(book = book, isLoading = false, reloadKey = reloadKey.intValue),
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
                )
            }
        }
        rule.waitForIdle()
        val first = seenStart.first()

        rule.scrollTo(scroll)
        rule.tabNode(BookDetailTab.Reviews).performClick()
        rule.waitForIdle()
        assertTrue(seenStart.all { it === first })
        assertTrue(seenPanel.isNotEmpty() && seenPanel.all { it === first })

        rule.runOnIdle { reloadKey.intValue = 1 }
        rule.waitForIdle()
        assertTrue(seenStart.last() !== first)
        assertEquals(1, seenStart.last().reloadKey)
    }

    // TC-90
    @Test
    fun issue94_fullScreenExposesFourTabsAndNoRibbonNode() {
        val book = testDetailBook().copy(description = "word ".repeat(120))
        rule.setDetailScreen(
            BookDetailUIState(book = book, isLoading = false),
            slots = BookDetailSlots(overviewSections = listOf(BookDescriptionSection)),
        )
        rule.waitForIdle()
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(4)
        rule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        ).assertCountEquals(1)
        rule.onAllNodesWithTag(BookDetailTabsTestTags.RIBBON, useUnmergedTree = true).assertCountEquals(1)
        val ribbonConfig = rule.onNodeWithTag(BookDetailTabsTestTags.RIBBON, useUnmergedTree = true).fetchSemanticsNode().config
        assertTrue(ribbonConfig.contains(SemanticsProperties.HideFromAccessibility))
        val toggle = rule.onNodeWithTag(com.omnibus.omnibus.feature.bookdetail.overview.description.BookDescriptionTestTags.DESCRIPTION_TOGGLE, useUnmergedTree = true)
            .fetchSemanticsNode()
        assertEquals(Role.Button, toggle.config[SemanticsProperties.Role])
    }
}
