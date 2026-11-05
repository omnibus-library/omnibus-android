package com.omnibus.omnibus.feature.shell.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.library.LibraryUIState
import com.omnibus.omnibus.feature.library.ui.LibraryScreenContent
import com.omnibus.omnibus.feature.library.ui.LibraryTestTags
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.feature.shell.chrome.FabSpec
import com.omnibus.omnibus.feature.shell.chrome.ChromeMode
import com.omnibus.omnibus.feature.shell.chrome.chromeModeFor
import com.omnibus.omnibus.ui.components.OmnibusTabBar
import com.omnibus.omnibus.ui.components.OmnibusTabBarItem
import com.omnibus.omnibus.ui.components.OmnibusTabBarTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * The overlay's geometry and motion on a real device. Every check is relative (testing.md): it asserts alignment,
 * ordering and margins, never an absolute size, so it holds on the 320 dp CI emulator and on a wide device alike.
 */
class ChromeScaffoldTest {

    @get:Rule
    val rule = createComposeRule()

    private val chromeShown = mutableStateOf(true)
    private val fabShown = mutableStateOf(true)
    private val miniShown = mutableStateOf(false)

    @Test
    fun chromeIsAlignedForTheCurrentMode() {
        // TC-41: narrow, the bar's margins match and the FAB lines up with the bar's end; wide, the bar is at the start
        // and the FAB is centred on it at the end
        setScaffold()

        val root = rootBounds()
        val bar = slot(ChromeTestTags.BAR_SLOT)
        val fab = slot(ChromeTestTags.FAB_SLOT)
        when (chromeModeFor(root.widthDp.value)) {
            ChromeMode.Narrow -> {
                assertNear("bar margins", bar.left - root.left, root.right - bar.right)
                assertNear("FAB end edge", fab.right, bar.right)
                assertAtMost("FAB above the bar", fab.bottom, bar.top)
            }

            ChromeMode.Wide -> {
                assertNear("bar and FAB margins", bar.left - root.left, root.right - fab.right)
                assertNear("FAB centred on the bar", fab.centreY, bar.centreY)
                assertAtLeast("FAB clear of the bar", fab.left, bar.right)
            }
        }
    }

    @Test
    fun chromeMirrorsInRtl() {
        // TC-42: with right-to-left, the same rules mirror: the FAB lines up with the bar's start edge when narrow
        setScaffold(direction = LayoutDirection.Rtl)

        val root = rootBounds()
        val bar = slot(ChromeTestTags.BAR_SLOT)
        val fab = slot(ChromeTestTags.FAB_SLOT)
        when (chromeModeFor(root.widthDp.value)) {
            ChromeMode.Narrow -> {
                assertNear("FAB at the bar's end edge", fab.left, bar.left)
                assertNear("bar margins", bar.right - root.right, root.left - bar.left)
            }

            ChromeMode.Wide -> {
                assertNear("bar and FAB margins", root.right - bar.right, fab.left - root.left)
                assertAtMost("FAB clear of the bar", fab.right, bar.left)
            }
        }
    }

    @Test
    fun miniPlayerSitsAboveTheBarAtItsWidth() {
        // TC-43: the mini player takes the bar's width and sits above it; narrow, the FAB stacks above it; wide, the
        // FAB stays centred on the bar
        miniShown.value = true
        setScaffold()

        val root = rootBounds()
        val bar = slot(ChromeTestTags.BAR_SLOT)
        val mini = slot(ChromeTestTags.MINI_PLAYER_SLOT)
        val fab = slot(ChromeTestTags.FAB_SLOT)
        assertNear("mini player start edge", mini.left, bar.left)
        assertNear("mini player end edge", mini.right, bar.right)
        assertAtMost("mini player above the bar", mini.bottom, bar.top)
        when (chromeModeFor(root.widthDp.value)) {
            ChromeMode.Narrow -> assertAtMost("FAB above the mini player", fab.bottom, mini.top)
            ChromeMode.Wide -> assertNear("FAB centred on the bar", fab.centreY, bar.centreY)
        }
    }

