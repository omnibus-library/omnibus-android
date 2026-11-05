package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

    // CS-10
    @Test
    fun cs10_topLevelUsesMediumWeight() {
        showBody()

        val top = rule.onNodeWithTag(ReaderContentsTags.label(0), useUnmergedTree = true).textLayout().layoutInput.style.fontWeight
        val nested = rule.onNodeWithTag(ReaderContentsTags.label(1), useUnmergedTree = true).textLayout().layoutInput.style.fontWeight
        assertEquals(FontWeight.Medium, top)
        assertEquals(FontWeight.Normal, nested)
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
