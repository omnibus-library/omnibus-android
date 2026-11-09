package com.omnibus.omnibus.feature.library.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.library.LibraryUIError
import com.omnibus.omnibus.feature.library.LibraryUIState
import com.omnibus.omnibus.feature.shell.ui.SHELL_OVERLAY_TAG
import com.omnibus.omnibus.feature.shell.ui.ShellLayout
import com.omnibus.omnibus.feature.shell.ui.ShellTestOverlay
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Library under the system bars and the shell's bottom overlay: its grid, masthead, fades and snackbar. */
class LibraryEdgeToEdgeTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    // TC-4
    @Test
    fun tc4_gridFillsTheWindowUnderTheBars() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        setLibrary(LibraryUIState(books = (1L..40L).map { book(it) }))
        rule.waitForIdle()

        val grid = rule.onNodeWithTag(LibraryTestTags.GRID).bounds()
        val root = rule.onRoot().bounds()
        assertEquals(root.top, grid.top, 1f)
        assertEquals(root.bottom, grid.bottom, 1f)
    }

    // TC-5
    @Test
    fun tc5_mastheadClearsTheStatusBarAtScrollZero() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        var statusTop = 0
        setLibrary(
            LibraryUIState(books = (1L..40L).map { book(it) }),
            onStatusTop = { statusTop = it },
        )
        rule.waitForIdle()

        val masthead = rule.onNode(isHeading() and hasText(detailText(R.string.library_title))).bounds()
        assertTrue(
            "the masthead starts at ${masthead.top}, above the status bar's bottom at $statusTop",
            masthead.top >= statusTop - 1f,
        )
    }

    // TC-6
    @Test
    fun tc6_lastRowEndsAboveTheOverlayWhenScrolledToTheEnd() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        setLibrary(LibraryUIState(books = (1L..40L).map { book(it) }))

        rule.onNodeWithTag(LibraryTestTags.GRID).performScrollToIndex(40)
        rule.waitForIdle()

        val lastCell = rule.onNodeWithTag(LibraryTestTags.cell(40L)).bounds()
        val overlayTop = rule.onNodeWithTag(SHELL_OVERLAY_TAG).bounds().top
        assertTrue(
            "the last cell ends at ${lastCell.bottom}, below the overlay's top at $overlayTop",
            lastCell.bottom <= overlayTop + 1f,
        )
    }

    // TC-7
    @Test
    fun tc7_coverUnderTheTopFadeStillOpens() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        val opened = mutableListOf<String>()
        var gridState: LazyGridState? = null
        var statusTop = 0
        setLibrary(
            LibraryUIState(books = (1L..40L).map { book(it) }),
            onOpenBook = { opened += it },
            onStatusTop = { statusTop = it },
            onGridState = { gridState = it },
        )
        rule.onNodeWithTag(LibraryTestTags.GRID).performScrollToIndex(20)
        rule.waitForIdle()
        val grid = checkNotNull(gridState)

        // Just below the status bar, where the top fade's eased part begins.
        val target = statusTop + 1f
        // Scroll forward so the composed cell nearest below the target moves up onto it.
        val (id, nearest) = grid.layoutInfo.visibleItemsInfo
            .mapNotNull { it.key as? Long }
            .map { id -> id to rule.onNodeWithTag(LibraryTestTags.cell(id)).bounds() }
            .filter { (_, bounds) -> bounds.center.y > target }
            .minByOrNull { (_, bounds) -> bounds.center.y }
            ?: error("no composed cell below the target $target")
        rule.runOnIdle { grid.dispatchRawDelta(nearest.center.y - target) }
        rule.waitForIdle()

        val cell = rule.onNodeWithTag(LibraryTestTags.cell(id)).bounds()
        assertTrue(
            "the precondition failed: the target $target is not inside cell $id at ${cell.top}..${cell.bottom}",
            target >= cell.top && target <= cell.bottom,
        )
        rule.onRoot().performTouchInput { click(Offset(cell.center.x, target)) }
        rule.waitForIdle()

        assertEquals(listOf("uuid-$id"), opened)
    }

    // TC-8
    @Test
    fun tc8_snackbarSitsAboveTheOverlay() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        setLibrary(LibraryUIState(books = listOf(book(1L)), transientError = CONNECT_UI_ERROR))
        rule.waitForIdle()

        val message = rule.onNodeWithText(CONNECT_ERROR).assertIsDisplayed().bounds()
        val overlayTop = rule.onNodeWithTag(SHELL_OVERLAY_TAG).bounds().top
        assertTrue(
            "the snackbar text ends at ${message.bottom}, below the overlay's top at $overlayTop",
            message.bottom <= overlayTop + 1f,
        )
    }

    /**
     * Hosts [LibraryScreenContent] in the shell's layout, under the real tab bar. [onStatusTop] receives the status bar
     * inset, and [onGridState] the hoisted grid state, both read from the composition.
     */
    private fun setLibrary(
        uiState: LibraryUIState,
        onOpenBook: (uuid: String) -> Unit = {},
        onStatusTop: (Int) -> Unit = {},
        onGridState: (LazyGridState) -> Unit = {},
    ) {
        rule.setContent {
            onStatusTop(WindowInsets.statusBars.getTop(LocalDensity.current))
            val gridState = rememberLazyGridState()
            onGridState(gridState)
            OmnibusTheme(darkTheme = true) {
                ShellLayout(overlay = { ShellTestOverlay() }, modifier = Modifier.fillMaxSize()) {
                    LibraryScreenContent(
                        uiState = uiState,
                        onRetry = {},
                        onRefresh = {},
                        onLoadMore = {},
                        onOpenBook = onOpenBook,
                        onAddBooks = {},
                        onTransientErrorShown = {},
                        gridState = gridState,
                    )
                }
            }
        }
    }

    private companion object {
        val CONNECT_UI_ERROR = LibraryUIError.Failed(LibraryError.Unreachable)
        const val CONNECT_ERROR = "Could not connect to the server."

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
    }
}
