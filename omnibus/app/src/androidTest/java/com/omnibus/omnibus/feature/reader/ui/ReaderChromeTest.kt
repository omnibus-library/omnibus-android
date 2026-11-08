package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.ReaderMenuRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val TITLE = "Nineteen Eighty-Four"
private const val LONG_TITLE = "Nineteen Eighty-Four and the rest of a very long title that cannot fit on one bar line"
private const val ROOT_TAG = "reader_chrome_test_root"

private const val UNDERLAY_TAG = "reader_chrome_test_underlay"

private val ALL_ROWS = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Bookmarks, ReaderMenuRow.Settings)

/** A mid-book location: page 361 of 661, seven pages left in chapter 3 of 12, 54%. */
private val READING = RelocateData(
    page = 361,
    totalPages = 661,
    pct = 54,
    chapter = 3,
    totalChapters = 12,
    chapterPagesLeft = 7,
)

/** Everything the chrome is drawn from. Tests change it through a mutable state. */
private data class ChromeInputs(
    val location: RelocateData? = READING,
    val title: String = TITLE,
    val chromeVisible: Boolean = false,
    val menuOpen: Boolean = false,
    val menuRows: List<ReaderMenuRow> = emptyList(),
    val pageBookmarked: Boolean = false,
    val canBookmark: Boolean = true,
    val theme: ReaderTheme = ReaderTheme.Light,
    val reduceMotion: Boolean = true,
    val insets: WindowInsets = WindowInsets(),
)

// RC-25 to RC-44, RC-50 and RC-51. Tests use tags, text and relative bounds only, and size the root as a fraction of the
// window (never in fixed dp), so they hold on the 320dp CI AVD and on a phone-sized one.
/** Callbacks, touch pass-through, motion and semantics. ReaderChromeScreenshotTest covers how each state looks. */
class ReaderChromeTest {

    @get:Rule
    val rule = createComposeRule()

