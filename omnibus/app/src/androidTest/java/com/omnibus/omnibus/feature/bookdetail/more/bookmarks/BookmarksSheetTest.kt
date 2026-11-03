package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookmarksSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val title get() = detailText(R.string.book_detail_bookmarks_title)
    private val closeLabel get() = detailText(R.string.book_detail_bookmarks_close)
    private val emptyCopy get() = detailText(R.string.book_detail_bookmarks_empty)
    private val loadingCopy get() = detailText(R.string.book_detail_bookmarks_loading)
    private val errorCopy get() = detailText(R.string.book_detail_bookmarks_error)
    private val retryLabel get() = detailText(R.string.book_detail_bookmarks_retry)

    /** The three sample rows: a titled text bookmark, an untitled audio one, and an untitled text one. */
    private val sampleRows = listOf(
        rowModel(bookmark(1, title = "The Episode of the Barrel"), BookmarkPlacement(7, 47)),
        rowModel(bookmark(2, position = "4332.5"), BookmarkPlacement(4, 28)),
        rowModel(bookmark(3, fraction = 0.41f), BookmarkPlacement(5, 41)),
    )

    // TC-9
    @Test
    fun issue117_tc9_titleIsAHeadingAndCloseIsLabelled() {
        rule.setSheetContent(BookmarksContent.Loaded(emptyList()))

        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(title).assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
        rule.onNodeWithContentDescription(closeLabel).assertIsDisplayed().assertHasClickAction()
    }

    // TC-15
    @Test
    fun issue117_tc15_rowsRenderInOrderWithNameMetaRibbonAndChevron() {
        rule.setSheetContent(BookmarksContent.Loaded(sampleRows))

        val keys = listOf("id:1", "id:2", "id:3")
        val tops = keys.map { rule.onNodeWithTag(BookmarksTestTags.row(it)).fetchSemanticsNode().boundsInRoot.top }
        assertTrue("rows in given order", tops[0] < tops[1] && tops[1] < tops[2])

        rule.onNodeWithTag(BookmarksTestTags.name("id:1"), useUnmergedTree = true)
            .assertTextEquals("The Episode of the Barrel")
        rule.onNodeWithTag(BookmarksTestTags.meta("id:1"), useUnmergedTree = true)
            .assertTextEquals("Chapter 7 · 47% · October 28")
        rule.onNodeWithTag(BookmarksTestTags.name("id:2"), useUnmergedTree = true)
            .assertTextEquals("Listening, 1 h 12 m in")
        rule.onNodeWithTag(BookmarksTestTags.meta("id:2"), useUnmergedTree = true)
            .assertTextEquals("Chapter 4 · 28% · October 28")
        rule.onNodeWithTag(BookmarksTestTags.name("id:3"), useUnmergedTree = true)
            .assertTextEquals("Saved position")

        keys.forEach { key ->
            rule.onNodeWithTag(BookmarksTestTags.ribbon(key), useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithTag(BookmarksTestTags.chevron(key), useUnmergedTree = true).assertIsDisplayed()
        }
    }

    // TC-16
    @Test
    fun issue117_tc16_rowWithoutMetaOmitsTheMetaLine() {
        val untitled = rowModel(bookmark(4, fraction = null, createdAtSeconds = null), BookmarkPlacement(null, null))
        rule.setSheetContent(BookmarksContent.Loaded(listOf(untitled)))

        rule.onNodeWithTag(BookmarksTestTags.name("id:4"), useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithTag(BookmarksTestTags.meta("id:4"), useUnmergedTree = true).assertCountEquals(0)
    }

    // TC-37
    @Test
    fun issue117_tc37_emptyListShowsTheEmptyCopyWithoutRows() {
        rule.setSheetContent(BookmarksContent.Loaded(emptyList()))

        rule.onNodeWithText(emptyCopy).assertIsDisplayed()
        rule.onAllNodesWithTag(BookmarksTestTags.LIST, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag(BookmarksTestTags.row("id:1"), useUnmergedTree = true).assertCountEquals(0)
    }

    // TC-39
    @Test
    fun issue117_tc39_loadingShowsAnIndicatorOnly() {
        rule.setSheetContent(BookmarksContent.Loading)

        rule.onNodeWithTag(BookmarksTestTags.LOADING).assertIsDisplayed()
        rule.onNodeWithText(loadingCopy).assertIsDisplayed()
        rule.onAllNodesWithText(emptyCopy).assertCountEquals(0)
        rule.onAllNodesWithTag(BookmarksTestTags.ERROR).assertCountEquals(0)
    }

    // TC-40
    @Test
    fun issue117_tc40_failedShowsTheErrorAndRetriesOnce() {
        var retries = 0
        rule.setSheetContent(BookmarksContent.Failed, onRetry = { retries++ })

        rule.onNodeWithText(errorCopy).assertIsDisplayed()
        rule.onNodeWithText(retryLabel).assertIsDisplayed().performClick()
        rule.waitForIdle()

        assertEquals(1, retries)
        rule.onAllNodesWithText(emptyCopy).assertCountEquals(0)
    }

    // TC-63
    @Test
    fun issue117_tc63_rendersInLightTheme() {
        assertSheetRendersOn(darkTheme = false)
    }

    // TC-63
    @Test
    fun issue117_tc63_rendersInDarkTheme() {
        assertSheetRendersOn(darkTheme = true)
    }

    /** The content paints its own ground and keeps its ink, whichever app theme it sits in. */
    private fun assertSheetRendersOn(darkTheme: Boolean) {
        rule.setSheetContent(BookmarksContent.Loaded(sampleRows.take(1)), darkTheme = darkTheme)

        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText("The Episode of the Barrel").assertIsDisplayed()
        rule.onNodeWithText("Chapter 7 · 47% · October 28").assertIsDisplayed()
        val pixel = rule.onNodeWithTag(BookmarksTestTags.SHEET).captureToImage().toPixelMap()[2, 2]
        assertEquals(BookmarksSheetGround.toArgb(), pixel.toArgb())
    }

    // TC-65
    @Test
    fun issue117_tc65_longTextWrapsAndDoesNotClip() {
        val long = rowModel(bookmark(5, title = "A long bookmark name that must wrap onto several lines " +
            "in a narrow window, with a meta line as long as it needs to be to wrap as well"))
        val short = rowModel(bookmark(6, title = "Ok", createdAtSeconds = null), BookmarkPlacement(null, null))
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                CompositionLocalProvider(LocalDensity provides Density(rule.density.density, fontScale = 2f)) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth(0.5f)) {
                            BookmarksSheetContent(
                                content = BookmarksContent.Loaded(listOf(long, short)),
                                nowMillis = SHEET_NOW,
                                ribbonColor = TestRibbon,
                                onRetry = {},
                                onClose = {},
                                onChosen = {},
                            )
                        }
                    }
                }
            }
        }

        // Measure the long row first: on a small window it fills the viewport, so the short row
        // below it is only composed once the list has scrolled to it.
        val longName = rule.onNodeWithTag(BookmarksTestTags.name("id:5"), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val row = rule.onNodeWithTag(BookmarksTestTags.row("id:5")).fetchSemanticsNode().boundsInRoot
        val chevron = rule.onNodeWithTag(BookmarksTestTags.chevron("id:5"), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("the chevron stays inside the row", chevron.right <= row.right + 1f)
        assertTrue("the name does not run under the chevron", longName.right <= chevron.left + 1f)

        rule.onNodeWithTag(BookmarksTestTags.LIST).performScrollToNode(hasTestTag(BookmarksTestTags.row("id:6")))
        val shortName = rule.onNodeWithTag(BookmarksTestTags.name("id:6"), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("the long name wraps onto more lines", longName.height > shortName.height)
    }

    // TC-66
    @Test
    fun issue117_tc66_listScrollsUnderAFixedTitle() {
        val many = (1..40L).map { rowModel(bookmark(it, title = "Bookmark number $it")) }
        rule.setSheetContent(BookmarksContent.Loaded(many))

        val titleBefore = rule.onNodeWithTag(BookmarksTestTags.TITLE).fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag(BookmarksTestTags.LIST).performScrollToNode(hasTestTag(BookmarksTestTags.row("id:40")))
        rule.onNodeWithTag(BookmarksTestTags.row("id:40")).assertIsDisplayed()
        val titleAfter = rule.onNodeWithTag(BookmarksTestTags.TITLE).fetchSemanticsNode().boundsInRoot

        assertEquals(titleBefore, titleAfter)
    }

    // TC-67
    @Test
    fun issue117_tc67_ribbonLeadsTheNameAndChevronTrailsIt() {
        rule.setSheetContent(BookmarksContent.Loaded(sampleRows.take(1)))

        val ribbon = rule.onNodeWithTag(BookmarksTestTags.ribbon("id:1"), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val name = rule.onNodeWithTag(BookmarksTestTags.name("id:1"), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val chevron = rule.onNodeWithTag(BookmarksTestTags.chevron("id:1"), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        assertTrue("the ribbon is left of the name", ribbon.right <= name.left + 1f)
        assertTrue("the chevron is right of the name", name.right <= chevron.left + 1f)
    }
}
