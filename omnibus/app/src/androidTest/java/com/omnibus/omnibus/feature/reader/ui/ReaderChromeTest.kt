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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.ReaderMenuRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val TITLE = "Nineteen Eighty-Four"
private const val ROOT_TAG = "reader_chrome_test_root"

private const val UNDERLAY_TAG = "reader_chrome_test_underlay"

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
    val percent: Int = READING.pct,
    val bookmarkCount: Int = 0,
    val pageBookmarked: Boolean = false,
    val canBookmark: Boolean = true,
    val theme: ReaderTheme = ReaderTheme.Light,
    val reduceMotion: Boolean = true,
    val insets: WindowInsets = WindowInsets(),
)

// RC-25 to RC-44. Tests use tags, text and relative bounds only, and size the root as a fraction of the
// window (never in fixed dp), so they hold on the 320dp CI AVD and on a phone-sized one.
/** Callbacks, touch pass-through, motion and semantics. ReaderChromeScreenshotTest covers how each state looks. */
class ReaderChromeTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * Sets the chrome once, with the page under it. A tap on the page appends "page" to [events], and
     * each chrome callback appends its name, so a test can assert exactly what was reached.
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
                        indicators = ReaderIndicators(location = i.location, title = i.title, chromeVisible = i.chromeVisible),
                        colors = readerChromeColors(i.theme),
                        reduceMotion = i.reduceMotion,
                        insets = i.insets,
                    )
                    ReaderChrome(
                        chromeVisible = i.chromeVisible,
                        menuOpen = i.menuOpen,
                        menuRows = i.menuRows,
                        percent = i.percent,
                        bookmarkCount = i.bookmarkCount,
                        pageBookmarked = i.pageBookmarked,
                        canBookmark = i.canBookmark,
                        colors = readerChromeColors(i.theme),
                        reduceMotion = i.reduceMotion,
                        insets = i.insets,
                        onClose = { events += "close" },
                        onMenuOpened = { events += "menuOpened" },
                        onMenuDismissed = { events += "menuDismissed" },
                        onMenuRowChosen = { events += "row:${it.name}" },
                        onBookmarkToggled = { events += "bookmarkToggled" },
                    )
                }
            }
        }
    }

    // RC-27
    @Test
    fun rc27_hidingTheChromeRemovesTheButtons() {
        val inputs = mutableStateOf(
            ChromeInputs(chromeVisible = true, reduceMotion = false, menuRows = listOf(ReaderMenuRow.Contents)),
        )
        setChrome(inputs, mutableListOf())
        rule.waitForIdle()

        rule.runOnIdle { inputs.value = inputs.value.copy(chromeVisible = false) }
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderChromeTags.CLOSE).assertDoesNotExist()
        rule.onNodeWithTag(ReaderChromeTags.MENU).assertDoesNotExist()
    }

    // RC-31
    @Test
    fun rc31_labelsAndEmptyChromePassTouchesThrough() {
        val events = mutableListOf<String>()
        setChrome(mutableStateOf(ChromeInputs(chromeVisible = true)), events)

        rule.onNodeWithTag(ReaderChromeTags.LABEL_TOP).performTouchInput { click() }
        rule.onNodeWithTag(ReaderChromeTags.LABEL_BOTTOM).performTouchInput { click() }
        rule.onNodeWithTag(ROOT_TAG).performTouchInput { click() }
        rule.waitForIdle()

        assertEquals(listOf("page", "page", "page"), events)
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
            .assert(hasTestTag(ReaderChromeTags.CLOSE))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        rule.onNodeWithContentDescription("Menu")
            .assert(hasTestTag(ReaderChromeTags.MENU))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

}