    /** The bar's title. The top page label shows the same title when focused, so the lookup is scoped to the bar. */
    private fun barTitle(text: String) = rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(ReaderChromeTags.BAR)))

    /**
     * Sets the chrome once, with the page under it. A tap on the page appends "page" to [events], and
     * each chrome callback appends its name, so a test can assert exactly what was reached. Tapping the more
     * button opens the dropdown, as the screen does.
     */
    private fun setChrome(inputs: MutableState<ChromeInputs>, events: MutableList<String>) {
        rule.setContent {
            val i = inputs.value
            // The chrome's app theme is the opposite of the page's, as in the reader.
            OmnibusTheme(darkTheme = i.theme.isLight) {
                Box(Modifier.fillMaxSize().testTag(ROOT_TAG)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag(UNDERLAY_TAG)
                            .pointerInput(Unit) { detectTapGestures { events += "page" } },
                    )
                    ReaderPageLabels(
                        indicators = ReaderIndicators(location = i.location, title = i.title, chromeVisible = false),
                        colors = readerChromeColors(i.theme),
                        reduceMotion = i.reduceMotion,
                        insets = i.insets,
                    )
                    ReaderChrome(
                        title = i.title,
                        chromeVisible = i.chromeVisible,
                        menuOpen = i.menuOpen,
                        menuRows = i.menuRows,
                        pageBookmarked = i.pageBookmarked,
                        canBookmark = i.canBookmark,
                        colors = readerChromeColors(i.theme),
                        reduceMotion = i.reduceMotion,
                        insets = i.insets,
                        onClose = { events += "close" },
                        onMenuOpened = {
                            events += "menuOpened"
                            inputs.value = inputs.value.copy(menuOpen = true)
                        },
                        onMenuDismissed = { events += "menuDismissed" },
                        onMenuRowChosen = { events += "row:${it.name}" },
                        onBookmarkToggled = { events += "bookmarkToggled" },
                    )
                }
            }
        }
    }

    // RC-27, TC-7
    @Test
    fun rc27_hidingTheChromeRemovesTheBar() {
        val inputs = mutableStateOf(
            ChromeInputs(chromeVisible = true, reduceMotion = false, menuRows = listOf(ReaderMenuRow.Contents)),
        )
        setChrome(inputs, mutableListOf())
        rule.waitForIdle()

        rule.runOnIdle { inputs.value = inputs.value.copy(chromeVisible = false) }
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderChromeTags.BAR).assertDoesNotExist()
        rule.onNodeWithTag(ReaderChromeTags.BACK).assertDoesNotExist()
        rule.onNodeWithTag(ReaderChromeTags.BOOKMARK).assertDoesNotExist()
        rule.onNodeWithTag(ReaderChromeTags.MORE).assertDoesNotExist()
    }

    // RC-27, TC-7
    @Test
    fun rc27b_withoutRowsThereIsNoMoreButton() {
        setChrome(mutableStateOf(ChromeInputs(chromeVisible = true, menuRows = emptyList())), mutableListOf())
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderChromeTags.MORE).assertDoesNotExist()
    }

    // RC-27, TC-7
    @Test
    fun rc27c_aClosedDropdownHasNoRows() {
        setChrome(
            mutableStateOf(ChromeInputs(chromeVisible = true, menuOpen = false, menuRows = ALL_ROWS)),
            mutableListOf(),
        )
        rule.waitForIdle()

        ALL_ROWS.forEach { rule.onNodeWithTag(ReaderChromeTags.menuRow(it)).assertDoesNotExist() }
    }

    // RC-31
    @Test
    fun rc31_labelsAndEmptyChromePassTouchesThrough() {
        val events = mutableListOf<String>()
        setChrome(mutableStateOf(ChromeInputs(chromeVisible = true)), events)

        // The top label sits under the bar in context mode, so it is not tapped here.
        rule.onNodeWithTag(ReaderChromeTags.LABEL_BOTTOM).performTouchInput { click() }
        rule.onNodeWithTag(ROOT_TAG).performTouchInput { click() }
        rule.waitForIdle()

        assertEquals(listOf("page", "page"), events)
    }

    // RC-36
    @Test
    fun rc36_rowsReportTheChosenRow() {
        val events = mutableListOf<String>()
        setChrome(
            mutableStateOf(
                ChromeInputs(
                    chromeVisible = true,
                    menuOpen = true,
                    menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Settings),
                ),
            ),
            events,
        )

        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Contents)).performClick()
        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Settings)).performClick()
        rule.waitForIdle()

        assertEquals(listOf("row:Contents", "row:Settings"), events)
    }

    // RC-37
    @Test
    fun rc37_theScrimSwallowsTheTapThatDismissesTheMenu() {
        val inputs = mutableStateOf(
            ChromeInputs(
                chromeVisible = true,
                menuOpen = true,
                menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Settings),
            ),
        )
        val events = mutableListOf<String>()
        setChrome(inputs, events)

        rule.onNodeWithTag(ReaderChromeTags.MENU_SCRIM).performClick()
        rule.waitForIdle()

        assertEquals(listOf("menuDismissed"), events)
    }

    // RC-41
    @Test
    fun rc41_controlsAreLabelledForAccessibility() {
        setChrome(
            mutableStateOf(ChromeInputs(chromeVisible = true, menuRows = listOf(ReaderMenuRow.Contents))),
            mutableListOf(),
        )

        rule.onNodeWithContentDescription("Close book")
            .assert(hasTestTag(ReaderChromeTags.BACK))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        rule.onNodeWithContentDescription("Menu")
            .assert(hasTestTag(ReaderChromeTags.MORE))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    // RC-50, TC-6: the bar reads back, title, bookmark and more, from its start to its end, inside the bar.
    @Test
    fun rc50_barIsLaidOutBackTitleBookmarkMore() {
        setChrome(
            mutableStateOf(ChromeInputs(chromeVisible = true, menuRows = listOf(ReaderMenuRow.Contents))),
            mutableListOf(),
        )
        rule.waitForIdle()

        val bar = rule.onNodeWithTag(ReaderChromeTags.BAR).fetchSemanticsNode().boundsInRoot
        val back = rule.onNodeWithTag(ReaderChromeTags.BACK).fetchSemanticsNode().boundsInRoot
        val title = barTitle(TITLE).fetchSemanticsNode().boundsInRoot
        val bookmark = rule.onNodeWithTag(ReaderChromeTags.BOOKMARK).fetchSemanticsNode().boundsInRoot
        val more = rule.onNodeWithTag(ReaderChromeTags.MORE).fetchSemanticsNode().boundsInRoot

        assertTrue("the back arrow leads the bar", back.left < title.left)
        assertTrue("the title comes before the bookmark toggle", title.right <= bookmark.left)
        assertTrue("the bookmark toggle comes before more", bookmark.right <= more.left)
        assertTrue("the buttons sit in the bar's height", listOf(back, bookmark, more).all { it.top >= bar.top && it.bottom <= bar.bottom })
    }

    // RC-50, TC-6: each bar button is a touch target of at least 48dp.
    @Test
    fun rc50b_barButtonsAreAtLeast48dp() {
        setChrome(
            mutableStateOf(ChromeInputs(chromeVisible = true, menuRows = listOf(ReaderMenuRow.Contents))),
            mutableListOf(),
        )
        rule.waitForIdle()

        listOf(ReaderChromeTags.BACK, ReaderChromeTags.BOOKMARK, ReaderChromeTags.MORE).forEach { tag ->
            val bounds = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag is at least 48dp wide", bounds.right - bounds.left >= 48.dp)
            assertTrue("$tag is at least 48dp tall", bounds.bottom - bounds.top >= 48.dp)
        }
    }

    // RC-50, TC-6: a very long title stays on one line, so the bar keeps its height.
    @Test
    fun rc50c_aLongTitleStaysOnOneLine() {
        setChrome(
            mutableStateOf(ChromeInputs(title = LONG_TITLE, chromeVisible = true, menuRows = listOf(ReaderMenuRow.Contents))),
            mutableListOf(),
        )
        rule.waitForIdle()

        val lines = mutableListOf<TextLayoutResult>()
        barTitle(LONG_TITLE).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
            .action!!.invoke(lines)
        assertEquals(1, lines.single().lineCount)
    }

    // RC-51, TC-9: the more button opens the dropdown with its three rows in order, each showing only its label.
    @Test
    fun rc51_dropdownOpensWithThreeRowsInOrder() {
        setChrome(mutableStateOf(ChromeInputs(chromeVisible = true, menuRows = ALL_ROWS)), mutableListOf())
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderChromeTags.MORE).performClick()
        rule.waitForIdle()

        val tops = ALL_ROWS.map { rule.onNodeWithTag(ReaderChromeTags.menuRow(it)).fetchSemanticsNode().boundsInRoot.top }
        assertTrue("the rows run top to bottom in order", tops.zipWithNext().all { (a, b) -> a < b })
        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Contents)).assert(hasAnyDescendant(hasText("Contents")))
        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Bookmarks)).assert(hasAnyDescendant(hasText("Bookmarks")))
        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Settings)).assert(hasAnyDescendant(hasText("Themes & Settings")))
        rule.onAllNodesWithText("%", substring = true).assertCountEquals(0)
    }

    // RC-51, TC-9: the dropdown hangs under the bar at the end edge, and is narrower than the window.
    @Test
    fun rc51b_dropdownSitsUnderTheBarAtTheEnd() {
        setChrome(mutableStateOf(ChromeInputs(chromeVisible = true, menuRows = ALL_ROWS)), mutableListOf())
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderChromeTags.MORE).performClick()
        rule.waitForIdle()

        val root = rule.onNodeWithTag(ROOT_TAG).fetchSemanticsNode().boundsInRoot
        val bar = rule.onNodeWithTag(ReaderChromeTags.BAR).fetchSemanticsNode().boundsInRoot
        val dropdown = rule.onNodeWithTag(ReaderChromeTags.DROPDOWN).fetchSemanticsNode().boundsInRoot
        val endMargin = with(rule.density) { 12.dp.toPx() }

        assertTrue("the dropdown is entirely below the bar", dropdown.top >= bar.bottom - 1f)
        assertTrue("the dropdown is inside the window", dropdown.right <= root.right)
        assertTrue("the dropdown sits about 12dp from the end edge", abs(root.right - dropdown.right - endMargin) <= 1f)
        assertTrue("the dropdown is narrower than the window", dropdown.width < root.width)
    }
}
