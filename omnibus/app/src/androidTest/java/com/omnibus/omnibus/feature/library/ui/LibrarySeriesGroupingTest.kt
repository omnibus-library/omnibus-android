package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.library.LibraryUIState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The group-by-series switch, the folded stacks and the load-more reports they drive. */
class LibrarySeriesGroupingTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tc235_21_loadsMoreAgainWhenAPageOnlyJoinsAStack() {
        val reported = mutableListOf<Int>()
        val state = mutableStateOf(
            groupedState(
                seriesBook(1L, seriesId = 10L, series = DUNE, index = "1"),
                seriesBook(2L, seriesId = 10L, series = DUNE, index = "2"),
                book(3L),
            ),
        )
        setLibrary(state, onLoadMore = { reported += it })
        rule.waitForIdle()
        // Both grid items fit on screen, so the last item is visible and nothing lies after it.
        assertEquals(0, reported.last())
        val before = reported.size

        rule.runOnIdle {
            state.value = state.value.copy(books = state.value.books + seriesBook(4L, seriesId = 10L, series = DUNE, index = "3"))
        }
        rule.waitForIdle()

        assertTrue(reported.size > before)
    }

    @Test
    fun tc235_22_pillIsOneSwitchNamedGroupBySeries() {
        setLibrary(mutableStateOf(LibraryUIState(books = listOf(book(1L), book(2L)))))

        rule.onNodeWithTag(LibraryTestTags.GROUP_SWITCH)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertContentDescriptionEquals(GROUP_LABEL)
    }

    @Test
    fun tc235_22_pillIsOffWhenGroupingIsOff() {
        setLibrary(mutableStateOf(LibraryUIState(books = listOf(book(1L), book(2L)))))

        rule.onNodeWithTag(LibraryTestTags.GROUP_SWITCH).assertIsOff()
    }

    @Test
    fun tc235_22_pillIsOnWhenGroupingIsOn() {
        setLibrary(mutableStateOf(groupedState(book(1L), book(2L))))

        rule.onNodeWithTag(LibraryTestTags.GROUP_SWITCH).assertIsOn()
    }

    @Test
    fun tc235_22_tappingPillCallsToggle() {
        var toggles = 0
        setLibrary(
            mutableStateOf(LibraryUIState(books = listOf(book(1L), book(2L)))),
            onToggleGroupBySeries = { toggles++ },
        )

        rule.onNodeWithTag(LibraryTestTags.GROUP_SWITCH).performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun tc235_22_pillSitsLeftOfFilterButton() {
        setLibrary(mutableStateOf(LibraryUIState(books = listOf(book(1L), book(2L)))))

        val pill = rule.onNodeWithTag(LibraryTestTags.GROUP_SWITCH).getUnclippedBoundsInRoot()
        val filter = rule.onNodeWithTag(LibraryTestTags.FILTER_BUTTON).getUnclippedBoundsInRoot()

        assertTrue(pill.right <= filter.left)
    }

    @Test
    fun tc235_23_stackDescribesSeriesNameAndBookCount() {
        setLibrary(
            mutableStateOf(
                groupedState(
                    seriesBook(1L, seriesId = 10L, series = DUNE, index = "1"),
                    seriesBook(2L, seriesId = 10L, series = DUNE, index = "2"),
                    seriesBook(3L, seriesId = 10L, series = DUNE, index = "3"),
                    seriesBook(4L, seriesId = 20L, series = SOLO, index = "1"),
                ),
            ),
        )

        rule.onNodeWithTag(LibraryTestTags.stack(10L)).assertContentDescriptionEquals("$DUNE, 3 books")
        rule.onNodeWithTag(LibraryTestTags.stack(20L)).assertContentDescriptionEquals("$SOLO, 1 book")
    }

    @Test
    fun tc235_23_unfoldActionIsLabelledUnfoldSeries() {
        setLibrary(
            mutableStateOf(groupedState(seriesBook(1L, seriesId = 10L, series = DUNE, index = "1"))),
        )

        val action = rule.onNodeWithTag(LibraryTestTags.stack(10L)).fetchSemanticsNode()
            .config.getOrNull(SemanticsActions.OnClick)

        assertEquals(UNFOLD_LABEL, action?.label)
    }

    @Test
    fun tc235_23_performingUnfoldCallsOnUnfoldSeries() {
        val unfolded = mutableListOf<Long>()
        setLibrary(
            mutableStateOf(groupedState(seriesBook(1L, seriesId = 10L, series = DUNE, index = "1"))),
            onUnfoldSeries = { unfolded += it },
        )

        rule.onNodeWithTag(LibraryTestTags.stack(10L)).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(10L), unfolded)
    }

    @Test
    fun tc235_29_memberWithAnIndexShowsItsVolume() {
        setLibrary(mutableStateOf(unfoldedDune()))

        rule.onNode(hasText("VOL. 1") and hasAnyAncestor(hasTestTag(LibraryTestTags.cell(1L))), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun tc235_29_memberWithoutAnIndexShowsItsAuthor() {
        setLibrary(mutableStateOf(unfoldedDune()))

        rule.onNode(hasText(AUTHOR_2) and hasAnyAncestor(hasTestTag(LibraryTestTags.cell(2L))), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun tc235_29_memberWithoutAnIndexHasNoVolumeLine() {
        setLibrary(mutableStateOf(unfoldedDune()))

        rule.onAllNodes(
            hasText("VOL.", substring = true) and hasAnyAncestor(hasTestTag(LibraryTestTags.cell(2L))),
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    @Test
    fun tc235_29_summaryNamesTheSeries() {
        setLibrary(mutableStateOf(unfoldedDune()))

        rule.onNode(hasText(DUNE) and hasAnyAncestor(hasTestTag(LibraryTestTags.seriesSummary(10L))), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun tc235_29_summaryHasASeriesPageButton() {
        setLibrary(mutableStateOf(unfoldedDune()))

        rule.onNode(hasText("Series page →") and hasAnyAncestor(hasTestTag(LibraryTestTags.seriesSummary(10L))), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun tc235_30_foldUpCallsOnFoldUpSeries() {
        val folded = mutableListOf<Long>()
        setLibrary(mutableStateOf(unfoldedDune()), onFoldUpSeries = { folded += it })

        rule.onNodeWithTag(LibraryTestTags.foldUp(10L)).performClick()

        assertEquals(listOf(10L), folded)
    }

    @Test
    fun tc235_30_seriesPageCallsOnOpenSeries() {
        val opened = mutableListOf<Pair<Long, String>>()
        setLibrary(mutableStateOf(unfoldedDune()), onOpenSeries = { id, name -> opened += id to name })

        rule.onNodeWithTag(LibraryTestTags.seriesPage(10L)).performClick()

        assertEquals(listOf(10L to DUNE), opened)
    }

    @Test
    fun tc235_30_memberCellCallsOnOpenBook() {
        val opened = mutableListOf<String>()
        setLibrary(mutableStateOf(unfoldedDune()), onOpenBook = { opened += it })

        rule.onNodeWithTag(LibraryTestTags.cell(1L)).performClick()

        assertEquals(listOf("uuid-1"), opened)
    }

    private fun setLibrary(
        uiState: State<LibraryUIState>,
        onLoadMore: (Int) -> Unit = {},
        onToggleGroupBySeries: () -> Unit = {},
        onUnfoldSeries: (Long) -> Unit = {},
        onFoldUpSeries: (Long) -> Unit = {},
        onOpenSeries: (Long, String) -> Unit = { _, _ -> },
        onOpenBook: (String) -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    LibraryScreenContent(
                        uiState = uiState.value,
                        onRetry = {},
                        onRefresh = {},
                        onLoadMore = onLoadMore,
                        onOpenBook = onOpenBook,
                        onAddBooks = {},
                        onTransientErrorShown = {},
                        onToggleGroupBySeries = onToggleGroupBySeries,
                        onUnfoldSeries = onUnfoldSeries,
                        onFoldUpSeries = onFoldUpSeries,
                        onOpenSeries = onOpenSeries,
                    )
                }
            }
        }
    }

    private companion object {
        const val DUNE = "Dune Chronicles"
        const val SOLO = "Solo"
        const val GROUP_LABEL = "Group by series"
        const val UNFOLD_LABEL = "Unfold series"
        const val AUTHOR_2 = "Author 2"

        fun groupedState(vararg books: Book, unfolded: Set<Long> = emptySet()) = LibraryUIState(
            books = books.toList(),
            groupBySeries = true,
            unfoldedSeries = unfolded,
        )

        /** Dune Chronicles unfolded: book 1 at index "1", then book 2 with no index. */
        fun unfoldedDune() = groupedState(
            seriesBook(1L, seriesId = 10L, series = DUNE, index = "1"),
            book(2L).copy(seriesId = 10L, series = DUNE),
            unfolded = setOf(10L),
        )

        /** A book with a uuid of `uuid-<id>` and no cover, so no image loads in these tests. */
        fun book(id: Long) = Book(
            id = id,
            uniqueIdentifier = "uuid-$id",
            filename = "book-$id.epub",
            title = "Book $id",
            creators = listOf(Creator("Author $id", null, null, null)),
            coverUrl = null,
            accent = null,
            formats = listOf("epub"),
            series = null,
            seriesIndex = null,
            hasPhysical = false,
            addedAt = null,
            lastInteractedAt = null,
            bookFiles = emptyList(),
        )

        /** A book of series [seriesId] named [series], at [index] in the series. */
        fun seriesBook(id: Long, seriesId: Long, series: String, index: String) = book(id).copy(
            seriesId = seriesId,
            series = series,
            seriesIndex = index,
        )
    }
}
