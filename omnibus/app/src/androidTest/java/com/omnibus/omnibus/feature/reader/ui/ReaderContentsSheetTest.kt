package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.feature.reader.ReaderBookmarksStatus
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Positions and sizes are relative (one node against another, or a fraction of the host), with a pixel of
// slack for dp conversion. No test pins a screen size, so the cases hold on the 320dp CI AVD and a phone.
class ReaderContentsSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val cover = CoverIdentity(uuid = "book-1", title = "The Long Road Home", creator = "A. N. Author", hasCover = false)

    /** Three levels, no pages, no current chapter. */
    private val fiveEntries = listOf(
        TocEntry(label = "Part One", href = "one.xhtml", level = 0),
        TocEntry(label = "1. Departure", href = "two.xhtml", level = 1),
        TocEntry(label = "Crossing at Dusk", href = "three.xhtml", level = 2),
        TocEntry(label = "Part Two", href = "four.xhtml", level = 0),
        TocEntry(label = "2. Return", href = "five.xhtml", level = 1),
    )

    /** Sixty bare entries, for the scroll cases. */
    private val sixtyEntries = (0 until 60).map { TocEntry(label = "Chapter ${it + 1}", href = "c$it.xhtml", level = 0) }

    /** The sheet's body over a full-size host, with [toc] and [location] read on every composition. */
    private fun showBody(
        toc: MutableState<List<TocEntry>> = mutableStateOf(fiveEntries),
        location: MutableState<RelocateData?> = mutableStateOf(null),
        title: String = "The Long Road Home",
        onEntryChosen: (TocEntry) -> Unit = {},
        onDismiss: () -> Unit = {},
        initialTab: ReaderContentsTab = ReaderContentsTab.Contents,
        bookmarks: MutableState<List<Bookmark>> = mutableStateOf(emptyList()),
        status: MutableState<ReaderBookmarksStatus> = mutableStateOf(ReaderBookmarksStatus.Loaded),
        onBookmarkChosen: (Bookmark) -> Unit = {},
        onBookmarksRetry: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = false) {
                ReaderContentsSheetContent(
                    title = title,
                    cover = cover,
                    toc = toc.value,
                    location = location.value,
                    onEntryChosen = onEntryChosen,
                    onDismiss = onDismiss,
                    modifier = Modifier.fillMaxSize(),
                    initialTab = initialTab,
                    bookmarks = bookmarks.value,
                    bookmarksStatus = status.value,
                    onBookmarkChosen = onBookmarkChosen,
                    onBookmarksRetry = onBookmarksRetry,
                )
            }
        }
    }

    /** The real modal sheet over a full-size host, shown while [open] is true. */
    private fun showModal(
        open: MutableState<Boolean>,
        toc: List<TocEntry> = fiveEntries,
        onDismiss: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().testTag(HOST)) {
                    if (open.value) {
                        ReaderContentsSheet(
                            title = "The Long Road Home",
                            cover = cover,
                            toc = toc,
                            location = null,
                            onEntryChosen = {},
                            onDismiss = {
                                onDismiss()
                                open.value = false
                            },
                        )
                    }
                }
            }
        }
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }

    private fun assertNear(expected: Float, actual: Float, message: String) {
        assertTrue("$message: expected $expected but was $actual", abs(expected - actual) <= 1f)
    }

    private fun assertColourEquals(expected: Color, actual: Color, message: String) {
        assertEquals(message, expected, actual)
    }

    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    // CS-5
    // CS-5
    @Test
    fun cs5_listsEveryEntryInOrder() {
        showBody()

        var previousTop = Float.NEGATIVE_INFINITY
        fiveEntries.forEachIndexed { i, entry ->
            val row = rule.onNodeWithTag(ReaderContentsTags.row(i))
            row.assertIsDisplayed()
            row.fetchSemanticsNode().boundsInRoot.top.also { top ->
                assertTrue("row $i is below row ${i - 1}", top > previousTop)
                previousTop = top
            }
            rule.onNodeWithTag(ReaderContentsTags.label(i), useUnmergedTree = true).assertTextEquals(entry.label)
        }
    }

    // CS-6
    // CS-6
    @Test
    fun cs6_labelsAreTrimmed() {
        showBody(toc = mutableStateOf(listOf(TocEntry(label = "   Spaced Out  ", href = "a.xhtml", level = 0))))

        rule.onNodeWithTag(ReaderContentsTags.label(0), useUnmergedTree = true).assertTextEquals("Spaced Out")
    }

    // CS-7
    // CS-7
    @Test
    fun cs7_longLabelsWrap() {
        val long = (1..35).joinToString(" ") { "word$it" }
        showBody(
            toc = mutableStateOf(
                listOf(
                    TocEntry(label = long, href = "long.xhtml", level = 0),
                    TocEntry(label = "Short", href = "short.xhtml", level = 0),
                ),
            ),
        )

        val longLabel = rule.onNodeWithTag(ReaderContentsTags.label(0), useUnmergedTree = true).fetchSemanticsNode().size.height
        val shortLabel = rule.onNodeWithTag(ReaderContentsTags.label(1), useUnmergedTree = true).fetchSemanticsNode().size.height
        assertTrue("the long label wraps onto more lines than a single line", longLabel > shortLabel)

        val sheet = rule.onNodeWithTag(ReaderContentsTags.SHEET).fetchSemanticsNode().boundsInRoot
        val row = rule.onNodeWithTag(ReaderContentsTags.row(0)).fetchSemanticsNode().boundsInRoot
        assertTrue("the row stays within the sheet width", row.right <= sheet.right + 1f)
    }

    // CS-8
    @Test
    fun cs8_opensAtNearlyFullHeight() {
        showModal(open = mutableStateOf(true))

        val host = rule.onNodeWithTag(HOST).fetchSemanticsNode().size.height.toFloat()
        val sheet = rule.onNodeWithTag(ReaderContentsTags.SHEET).fetchSemanticsNode().size.height.toFloat()
        assertTrue("the sheet is at least 75% of the host (was $sheet of $host)", sheet >= 0.75f * host)
    }

    // CS-9
    // CS-9
    @Test
    fun cs9_labelsIndent16dpPerLevel() {
        val leveled = listOf(
            TocEntry(label = "Top", href = "a.xhtml", level = 0),
            TocEntry(label = "Middle", href = "b.xhtml", level = 1),
            TocEntry(label = "Deep", href = "c.xhtml", level = 2),
        )
        showBody(toc = mutableStateOf(leveled))

        val step = with(rule.density) { 16.dp.toPx() }
        val left = (0..2).map { rule.onNodeWithTag(ReaderContentsTags.label(it), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left }
        assertNear(step, left[1] - left[0], "level 1 indent")
        assertNear(2 * step, left[2] - left[0], "level 2 indent")
    }

    // CS-10
    @Test
    fun cs10_topLevelUsesMediumWeight() {
        showBody()

        val top = rule.onNodeWithTag(ReaderContentsTags.label(0), useUnmergedTree = true).textLayout().layoutInput.style.fontWeight
        val nested = rule.onNodeWithTag(ReaderContentsTags.label(1), useUnmergedTree = true).textLayout().layoutInput.style.fontWeight
        assertEquals(FontWeight.Medium, top)
        assertEquals(FontWeight.Normal, nested)
    }

    // CS-11
    // CS-11
    @Test
    fun cs11_pageNumberIsRightAligned() {
        showBody(toc = mutableStateOf(listOf(TocEntry(label = "Paged", href = "a.xhtml", level = 0, page = 12))))

        rule.onNodeWithTag(ReaderContentsTags.page(0), useUnmergedTree = true).assertTextEquals("12")
        val row = rule.onNodeWithTag(ReaderContentsTags.row(0)).fetchSemanticsNode().boundsInRoot
        val label = rule.onNodeWithTag(ReaderContentsTags.label(0), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val page = rule.onNodeWithTag(ReaderContentsTags.page(0), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the page is right of the label", page.left >= label.right - 1f)
        assertNear(row.right, page.right, "page right edge against the row")
    }

    // CS-12
    @Test
    fun cs12_rowWithoutPageGivesLabelTheSpace() {
        showBody(
            toc = mutableStateOf(
                listOf(
                    TocEntry(label = "Bare", href = "a.xhtml", level = 0, page = null),
                    TocEntry(label = "Paged", href = "b.xhtml", level = 0, page = 12),
                ),
            ),
        )

        rule.onAllNodesWithTag(ReaderContentsTags.page(0), useUnmergedTree = true).assertCountEquals(0)
        val bareRow = rule.onNodeWithTag(ReaderContentsTags.row(0)).fetchSemanticsNode().boundsInRoot
        val bareLabel = rule.onNodeWithTag(ReaderContentsTags.label(0), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertNear(bareRow.right, bareLabel.right, "label right edge in the row without a page")

        val pagedLabel = rule.onNodeWithTag(ReaderContentsTags.label(1), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pagedPage = rule.onNodeWithTag(ReaderContentsTags.page(1), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the paged label stops before its page", pagedLabel.right <= pagedPage.left + 1f)
    }

    // CS-13
    // CS-13
    @Test
    fun cs13_nonPositivePageShowsNothing() {
        showBody(
            toc = mutableStateOf(
                listOf(
                    TocEntry(label = "Alpha", href = "a.xhtml", level = 0, page = 0),
                    TocEntry(label = "Beta", href = "b.xhtml", level = 0, page = -3),
                ),
            ),
        )

        rule.onAllNodesWithTag(ReaderContentsTags.page(0), useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag(ReaderContentsTags.page(1), useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag(ReaderContentsTags.row(0)).assertTextEquals("Alpha")
        rule.onNodeWithTag(ReaderContentsTags.row(1)).assertTextEquals("Beta")
    }

    // CS-15
    @Test
    fun cs15_secondTocAddsPagesWithoutResetting() {
        val toc = mutableStateOf(sixtyEntries)
        showBody(toc = toc)

        rule.onNodeWithTag(ReaderContentsTags.LIST).performScrollToIndex(40)
        rule.waitForIdle()
        val before = rule.onNodeWithTag(ReaderContentsTags.row(40)).fetchSemanticsNode().boundsInRoot.top

        rule.runOnIdle { toc.value = sixtyEntries.mapIndexed { i, entry -> entry.copy(page = i + 1) } }
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderContentsTags.row(40)).assertIsDisplayed()
        assertNear(before, rule.onNodeWithTag(ReaderContentsTags.row(40)).fetchSemanticsNode().boundsInRoot.top, "row 40 after the pages arrive")
        rule.onNodeWithTag(ReaderContentsTags.segment(ReaderContentsTab.Contents)).assertIsSelected()
        rule.onNodeWithTag(ReaderContentsTags.page(40), useUnmergedTree = true).assertTextEquals("41")
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
    }

    // CS-19
    @Test
    fun cs19_tappingARowChoosesThatEntry() {
        val toc = listOf(
            TocEntry(label = "Same", href = "one.xhtml", level = 0),
            TocEntry(label = "Same", href = "two.xhtml", level = 0),
            TocEntry(label = "Same", href = "three.xhtml", level = 0),
            TocEntry(label = "Other", href = "four.xhtml", level = 0),
        )
        val chosen = mutableListOf<TocEntry>()
        showBody(toc = mutableStateOf(toc), onEntryChosen = { chosen += it })

        rule.onNodeWithTag(ReaderContentsTags.row(2)).performClick()
        rule.waitForIdle()

        assertEquals(listOf(toc[2]), chosen)
    }

    // CS-23
    @Test
    fun cs23_marksCurrentChapterByIndex() {
        val toc = listOf(
            TocEntry(label = "Same", href = "one.xhtml", level = 0),
            TocEntry(label = "Same", href = "two.xhtml", level = 0),
            TocEntry(label = "Same", href = "three.xhtml", level = 0),
            TocEntry(label = "Same", href = "four.xhtml", level = 0),
        )
        showBody(toc = mutableStateOf(toc), location = mutableStateOf(RelocateData(chapter = 3)))

        rule.onAllNodesWithTag(ReaderContentsTags.CURRENT_MARKER, useUnmergedTree = true).assertCountEquals(1)
        val marker = rule.onNodeWithTag(ReaderContentsTags.CURRENT_MARKER, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val centre = (marker.top + marker.bottom) / 2f
        for (i in toc.indices) {
            val row = rule.onNodeWithTag(ReaderContentsTags.row(i)).fetchSemanticsNode().boundsInRoot
            assertEquals("marker inside row $i", i == 2, centre >= row.top && centre <= row.bottom)
        }
    }

    // CS-24
    @Test
    fun cs24_noMarkerWithoutAValidChapter() {
        val location = mutableStateOf<RelocateData?>(RelocateData(chapter = 0))
        showBody(location = location)

        rule.onAllNodesWithTag(ReaderContentsTags.CURRENT_MARKER, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).assertCountEquals(0)

        rule.runOnIdle { location.value = RelocateData(chapter = fiveEntries.size + 1) }
        rule.waitForIdle()
        rule.onAllNodesWithTag(ReaderContentsTags.CURRENT_MARKER, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).assertCountEquals(0)

        rule.runOnIdle { location.value = null }
        rule.waitForIdle()
        rule.onAllNodesWithTag(ReaderContentsTags.CURRENT_MARKER, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).assertCountEquals(0)
    }

    // CS-25
    @Test
    fun cs25_markerColumnDoesNotShiftText() {
        showBody(
            toc = mutableStateOf(
                listOf(
                    TocEntry(label = "Unmarked", href = "a.xhtml", level = 0),
                    TocEntry(label = "Marked", href = "b.xhtml", level = 0),
                ),
            ),
            location = mutableStateOf(RelocateData(chapter = 2)),
        )

        val unmarked = rule.onNodeWithTag(ReaderContentsTags.label(0), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val marked = rule.onNodeWithTag(ReaderContentsTags.label(1), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertNear(unmarked.left, marked.left, "label left edge with and without the marker")

        val row = rule.onNodeWithTag(ReaderContentsTags.row(1)).fetchSemanticsNode().boundsInRoot
        val marker = rule.onNodeWithTag(ReaderContentsTags.CURRENT_MARKER, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the marker is inside its row", marker.top >= row.top - 1f && marker.bottom <= row.bottom + 1f)
        assertTrue("the marker is narrower than it is tall", marker.width < marker.height)
    }

    // CS-26
    @Test
    fun cs26_currentRowUsesAccentAndSemibold() {
        showBody(
            toc = mutableStateOf(
                listOf(
                    TocEntry(label = "Unmarked", href = "a.xhtml", level = 0, page = 3),
                    TocEntry(label = "Marked", href = "b.xhtml", level = 0, page = 9),
                ),
            ),
            location = mutableStateOf(RelocateData(chapter = 2)),
        )
        val palette = OmnibusPalette.Light

        val currentLabel = rule.onNodeWithTag(ReaderContentsTags.label(1), useUnmergedTree = true).textLayout().layoutInput.style
        assertColourEquals(palette.accent, currentLabel.color, "current label colour")
        assertEquals(FontWeight.SemiBold, currentLabel.fontWeight)
        assertColourEquals(palette.accent, rule.onNodeWithTag(ReaderContentsTags.page(1), useUnmergedTree = true).textLayout().layoutInput.style.color, "current page colour")

        val plainLabel = rule.onNodeWithTag(ReaderContentsTags.label(0), useUnmergedTree = true).textLayout().layoutInput.style
        assertColourEquals(palette.ink1, plainLabel.color, "unmarked label colour")
        assertColourEquals(palette.ink3, rule.onNodeWithTag(ReaderContentsTags.page(0), useUnmergedTree = true).textLayout().layoutInput.style.color, "unmarked page colour")
    }

    // CS-27
    @Test
    fun cs27_scrollsCurrentEntryIntoViewOnOpen() {
        showBody(toc = mutableStateOf(sixtyEntries), location = mutableStateOf(RelocateData(chapter = 40)))

        rule.waitUntil { rule.onAllNodesWithTag(ReaderContentsTags.row(39)).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderContentsTags.row(39)).assertIsDisplayed()
        rule.onAllNodesWithTag(ReaderContentsTags.row(0)).assertCountEquals(0)

        val list = rule.onNodeWithTag(ReaderContentsTags.LIST).fetchSemanticsNode().boundsInRoot
        val row = rule.onNodeWithTag(ReaderContentsTags.row(39)).fetchSemanticsNode().boundsInRoot
        val centre = (row.top + row.bottom) / 2f
        val height = list.bottom - list.top
        assertTrue("the current row sits in the middle of the list", centre >= list.top + 0.25f * height && centre <= list.top + 0.75f * height)
    }

    // CS-28
    @Test
    fun cs28_noScrollWhenNothingIsMarked() {
        showBody(toc = mutableStateOf(sixtyEntries), location = mutableStateOf(RelocateData(chapter = 0)))

        rule.onNodeWithTag(ReaderContentsTags.row(0)).assertIsDisplayed()
        val list = rule.onNodeWithTag(ReaderContentsTags.LIST).fetchSemanticsNode().boundsInRoot
        assertNear(list.top, rule.onNodeWithTag(ReaderContentsTags.row(0)).fetchSemanticsNode().boundsInRoot.top, "row 0 at the top of the list")
    }

    // CS-29 (TC-72): two segments, Contents selected by default.
    @Test
    fun cs29_showsContentsAndBookmarksSegmentsWithContentsSelected() {
        showBody()

        rule.onAllNodesWithTag(ReaderContentsTags.segment(ReaderContentsTab.Contents)).assertCountEquals(1)
        rule.onAllNodesWithTag(ReaderContentsTags.segment(ReaderContentsTab.Bookmarks)).assertCountEquals(1)
        val segment = rule.onNodeWithTag(ReaderContentsTags.segment(ReaderContentsTab.Contents))
        segment.assertTextEquals(text(R.string.reader_contents_tab_contents))
        segment.assertIsSelected()
        val bookmarksSegment = rule.onNodeWithTag(ReaderContentsTags.segment(ReaderContentsTab.Bookmarks))
        bookmarksSegment.assertTextEquals(text(R.string.reader_contents_tab_bookmarks))
        bookmarksSegment.assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))

        segment.performClick()
        rule.waitForIdle()

        segment.assertIsSelected()
        rule.onNodeWithTag(ReaderContentsTags.row(0)).assertIsDisplayed()
    }

    // CS-33
    @Test
    fun cs33_headerShowsPagePosition() {
        showBody(location = mutableStateOf(RelocateData(page = 5, totalPages = 20, pct = 25)))

        rule.onNodeWithTag(ReaderContentsTags.TITLE).assertIsDisplayed()
        rule.onNodeWithTag(ReaderContentsTags.POSITION).assertTextEquals("Page 5 of 20")
        rule.onNodeWithTag(ReaderContentsTags.POSITION).assertTextEquals(text(R.string.reader_contents_page_of, 5, 20))
    }

    // CS-34
    @Test
    fun cs34_headerFallsBackToPercent() {
        showBody(location = mutableStateOf(RelocateData(page = 0, totalPages = 0, pct = 42)))

        rule.onNodeWithTag(ReaderContentsTags.POSITION).assertTextEquals("42% through")
        rule.onNodeWithTag(ReaderContentsTags.POSITION).assertTextEquals(text(R.string.reader_contents_percent_through, 42))
    }

    // CS-35
    // CS-35
    @Test
    fun cs35_headerHasNoPositionWithoutLocation() {
        showBody(location = mutableStateOf(null))

        rule.onNodeWithTag(ReaderContentsTags.TITLE).assertIsDisplayed()
        rule.onAllNodesWithTag(ReaderContentsTags.POSITION).assertCountEquals(0)
    }

    // CS-36
    @Test
    fun cs36_titleIsCappedAtTwoLines() {
        val longTitle = (1..40).joinToString(" ") { "Title$it" }
        showBody(title = longTitle)

        val layout = rule.onNodeWithTag(ReaderContentsTags.TITLE).textLayout()
        assertEquals(2, layout.lineCount)
        assertTrue("the title is ellipsized", layout.hasVisualOverflow)
    }

    // CS-37
    @Test
    fun cs37_headerShowsCoverBesideTitle() {
        showBody()

        rule.onNodeWithTag(ReaderContentsTags.COVER).assertIsDisplayed()
        val cover = rule.onNodeWithTag(ReaderContentsTags.COVER).fetchSemanticsNode().boundsInRoot
        val title = rule.onNodeWithTag(ReaderContentsTags.TITLE).fetchSemanticsNode().boundsInRoot
        assertTrue("the cover is left of the title", cover.right <= title.left + 1f)
        assertTrue("the cover starts at or above the title", cover.top <= title.top + 1f)
    }

    // CS-38
    @Test
    fun cs38_closeButtonDismisses() {
        var dismissals = 0
        showBody(onDismiss = { dismissals++ })

        val close = rule.onNodeWithTag(ReaderContentsTags.CLOSE)
        close.assertContentDescriptionEquals(text(R.string.reader_contents_done))
        close.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val bounds = close.fetchSemanticsNode().boundsInRoot
        assertNear(bounds.width, bounds.height, "the close button is square")

        close.performClick()
        rule.waitForIdle()

        assertEquals(1, dismissals)
    }

    // CS-41
    @Test
    fun cs41_emptyTocShowsEmptyState() {
        showBody(toc = mutableStateOf(emptyList()))

        rule.onNodeWithTag(ReaderContentsTags.EMPTY).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.reader_contents_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.reader_contents_empty_message)).assertIsDisplayed()
        rule.onAllNodesWithTag(ReaderContentsTags.row(0)).assertCountEquals(0)
        rule.onNodeWithTag(ReaderContentsTags.segment(ReaderContentsTab.Contents)).assertIsDisplayed()
    }

    // CS-45
    @Test
    fun cs45_separatorsSitBetweenRowsOnly() {
        showBody(toc = mutableStateOf(fiveEntries.take(3)))

        rule.onAllNodesWithTag(ReaderContentsTags.divider(0)).assertCountEquals(0)
        for (i in 1..2) {
            val divider = rule.onNodeWithTag(ReaderContentsTags.divider(i)).fetchSemanticsNode().boundsInRoot
            val row = rule.onNodeWithTag(ReaderContentsTags.row(i)).fetchSemanticsNode().boundsInRoot
            assertTrue("divider $i is above row $i", divider.top <= row.top + 1f)
        }
    }

    // TC-67
    @Test
    fun bookmarksTabShowsLoading() {
        showBody(initialTab = ReaderContentsTab.Bookmarks, status = mutableStateOf(ReaderBookmarksStatus.Loading))

        rule.onNodeWithTag(ReaderContentsTags.BOOKMARKS_LOADING).assertIsDisplayed()
        rule.onAllNodesWithTag(ReaderContentsTags.BOOKMARKS_LIST).assertCountEquals(0)
        rule.onAllNodesWithTag(ReaderContentsTags.BOOKMARKS_EMPTY).assertCountEquals(0)
        rule.onAllNodesWithTag(ReaderContentsTags.BOOKMARKS_ERROR).assertCountEquals(0)
    }

    // TC-68
    @Test
    fun bookmarksTabShowsErrorAndRetries() {
        var retries = 0
        showBody(
            initialTab = ReaderContentsTab.Bookmarks,
            status = mutableStateOf(ReaderBookmarksStatus.Failed),
            onBookmarksRetry = { retries++ },
        )

        rule.onNodeWithTag(ReaderContentsTags.BOOKMARKS_ERROR).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.reader_bookmarks_error_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.reader_bookmarks_error_message)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.reader_try_again)).performClick()
        rule.waitForIdle()

        assertEquals(1, retries)
    }

    // TC-69
    @Test
    fun bookmarksTabShowsEmptyState() {
        showBody(initialTab = ReaderContentsTab.Bookmarks)

        rule.onNodeWithTag(ReaderContentsTags.BOOKMARKS_EMPTY).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.reader_bookmarks_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.reader_bookmarks_empty_message)).assertIsDisplayed()
    }

    // TC-70
    @Test
    fun bookmarksTabListsRowsAndJumpsOnTap() {
        val bookmarks = listOf(
            Bookmark(1L, "client-1", "book-1", "epubcfi(/6/4)", "The ferry", "Part One", 0.43f, null),
            Bookmark(2L, "client-2", "book-1", "epubcfi(/6/8)", null, "Crossing at Dusk", null, null),
            Bookmark(3L, "client-3", "book-1", "epubcfi(/6/12)", null, null, 1f, null),
        )
        val chosen = mutableListOf<Bookmark>()
        showBody(
            initialTab = ReaderContentsTab.Bookmarks,
            bookmarks = mutableStateOf(bookmarks),
            onBookmarkChosen = { chosen += it },
        )

        val first = rule.onNodeWithTag(ReaderContentsTags.bookmarkRow(0))
        val second = rule.onNodeWithTag(ReaderContentsTags.bookmarkRow(1))
        val third = rule.onNodeWithTag(ReaderContentsTags.bookmarkRow(2))
        first.assert(hasText("The ferry")).assert(hasText("43%"))
        second.assert(hasText("Crossing at Dusk"))
        rule.onAllNodesWithTag(ReaderContentsTags.bookmarkPercent(1)).assertCountEquals(0)
        third.assert(hasText(text(R.string.reader_bookmark_untitled))).assert(hasText("100%"))

        val firstTop = first.fetchSemanticsNode().boundsInRoot.top
        val secondTop = second.fetchSemanticsNode().boundsInRoot.top
        val thirdTop = third.fetchSemanticsNode().boundsInRoot.top
        assertTrue("rows keep the server order", firstTop < secondTop && secondTop < thirdTop)

        first.performClick()
        rule.waitForIdle()
        assertEquals(listOf(bookmarks[0]), chosen)
    }

    // TC-71
    @Test
    fun segmentsSwitchBetweenContentsAndBookmarks() {
        showBody(
            initialTab = ReaderContentsTab.Bookmarks,
            bookmarks = mutableStateOf(
                listOf(
                    Bookmark(1L, "client-1", "book-1", "epubcfi(/6/4)", "The ferry", null, 0.2f, null),
                    Bookmark(2L, "client-2", "book-1", "epubcfi(/6/8)", "Dusk", null, 0.4f, null),
                ),
            ),
        )

        rule.onNodeWithTag(ReaderContentsTags.segment(ReaderContentsTab.Bookmarks)).assertIsSelected()
        rule.onNodeWithTag(ReaderContentsTags.bookmarkRow(0)).assertIsDisplayed()

        rule.onNodeWithTag(ReaderContentsTags.segment(ReaderContentsTab.Contents)).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderContentsTags.segment(ReaderContentsTab.Contents)).assertIsSelected()
        rule.onNodeWithTag(ReaderContentsTags.row(0)).assertIsDisplayed()

        rule.onNodeWithTag(ReaderContentsTags.segment(ReaderContentsTab.Bookmarks)).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderContentsTags.bookmarkRow(0)).assertIsDisplayed()
    }

    // CS-49
    @Test
    fun cs49_swipeDownDismisses() {
        val open = mutableStateOf(true)
        var dismissals = 0
        showModal(open = open, onDismiss = { dismissals++ })
        rule.onNodeWithTag(ReaderContentsTags.SHEET).assertIsDisplayed()

        val titleTop = rule.onNodeWithTag(ReaderContentsTags.TITLE).fetchSemanticsNode().boundsInRoot.top
        val screenBottom = context.resources.displayMetrics.heightPixels.toFloat()
        rule.onNodeWithTag(ReaderContentsTags.TITLE).performTouchInput {
            swipe(start = Offset(centerX, centerY), end = Offset(centerX, screenBottom - titleTop - 2f))
        }
        rule.waitForIdle()

        assertEquals(1, dismissals)
        rule.onAllNodesWithTag(ReaderContentsTags.SHEET).assertCountEquals(0)
    }

    // CS-53
    @Test
    fun cs53_rowsAreSingleButtonNodesWithMergedText() {
        showBody(toc = mutableStateOf(listOf(TocEntry(label = "Paged Chapter", href = "a.xhtml", level = 0, page = 12))))

        val row = rule.onNodeWithTag(ReaderContentsTags.row(0))
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        row.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        val merged = row.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" ") { it.text }
        assertTrue("the merged text has the label: $merged", merged.contains("Paged Chapter"))
        assertTrue("the merged text has the page: $merged", merged.contains("12"))
    }

    // CS-54
    @Test
    fun cs54_currentRowAnnouncesItsState() {
        showBody(location = mutableStateOf(RelocateData(chapter = 2)))

        val current = rule.onNodeWithTag(ReaderContentsTags.row(1)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertEquals(text(R.string.reader_contents_current_chapter), current)
        rule.onNodeWithTag(ReaderContentsTags.row(0)).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    // CS-55
    // CS-55
    @Test
    fun cs55_rowsMeetMinimumTouchHeight() {
        showBody(toc = mutableStateOf(listOf(TocEntry(label = "One line", href = "a.xhtml", level = 0))))

        val height = rule.onNodeWithTag(ReaderContentsTags.row(0)).fetchSemanticsNode().size.height.toFloat()
        val minimum = with(rule.density) { 48.dp.toPx() }
        assertTrue("row height $height is at least $minimum", height >= minimum - 1f)
    }

    // CS-56
    @Test
    fun cs56_closeButtonIsReachableBySemantics() {
        var dismissals = 0
        showBody(onDismiss = { dismissals++ })

        val height = rule.onNodeWithTag(ReaderContentsTags.CLOSE).fetchSemanticsNode().size.height.toFloat()
        assertTrue("the close button is at least its 32dp visual", height >= with(rule.density) { 32.dp.toPx() } - 1f)
        rule.onNodeWithTag(ReaderContentsTags.CLOSE).performClick()
        rule.waitForIdle()
        assertEquals(1, dismissals)
    }

    private companion object {
        const val HOST = "contents_host"
    }
}