    @Test
    fun libraryLastRowScrollsClearOfTheChrome() {
        // TC-44: the real Library grid, scrolled to its last book, clears the bar and the FAB (P-CON-AC1)
        val books = (1L..40L).map { book(it) }
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().testTag(ROOT)) {
                    ChromeScaffold(
                        chromeVisible = true,
                        fabVisible = true,
                        bar = { TestBar() },
                        fab = { TestFab() },
                    ) {
                        LibraryScreenContent(
                            uiState = LibraryUIState(books = books, hasLoaded = true),
                            onRetry = {},
                            onRefresh = {},
                            onLoadMore = {},
                            onOpenBook = {},
                            onTransientErrorShown = {},
                            bottomInset = LocalShellContentPadding.current.calculateBottomPadding(),
                        )
                    }
                }
            }
        }

        rule.onNodeWithTag(LibraryTestTags.GRID).performScrollToIndex(books.size)
        rule.waitForIdle()

        val bar = slot(ChromeTestTags.BAR_SLOT)
        val fab = slot(ChromeTestTags.FAB_SLOT)
        val lastCell = rule.onNodeWithTag(LibraryTestTags.cell(books.last().id)).getBoundsInRoot()
        assertAtMost("the last row clears the bar", lastCell.bottom, bar.top)
        assertAtMost("the last row clears the FAB", lastCell.bottom, fab.top)
    }

    @Test
    fun marginsPassTouchesThroughButTheBarDoesNot() {
        // TC-45: a tap in the bar's margin reaches content underneath; a tap on the bar's own surface doesn't
        var contentTaps = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().testTag(ROOT)) {
                    ChromeScaffold(
                        chromeVisible = true,
                        fabVisible = true,
                        bar = { TestBar() },
                        fab = { TestFab() },
                    ) {
                        Box(Modifier.fillMaxSize().clickable { contentTaps++ })
                    }
                }
            }
        }
        rule.waitForIdle()

        val root = rootBounds()
        val bar = slot(ChromeTestTags.BAR_SLOT)
        val density = rule.density
        val centreX = with(density) { ((bar.left + bar.right) / 2 - root.left).toPx() }
        val aboveBar = with(density) { (bar.top - 6.dp - root.top).toPx() }
        val barEdgeX = with(density) { (bar.left + 3.dp - root.left).toPx() }
        val barCentreY = with(density) { (bar.centreY - root.top).toPx() }

        rule.onNodeWithTag(ROOT).performTouchInput { click(Offset(centreX, aboveBar)) }
        rule.waitForIdle()
        assertEquals("a tap in the margin should reach the content", 1, contentTaps)

        rule.onNodeWithTag(ROOT).performTouchInput { click(Offset(barEdgeX, barCentreY)) }
        rule.waitForIdle()
        assertEquals("a tap on the bar should stop at the bar", 1, contentTaps)
    }

    @Test
    fun hiddenChromeLeavesTheTreeAndComesBack() {
        // TC-46: hidden, the bar and FAB slots leave the tree; shown again, the bar is displayed
        setScaffold()
        rule.onNodeWithTag(OmnibusTabBarTestTags.BAR).assertIsDisplayed()

        rule.runOnIdle { chromeShown.value = false }
        rule.waitForIdle()
        rule.onNodeWithTag(ChromeTestTags.BAR_SLOT).assertDoesNotExist()
        rule.onNodeWithTag(ChromeTestTags.FAB_SLOT).assertDoesNotExist()

        rule.runOnIdle { chromeShown.value = true }
        rule.waitForIdle()
        rule.onNodeWithTag(OmnibusTabBarTestTags.BAR).assertIsDisplayed()
    }

    @Test
    fun hidingTheFabKeepsTheBarWidth() {
        // TC-47: the FAB leaves, the bar keeps its width (P-LAY-5)
        setScaffold()
        val widthWithFab = slot(ChromeTestTags.BAR_SLOT).widthDp

        rule.runOnIdle { fabShown.value = false }
        rule.waitForIdle()

        rule.onNodeWithContentDescription(FAB_LABEL).assertDoesNotExist()
        assertNear("bar width", slot(ChromeTestTags.BAR_SLOT).widthDp, widthWithFab)
    }

    /** Sets the scaffold with the shell's bar, the Add books FAB and an empty content area. */
    private fun setScaffold(
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                OmnibusTheme(darkTheme = true) {
                    Box(Modifier.fillMaxSize().testTag(ROOT)) {
                        ChromeScaffold(
                            chromeVisible = chromeShown.value,
                            fabVisible = fabShown.value,
                            bar = { TestBar() },
                            fab = { TestFab() },
                            miniPlayer = {
                                if (miniShown.value) Box(Modifier.fillMaxWidth().height(56.dp))
                            },
                        ) {
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TestBar() {
        val items = ShellTab.entries.map { OmnibusTabBarItem(stringResource(it.label), it.icon, it.selectedIcon) }
        OmnibusTabBar(items = items, selectedIndex = 0, onSelect = {}, modifier = Modifier.fillMaxWidth())
    }

    @Composable
    private fun TestFab() {
        ShellFab(FabSpec(R.drawable.ic_add_book, R.string.masthead_add_books) {})
    }

    private fun rootBounds(): DpRect = rule.onNodeWithTag(ROOT).getBoundsInRoot()

    private fun slot(tag: String): DpRect = rule.onNodeWithTag(tag).getBoundsInRoot()

    private val DpRect.widthDp: Dp get() = right - left

    private val DpRect.centreY: Dp get() = (top + bottom) / 2

    /** Asserts [actual] is within half a dp of [expected]. */
    private fun assertNear(label: String, actual: Dp, expected: Dp) {
        assertTrue("$label: $actual should match $expected", abs((actual - expected).value) <= HALF_DP.value)
    }

    /** Asserts [actual] is at most [limit], with half a dp of slack. */
    private fun assertAtMost(label: String, actual: Dp, limit: Dp) {
        assertTrue("$label: $actual should be at most $limit", actual.value <= limit.value + HALF_DP.value)
    }

    /** Asserts [actual] is at least [limit], with half a dp of slack. */
    private fun assertAtLeast(label: String, actual: Dp, limit: Dp) {
        assertTrue("$label: $actual should be at least $limit", actual.value >= limit.value - HALF_DP.value)
    }

    /** A book with a uuid of `uuid-<id>` and no cover, so no image loads here. */
    private fun book(id: Long) = Book(
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

    private companion object {
        const val ROOT = "chrome-test-root"
        const val FAB_LABEL = "Add books by upload or barcode scan"

        /** Half a dp of slack, to absorb sub-pixel rounding in the measured bounds. */
        val HALF_DP = 0.5.dp
    }
}
