package com.omnibus.omnibus.feature.shelves.detail.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assert
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.shelves.ShelfDetail
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.ShelfRules
import com.omnibus.omnibus.feature.library.ui.LibraryTestTags
import com.omnibus.omnibus.feature.shelves.detail.ShelfDetailUIState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Callbacks, semantics and the fading title of the shelf detail screen, on hand-built state. The screen's look is
 * covered by ShelfDetailScreenshotTest. Nothing here fixes a window size: books are many enough to scroll on any phone.
 */
class ShelfDetailScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun issue232_tc16_aBookCellOpensTheBook() {
        val plain = List(3) { plainBook(it) }
        var opened: String? = null
        setScreen(
            uiState = stateWith(books = plain),
            onOpenBook = { opened = it },
        )

        rule.onNodeWithTag(ShelfDetailTestTags.GRID)
            .performScrollToNode(hasTestTag(LibraryTestTags.cell(plain.first().id)))
        rule.onNodeWithTag(LibraryTestTags.cell(plain.first().id)).performClick()

        rule.waitUntil { opened != null }
        assertEquals(plain.first().uuid, opened)
    }

    @Test
    fun issue232_tc16_aStackCellUnfoldsInPlaceAndDoesNotOpenABook() {
        val series = seriesBooks(seriesId = 42L)
        var unfolded: Long? = null
        var opened: String? = null
        setScreen(
            uiState = stateWith(books = series),
            onUnfoldSeries = { unfolded = it },
            onOpenBook = { opened = it },
        )

        rule.onNodeWithTag(ShelfDetailTestTags.GRID)
            .performScrollToNode(hasTestTag(LibraryTestTags.stack(42L)))
        rule.onNodeWithTag(LibraryTestTags.stack(42L)).performClick()

        rule.waitUntil { unfolded != null }
        assertEquals(42L, unfolded)
        assertNull(opened)
    }

    @Test
    fun issue232_tc17_theTitleAppearsOnceTheHeroHasScrolledAway() {
        val books = List(60) { plainBook(it) }
        setScreen(uiState = stateWith(books = books), reducedMotion = true)
        rule.waitForIdle()

        rule.onAllNodesWithTag(ShelfDetailTestTags.BAR_TITLE).assertCountEquals(0)

        rule.onNodeWithTag(ShelfDetailTestTags.GRID).performScrollToIndex(books.lastIndex)
        rule.waitForIdle()
        rule.onNodeWithTag(ShelfDetailTestTags.BAR_TITLE).assertPresent()

        rule.onNodeWithTag(ShelfDetailTestTags.GRID).performScrollToIndex(0)
        rule.waitForIdle()
        rule.onAllNodesWithTag(ShelfDetailTestTags.BAR_TITLE).assertCountEquals(0)
    }

    @Test
    fun issue232_tc17_theBackButtonIsThereWhileTheTitleFades() {
        val books = List(60) { plainBook(it) }
        setScreen(uiState = stateWith(books = books), reducedMotion = true)
        rule.waitForIdle()

        rule.onNodeWithTag(ShelfDetailTestTags.BACK).assertPresent()
        rule.onNodeWithTag(ShelfDetailTestTags.GRID).performScrollToIndex(books.lastIndex)
        rule.waitForIdle()
        rule.onNodeWithTag(ShelfDetailTestTags.BACK).assertPresent()
    }

    @Test
    fun issue232_tc18_theHeroNameIsAHeadingAndTheMetaLineReadsAsOneNode() {
        val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val shelf = shelfOf(ShelfKind.Smart, ShelfVisibility.Public, bookCount = 5L)
        setScreen(uiState = stateWith(shelf = shelf, rules = smartRules(), description = "Long ones"))
        rule.waitForIdle()

        rule.onNodeWithTag(ShelfDetailTestTags.HERO_NAME).assert(isHeading())
        val meta = listOf(
            res.getQuantityString(R.plurals.library_shelves_book_count, 5, 5),
            res.getString(R.string.shelves_kind_smart),
            res.getString(R.string.shelves_visibility_public),
        ).joinToString(", ")
        rule.onNodeWithContentDescription(meta).assertPresent()
        rule.onNodeWithText("Long ones").assertIsDisplayed()
    }

    @Test
    fun issue232_tc18_theRulesShowTheirMatchModeAndAChipEach() {
        val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
        setScreen(uiState = stateWith(shelf = shelfOf(ShelfKind.Smart, ShelfVisibility.Private), rules = smartRules()))
        rule.waitForIdle()

        rule.onNodeWithText(res.getString(R.string.shelf_detail_matches_all), substring = true, ignoreCase = true)
            .assertPresent()
        rule.onNodeWithText("Poetry", substring = true, ignoreCase = true).assertPresent()
    }

    @Test
    fun issue232_tc18_theMosaicPlateIsHiddenFromTalkBack() {
        val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
        setScreen(uiState = stateWith(shelf = shelfOf(ShelfKind.Smart, ShelfVisibility.Private), rules = null))
        rule.waitForIdle()

        rule.onAllNodesWithText(res.getString(R.string.shelves_empty_ledge_smart), ignoreCase = true)
            .assertCountEquals(0)
    }

    private fun setScreen(
        uiState: ShelfDetailUIState,
        onOpenBook: (String) -> Unit = {},
        onUnfoldSeries: (Long) -> Unit = {},
        reducedMotion: Boolean = false,
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    ShelfDetailContent(
                        uiState = uiState,
                        onBack = {},
                        onOpenBook = onOpenBook,
                        onOpenSeries = { _, _ -> },
                        onRefresh = {},
                        onRetry = {},
                        onUnfoldSeries = onUnfoldSeries,
                        onFoldUpSeries = {},
                        onTransientErrorShown = {},
                        gridState = rememberLazyGridState(),
                        reducedMotion = reducedMotion,
                    )
                }
            }
        }
    }

    private fun stateWith(
        shelf: Shelf = shelfOf(ShelfKind.Manual, ShelfVisibility.Private),
        books: List<Book>? = null,
        rules: ShelfRules? = null,
        description: String? = null,
    ) = ShelfDetailUIState(
        shelfId = shelf.id,
        preview = ShelfPreview(shelf, emptyList()),
        detail = ShelfDetail(shelf, description, rules ?: ShelfRules(null, emptyList())),
        books = books,
        unfilteredBooks = books,
    )

    private fun shelfOf(
        kind: ShelfKind,
        visibility: ShelfVisibility,
        bookCount: Long = 60L,
    ) = Shelf(
        id = 1L,
        ownerUserId = VIEWER_ID,
        ownerUsername = "Hunter",
        kind = kind,
        name = "Weekend reads",
        visibility = visibility,
        accent = null,
        bookCount = bookCount,
    )

    private fun smartRules() = ShelfRules(
        matchMode = ShelfMatchMode.All,
        rules = listOf(ShelfRule(ShelfRuleField.Tag, ShelfRuleOp.Is, "Poetry")),
    )

    private fun plainBook(index: Int): Book = bookWith(id = index + 1L, seriesId = null, seriesIndex = null)

    private fun seriesBooks(seriesId: Long): List<Book> = listOf(
        bookWith(id = 1001L, seriesId = seriesId, seriesIndex = "1"),
        bookWith(id = 1002L, seriesId = seriesId, seriesIndex = "2"),
    ) + List(60) { plainBook(it) }

    private fun bookWith(id: Long, seriesId: Long?, seriesIndex: String?): Book = Book(
        id = id,
        uniqueIdentifier = "uuid-$id",
        filename = "book-$id.epub",
        title = "Book $id",
        creators = emptyList(),
        coverUrl = null,
        accent = null,
        formats = listOf("epub"),
        series = seriesId?.let { "Series $it" },
        seriesIndex = seriesIndex,
        hasPhysical = false,
        addedAt = null,
        lastInteractedAt = null,
        bookFiles = emptyList(),
        seriesId = seriesId,
    )

    /** Fails when no node matches: the node is looked up, and the lookup throws when there is none. */
    private fun SemanticsNodeInteraction.assertPresent(): SemanticsNodeInteraction {
        fetchSemanticsNode("No node matches this selector")
        return this
    }

    private companion object {
        const val VIEWER_ID = 7L
    }
}
