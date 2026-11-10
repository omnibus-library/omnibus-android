package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpRect
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.library.LibraryUIState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun tc258_20_singlesGlideToTheirUnfoldedCells() {
        val state = mutableStateOf(twoBookSeriesThenSingles(unfolded = false))
        rule.mainClock.autoAdvance = false
        setLibrary(state)
        rule.waitForIdle()
        val folded = rule.onNodeWithTag(LibraryTestTags.cell(3L)).getUnclippedBoundsInRoot()

        rule.runOnIdle { state.setUnfolded(10L, true) }
        rule.mainClock.advanceTimeBy(200)
        rule.waitForIdle()
        val midway = rule.onNodeWithTag(LibraryTestTags.cell(3L)).getUnclippedBoundsInRoot()

        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        val unfolded = rule.onNodeWithTag(LibraryTestTags.cell(3L)).getUnclippedBoundsInRoot()
        val horizontal = movesHorizontally(folded, unfolded)
        assertNotEquals(folded.along(horizontal), unfolded.along(horizontal))

        val between = (midway.along(horizontal) - folded.along(horizontal)) *
            (midway.along(horizontal) - unfolded.along(horizontal))
        assertTrue(between < 0f)

        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(unfolded, rule.onNodeWithTag(LibraryTestTags.cell(3L)).getUnclippedBoundsInRoot())
    }

    @Test
    fun tc258_21_aSortChangeDoesNotAnimate() {
        val state = mutableStateOf(twoBookSeriesThenSingles(unfolded = true))
        rule.mainClock.autoAdvance = false
        setLibrary(state)
        rule.waitForIdle()
        val before = boundsOf(cell(3L))

        rule.runOnIdle {
            val books = state.value.books
            state.value = state.value.copy(books = books.take(2) + books.drop(2).reversed())
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val afterFrame = boundsOf(*singles())
        assertNotEquals(before.getValue(cell(3L)), afterFrame.getValue(cell(3L)))

        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(afterFrame, boundsOf(*singles()))
    }

    @Test
    fun tc258_23_foldingMidUnfoldEndsFolded() {
        val state = mutableStateOf(twoBookSeriesThenSingles(unfolded = false))
        rule.mainClock.autoAdvance = false
        setLibrary(state, onFoldUpSeries = { state.setUnfolded(it, false) })
        rule.waitForIdle()

        rule.runOnIdle { state.setUnfolded(10L, true) }
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        rule.onNodeWithTag(LibraryTestTags.foldUp(10L)).performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()

        rule.onNodeWithTag(LibraryTestTags.stack(10L)).assertExists()
        rule.onAllNodesWithTag(LibraryTestTags.seriesSummary(10L)).assertCountEquals(0)
        rule.onAllNodesWithTag(LibraryTestTags.cell(1L)).assertCountEquals(0)
        rule.onAllNodesWithTag(LibraryTestTags.cell(2L)).assertCountEquals(0)
    }

    @Test
    fun tc258_24_tappingAFoldingSeriesUnfoldsIt() {
        val state = mutableStateOf(twoBookSeriesThenSingles(unfolded = true))
        val unfoldCalls = mutableListOf<Long>()
        rule.mainClock.autoAdvance = false
        setLibrary(
            state,
            onUnfoldSeries = {
                unfoldCalls += it
                state.setUnfolded(it, true)
            },
            onFoldUpSeries = { state.setUnfolded(it, false) },
        )
        rule.waitForIdle()

        rule.onNodeWithTag(LibraryTestTags.foldUp(10L)).performClick()
        rule.mainClock.advanceTimeBy(200)
        rule.waitForIdle()
        rule.onNodeWithTag(LibraryTestTags.stack(10L)).performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()

        assertEquals(listOf(10L), unfoldCalls)
        rule.onNodeWithTag(LibraryTestTags.seriesSummary(10L)).assertExists()
        rule.onNodeWithTag(LibraryTestTags.cell(1L)).assertExists()
        rule.onAllNodesWithTag(LibraryTestTags.stack(10L)).assertCountEquals(0)
    }

    @Test
    fun tc258_25_reducedMotionSwitchesLayoutWithoutMotion() {
        val state = mutableStateOf(twoBookSeriesThenSingles(unfolded = false))
        rule.mainClock.autoAdvance = false
        setLibrary(state, reducedMotion = true)
        rule.waitForIdle()
        val folded = rule.onNodeWithTag(LibraryTestTags.cell(3L)).getUnclippedBoundsInRoot()

        rule.runOnIdle { state.setUnfolded(10L, true) }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val afterFrame = boundsOf(*unfoldedCells())
        assertNotEquals(folded, afterFrame.getValue(cell(3L)))

        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(afterFrame, boundsOf(*unfoldedCells()))
    }

    @Test
    fun tc258_26_anAlreadyUnfoldedSeriesShowsAtRest() {
        val state = mutableStateOf(twoBookSeriesThenSingles(unfolded = true))
        rule.mainClock.autoAdvance = false
        setLibrary(state)
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val afterFrame = boundsOf(seriesSummary(10L), *unfoldedCells())

        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(afterFrame, boundsOf(seriesSummary(10L), *unfoldedCells()))
    }

    @Test
    fun tc258_27_turningGroupingOffDoesNotAnimate() {
        val state = mutableStateOf(twoBookSeriesThenSingles(unfolded = true))
        rule.mainClock.autoAdvance = false
        setLibrary(state)
        rule.waitForIdle()
        val unfolded = boundsOf(*singles())

        rule.runOnIdle { state.value = state.value.copy(groupBySeries = false, unfoldedSeries = emptySet()) }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val afterFrame = boundsOf(*singles())
        assertNotEquals(unfolded.getValue(cell(3L)), afterFrame.getValue(cell(3L)))

        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(afterFrame, boundsOf(*singles()))
        rule.onAllNodesWithTag(LibraryTestTags.seriesSummary(10L)).assertCountEquals(0)
    }

    private fun cell(id: Long) = LibraryTestTags.cell(id)

    private fun seriesSummary(seriesId: Long) = LibraryTestTags.seriesSummary(seriesId)

    /** The Single cells of [twoBookSeriesThenSingles], ids 3 to 6. */
    private fun singles() = arrayOf(cell(3L), cell(4L), cell(5L), cell(6L))

    /** The Member cells of an unfolded [twoBookSeriesThenSingles], plus the first Single. */
    private fun unfoldedCells() = arrayOf(cell(1L), cell(2L), cell(3L))

    /** The unclipped bounds in the root of each of [tags]' nodes, by tag. */
    private fun boundsOf(vararg tags: String): Map<String, DpRect> =
        tags.associateWith { rule.onNodeWithTag(it).getUnclippedBoundsInRoot() }

    private fun setLibrary(
        uiState: MutableState<LibraryUIState>,
        reducedMotion: Boolean = false,
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
                        reducedMotion = reducedMotion,
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

        /** Dune Chronicles' two books (ids 1 and 2), then four Singles (ids 3 to 6). Unfolded when [unfolded]. */
        fun twoBookSeriesThenSingles(unfolded: Boolean) = groupedState(
            seriesBook(1L, seriesId = 10L, series = DUNE, index = "1"),
            seriesBook(2L, seriesId = 10L, series = DUNE, index = "2"),
            book(3L),
            book(4L),
            book(5L),
            book(6L),
            unfolded = if (unfolded) setOf(10L) else emptySet(),
        )

        /** Adds [seriesId] to the unfolded set, or removes it, as the ViewModel does for a tap. */
        fun MutableState<LibraryUIState>.setUnfolded(seriesId: Long, unfolded: Boolean) {
            value = value.copy(
                unfoldedSeries = if (unfolded) value.unfoldedSeries + seriesId else value.unfoldedSeries - seriesId,
            )
        }

        /** Whether [to] moved more across the horizontal axis than down the vertical one, from [from]. */
        fun movesHorizontally(from: DpRect, to: DpRect): Boolean =
            abs(to.left.value - from.left.value) > abs(to.top.value - from.top.value)

        /** [rect]'s left edge along the horizontal axis, or its top edge along the vertical one. */
        fun DpRect.along(horizontal: Boolean): Float = if (horizontal) left.value else top.value
    }
}
