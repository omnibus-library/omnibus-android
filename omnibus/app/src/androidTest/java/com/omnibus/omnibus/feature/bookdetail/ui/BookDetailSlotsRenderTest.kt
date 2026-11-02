package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.scrollTo
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookDetailSlotsRenderTest {

    @get:Rule
    val rule = createComposeRule()

    private fun fake(tag: String): BookDetailSection = BookDetailSection { _, modifier ->
        Text(text = tag, modifier = modifier.testTag(tag))
    }

    private val linkedBook: Book = testDetailBook(
        creators = listOf(Creator("Ann Lee", "author", null, null)),
    )

    // TC-114
    @Test
    fun issue93_tc114_stageSlotsRenderInPlace() {
        val slots = BookDetailSlots(
            stageAboveTitle = listOf(fake("fake-above")),
            stageStatusStart = listOf(fake("fake-start")),
            stageStatusEnd = listOf(fake("fake-end")),
            stageProgress = listOf(fake("fake-progress")),
            stageCoverOverlay = listOf(fake("fake-overlay")),
        )
        rule.setDetailScreen(BookDetailUIState(book = linkedBook, isLoading = false), slots = slots)

        val cover = rule.onNodeWithTag(BookDetailTestTags.COVER).bounds()
        val above = rule.onNodeWithTag("fake-above").bounds()
        val title = rule.onNodeWithTag(BookDetailTestTags.TITLE).bounds()
        assertTrue(above.top > cover.top)
        assertTrue(above.bottom <= title.top + 1f)

        val author = rule.onNodeWithTag(BookDetailTestTags.AUTHOR).bounds()
        val start = rule.onNodeWithTag("fake-start").bounds()
        val end = rule.onNodeWithTag("fake-end").bounds()
        val row = rule.onNodeWithTag(BookDetailTestTags.STATUS_ROW).bounds()
        assertTrue(start.top >= author.bottom - 1f)
        assertEquals(start.center.y, end.center.y, 2f)
        assertTrue(start.left < end.left)

        val progress = rule.onNodeWithTag("fake-progress").bounds()
        assertTrue(progress.top >= row.bottom - 1f)

        val overlay = rule.onNodeWithTag("fake-overlay").bounds()
        assertTrue(overlay.left >= cover.left - 1f && overlay.right <= cover.right + 1f)
        assertTrue(overlay.top >= cover.top - 1f && overlay.bottom <= cover.bottom + 1f)
    }

    // TC-115
    @Test
    fun issue93_tc115_topBarOverlayAndTabListSlots() {
        val slots = BookDetailSlots(
            topBarActions = listOf(fake("fake-top-action")),
            screenOverlays = listOf(fake("fake-overlay-screen")),
            overviewSections = listOf(fake("fake-overview")),
            reviewsSections = listOf(fake("fake-reviews")),
            highlightsSections = listOf(fake("fake-highlights")),
            filesSections = listOf(fake("fake-files")),
        )
        rule.setDetailScreen(BookDetailUIState(book = linkedBook, isLoading = false), slots = slots)

        val bar = rule.onNodeWithTag(BookDetailTestTags.TOP_BAR).bounds()
        val disc = rule.onNodeWithContentDescription(detailText(R.string.book_detail_back)).bounds()
        val action = rule.onNodeWithTag("fake-top-action").bounds()
        assertTrue(bar.contains(action.center))
        assertTrue(action.left >= disc.right)

        val root = rule.onRoot().bounds()
        rule.onNodeWithTag("fake-overlay-screen").assertIsDisplayed()
        assertTrue(root.contains(rule.onNodeWithTag("fake-overlay-screen").bounds().center))

        listOf("fake-overview", "fake-reviews", "fake-highlights", "fake-files").forEach {
            rule.onAllNodesWithTag(it).assertCountEquals(0)
        }
    }

    // TC-117
    @Test
    fun issue93_tc117_sectionsShareOneContextAndCallbacksForward() {
        val seenStart = mutableListOf<BookDetailSectionContext>()
        val seenTop = mutableListOf<BookDetailSectionContext>()
        val seenAction = mutableListOf<BookDetailSectionContext>()
        val navigations = mutableListOf<BookDetailDestination>()
        var closes = 0
        var reloads = 0
        val changed = mutableListOf<Book>()
        val replacement = testDetailBook(id = 2, title = "Replacement")

        val slots = BookDetailSlots(
            stageStatusStart = listOf(BookDetailSection { ctx, modifier ->
                SideEffect { seenStart += ctx }
                Row(modifier) {
                    Text(
                        text = "nav-author",
                        modifier = Modifier.testTag("nav-author").clickable {
                            ctx.onNavigate(BookDetailDestination.Author(7L, "A"))
                        },
                    )
                }
            }),
            topBarActions = listOf(BookDetailSection { ctx, modifier ->
                SideEffect { seenTop += ctx }
                Text(
                    text = "close-action",
                    modifier = modifier.testTag("close-action").clickable { ctx.onClose() },
                )
            }),
            actionBar = listOf(BookDetailSection { ctx, modifier ->
                SideEffect { seenAction += ctx }
                Column(modifier) {
                    Text(
                        text = "changed-action",
                        modifier = Modifier.testTag("changed-action").clickable { ctx.onBookChanged(replacement) },
                    )
                    Text(
                        text = "reload-action",
                        modifier = Modifier.testTag("reload-action").clickable { ctx.onReloadBook() },
                    )
                }
            }),
        )
        val book = testDetailBook()
        rule.setDetailScreen(
            BookDetailUIState(book = book, isLoading = false),
            slots = slots,
            onNavigate = { navigations += it },
            onClose = { closes++ },
            onReloadBook = { reloads++ },
            onBookChanged = { changed += it },
        )
        rule.waitForIdle()

        val context = seenStart.first()
        assertTrue(seenStart.all { it === context })
        assertTrue(seenTop.all { it === context })
        assertTrue(seenAction.all { it === context })
        assertEquals(book, context.book)
        assertEquals(0, context.reloadKey)
        assertEquals(BookDetailTab.Overview, context.tabs.selected)
        assertEquals(bookAccent(book.coverIdentity().tone, lightGround = false), context.accent)

        rule.onNodeWithTag("nav-author").performClick()
        rule.onNodeWithTag("close-action").performClick()
        rule.onNodeWithTag("changed-action").performClick()
        rule.onNodeWithTag("reload-action").performClick()
        rule.waitForIdle()

        assertEquals(listOf<BookDetailDestination>(BookDetailDestination.Author(7L, "A")), navigations)
        assertEquals(1, closes)
        assertEquals(listOf(replacement), changed)
        assertEquals(1, reloads)
    }

    // TC-118
    @Test
    fun issue93_tc118_contextStableUnderScrollNewOnReloadKey() {
        val seen = mutableListOf<BookDetailSectionContext>()
        val slots = BookDetailSlots(
            stageStatusStart = listOf(BookDetailSection { ctx, modifier ->
                SideEffect { seen += ctx }
                Text(text = "recorder", modifier = modifier.testTag("recorder"))
            }),
        )
        val reloadKey = mutableIntStateOf(0)
        val scroll = ScrollState(0)
        val book = testDetailBook()
        rule.setContent {
            com.omnibus.omnibus.ui.theme.OmnibusTheme(darkTheme = true) {
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
        val first = seen.first()

        rule.scrollTo(scroll)
        assertTrue("scrolled ${scroll.value}px", scroll.value > 0)
        assertTrue(seen.all { it === first })

        rule.runOnIdle { reloadKey.intValue = 1 }
        rule.waitForIdle()
        val last = seen.last()
        assertEquals(1, last.reloadKey)
        assertTrue(last !== first)
    }
}
