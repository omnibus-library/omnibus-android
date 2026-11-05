package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.ReaderMenuRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val TITLE = "Nineteen Eighty-Four"
private const val ROOT_TAG = "reader_chrome_test_root"

private const val UNDERLAY_TAG = "reader_chrome_test_underlay"

/** The three rows the ViewModel offers, in its order. */
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
    val percent: Int = READING.pct,
    val bookmarkCount: Int = 0,
    val pageBookmarked: Boolean = false,
    val canBookmark: Boolean = true,
    val theme: ReaderTheme = ReaderTheme.Light,
    val reduceMotion: Boolean = true,
    val insets: WindowInsets = WindowInsets(),
    /** The width of the window the chrome sits in, or null to fill the test's window. */
    val rootWidthFraction: Float? = null,
)

/** One preview under test: a theme and chrome state. */
private data class PreviewCase(
    val theme: ReaderTheme,
    val chromeVisible: Boolean,
    val menuOpen: Boolean = false,
    val menuRows: List<ReaderMenuRow> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
)

// RC-25 to RC-44. Tests use tags, text and relative bounds only, and size the root as a fraction of the
// window (never in fixed dp), so they hold on the 320dp CI AVD and on a phone-sized one.
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
            val root = i.rootWidthFraction?.let { Modifier.fillMaxWidth(it).fillMaxHeight() } ?: Modifier.fillMaxSize()
            // The chrome's app theme is the opposite of the page's, as in the reader.
            OmnibusTheme(darkTheme = i.theme.isLight) {
                Box(root.testTag(ROOT_TAG)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag(UNDERLAY_TAG)
                            .pointerInput(Unit) { detectTapGestures { events += "page" } },
                    )
                    ReaderChrome(
                        indicators = ReaderIndicators(location = i.location, title = i.title, chromeVisible = i.chromeVisible),
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

    // RC-25
    @Test
    fun rc25_hiddenChromeShowsOnlyTheTwoLabels() {
        setChrome(mutableStateOf(ChromeInputs()), mutableListOf())

        rule.onNodeWithTag(ReaderChromeTags.LABEL_TOP).assertTextEquals(TITLE)
        rule.onNodeWithTag(ReaderChromeTags.LABEL_BOTTOM).assertTextEquals("361")
        rule.onNodeWithTag(ReaderChromeTags.CLOSE).assertDoesNotExist()
        rule.onNodeWithTag(ReaderChromeTags.MENU).assertDoesNotExist()
    }

    // RC-26
    @Test
    fun rc26_visibleChromeShowsButtonsAndNavigatingLabels() {
        val inputs = mutableStateOf(ChromeInputs(chromeVisible = true, menuRows = listOf(ReaderMenuRow.Contents)))
        setChrome(inputs, mutableListOf())

        rule.onNodeWithTag(ReaderChromeTags.CLOSE).assertIsDisplayed()
        rule.onNodeWithTag(ReaderChromeTags.MENU).assertIsDisplayed()
        rule.onNodeWithTag(ReaderChromeTags.LABEL_TOP).assertTextEquals("7 pages left in chapter")
        rule.onNodeWithTag(ReaderChromeTags.LABEL_BOTTOM).assertTextEquals("361 of 661")

        rule.runOnIdle { inputs.value = inputs.value.copy(location = READING.copy(chapterPagesLeft = 1)) }
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderChromeTags.LABEL_TOP).assertTextEquals("1 page left in chapter")
    }

    // RC-27
    @Test
    fun rc27_hidingTheChromeRemovesButtonsAndRestoresLabels() {
        val inputs = mutableStateOf(
            ChromeInputs(chromeVisible = true, reduceMotion = false, menuRows = listOf(ReaderMenuRow.Contents)),
        )
        setChrome(inputs, mutableListOf())
        rule.waitForIdle()

        rule.runOnIdle { inputs.value = inputs.value.copy(chromeVisible = false) }
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderChromeTags.CLOSE).assertDoesNotExist()
        rule.onNodeWithTag(ReaderChromeTags.MENU).assertDoesNotExist()
        rule.onNodeWithTag(ReaderChromeTags.LABEL_TOP).assertTextEquals(TITLE)
        rule.onNodeWithTag(ReaderChromeTags.LABEL_BOTTOM).assertTextEquals("361")
    }

    // RC-28
    @Test
    fun rc28_fallbackLabelsAndNoPageZero() {
        val inputs = mutableStateOf(
            ChromeInputs(
                location = RelocateData(totalPages = 0, pct = 18, chapter = 3, totalChapters = 12, chapterPagesLeft = 0),
                chromeVisible = true,
            ),
        )
        setChrome(inputs, mutableListOf())

        rule.onNodeWithTag(ReaderChromeTags.LABEL_TOP).assertTextEquals("Chapter 3 of 12")
        rule.onNodeWithTag(ReaderChromeTags.LABEL_BOTTOM).assertTextEquals("18%")

        rule.runOnIdle {
            inputs.value = inputs.value.copy(
                location = RelocateData(page = 1, totalPages = 0, pct = 0, chapter = 3, totalChapters = 12),
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderChromeTags.LABEL_BOTTOM).assertDoesNotExist()
        rule.onAllNodesWithText("1").assertCountEquals(0)
        rule.onAllNodesWithText("0%").assertCountEquals(0)

        rule.runOnIdle { inputs.value = inputs.value.copy(location = null) }
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderChromeTags.LABEL_TOP).assertDoesNotExist()
        rule.onNodeWithTag(ReaderChromeTags.LABEL_BOTTOM).assertDoesNotExist()
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

    // RC-32
    @Test
    fun rc32_closeButtonCallsOnClose() {
        val events = mutableListOf<String>()
        setChrome(mutableStateOf(ChromeInputs(chromeVisible = true)), events)

        rule.onNodeWithTag(ReaderChromeTags.CLOSE).performClick()
        rule.waitForIdle()

        assertEquals(listOf("close"), events)
    }

    // RC-33
    @Test
    fun rc33_menuButtonHiddenWithoutRows() {
        setChrome(mutableStateOf(ChromeInputs(chromeVisible = true, menuRows = emptyList())), mutableListOf())

        rule.onNodeWithTag(ReaderChromeTags.CLOSE).assertIsDisplayed()
        rule.onNodeWithTag(ReaderChromeTags.MENU).assertDoesNotExist()
        ReaderMenuRow.entries.forEach { row ->
            rule.onNodeWithTag(ReaderChromeTags.menuRow(row)).assertDoesNotExist()
        }
    }

    @Test
    fun menuOffersThemesAndSettingsAndTappingItOpensTheSheetOnce() {
        val events = mutableListOf<String>()
        setChrome(
            mutableStateOf(ChromeInputs(chromeVisible = true, menuOpen = true, menuRows = listOf(ReaderMenuRow.Settings))),
            events,
        )

        rule.onNodeWithText("Themes & Settings").assertIsDisplayed()
        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Settings)).performClick()

        assertEquals(listOf("row:Settings"), events)
    }

    // RC-34
    @Test
    fun rc34_menuButtonOpensTheMenu() {
        val events = mutableListOf<String>()
        setChrome(
            mutableStateOf(ChromeInputs(chromeVisible = true, menuRows = ALL_ROWS)),
            events,
        )

        rule.onNodeWithTag(ReaderChromeTags.MENU).performClick()
        rule.waitForIdle()

        assertEquals(listOf("menuOpened"), events)
        ReaderMenuRow.entries.forEach { row ->
            rule.onNodeWithTag(ReaderChromeTags.menuRow(row)).assertDoesNotExist()
        }
    }

    // RC-35
    @Test
    fun rc35_openMenuStacksRowsAboveTheButtonInListOrder() {
        val inputs = mutableStateOf(
            ChromeInputs(
                chromeVisible = true,
                menuOpen = true,
                menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Settings),
                percent = 54,
            ),
        )
        setChrome(inputs, mutableListOf())

        val contents = ReaderChromeTags.menuRow(ReaderMenuRow.Contents)
        val settings = ReaderChromeTags.menuRow(ReaderMenuRow.Settings)
        rule.onNodeWithTag(contents).assert(hasText("Contents")).assert(hasText("54%"))
        rule.onNodeWithTag(settings).assert(hasText("Themes & Settings"))

        var first = rule.onNodeWithTag(contents).getUnclippedBoundsInRoot()
        var second = rule.onNodeWithTag(settings).getUnclippedBoundsInRoot()
        var menu = rule.onNodeWithTag(ReaderChromeTags.MENU).getUnclippedBoundsInRoot()
        assertTrue("Contents sits above Settings", first.bottom <= second.top)
        assertTrue("Settings sits above the menu button", second.bottom <= menu.top)
        assertTrue("rows end at the menu button's right edge", abs((first.right - menu.right).value) <= 1f)
        assertTrue("rows end at the menu button's right edge", abs((second.right - menu.right).value) <= 1f)

        rule.runOnIdle { inputs.value = inputs.value.copy(menuRows = listOf(ReaderMenuRow.Settings, ReaderMenuRow.Contents)) }
        rule.waitForIdle()
        first = rule.onNodeWithTag(settings).getUnclippedBoundsInRoot()
        second = rule.onNodeWithTag(contents).getUnclippedBoundsInRoot()
        menu = rule.onNodeWithTag(ReaderChromeTags.MENU).getUnclippedBoundsInRoot()
        assertTrue("a reversed list reverses the rows", first.bottom <= second.top)
        assertTrue("the last row sits above the menu button", second.bottom <= menu.top)

        rule.runOnIdle { inputs.value = inputs.value.copy(percent = 0) }
        rule.waitForIdle()
        rule.onNodeWithTag(contents).assert(hasText("0%"))
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
    fun rc37_scrimExistsOnlyWhileOpenAndSwallowsTheTap() {
        val inputs = mutableStateOf(
            ChromeInputs(
                chromeVisible = true,
                menuOpen = false,
                menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Settings),
            ),
        )
        val events = mutableListOf<String>()
        setChrome(inputs, events)

        rule.onNodeWithTag(ReaderChromeTags.MENU_SCRIM).assertDoesNotExist()

        rule.runOnIdle { inputs.value = inputs.value.copy(menuOpen = true) }
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderChromeTags.MENU_SCRIM).performClick()
        rule.waitForIdle()

        assertEquals(listOf("menuDismissed"), events)
    }

    // RC-39
    @Test
    fun rc39_reducedMotionChangesOnTheNextFrame() {
        rule.mainClock.autoAdvance = false
        val inputs = mutableStateOf(
            ChromeInputs(
                reduceMotion = true,
                menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Settings),
            ),
        )
        setChrome(inputs, mutableListOf())

        rule.runOnIdle { inputs.value = inputs.value.copy(chromeVisible = true) }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(ReaderChromeTags.CLOSE).assertExists()
        rule.onNodeWithTag(ReaderChromeTags.LABEL_TOP).assertTextEquals("7 pages left in chapter")

        rule.runOnIdle { inputs.value = inputs.value.copy(menuOpen = true) }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Contents)).assertExists()
        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Settings)).assertExists()

        rule.runOnIdle { inputs.value = inputs.value.copy(chromeVisible = false, menuOpen = false) }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(ReaderChromeTags.CLOSE).assertDoesNotExist()
        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Contents)).assertDoesNotExist()
        rule.onNodeWithTag(ReaderChromeTags.LABEL_TOP).assertTextEquals(TITLE)
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

    // RC-44
    @Test
    fun rc44_previewHelperComposesForEveryThemeAndState() {
        val cases = ReaderTheme.entries.flatMap { theme ->
            listOf(
                PreviewCase(theme, chromeVisible = false),
                PreviewCase(theme, chromeVisible = true),
            )
        } + listOf(
            PreviewCase(ReaderTheme.Light, chromeVisible = true, menuOpen = true, menuRows = ALL_ROWS),
            PreviewCase(ReaderTheme.Dark, chromeVisible = true, menuOpen = true, menuRows = ALL_ROWS),
            PreviewCase(
                ReaderTheme.Light,
                chromeVisible = true,
                menuOpen = true,
                menuRows = ALL_ROWS,
                bookmarks = ReaderChromePreviewBookmarks,
            ),
            PreviewCase(
                ReaderTheme.Dark,
                chromeVisible = true,
                menuOpen = true,
                menuRows = ALL_ROWS,
                bookmarks = ReaderChromePreviewBookmarks,
            ),
        )
        val index = mutableStateOf(0)
        rule.setContent {
            val case = cases[index.value]
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ReaderChromePreview(
                    theme = case.theme,
                    chromeVisible = case.chromeVisible,
                    menuOpen = case.menuOpen,
                    menuRows = case.menuRows,
                    bookmarks = case.bookmarks,
                )
            }
        }

        cases.indices.forEach { i ->
            rule.runOnIdle { index.value = i }
            rule.waitForIdle()
            rule.onNodeWithTag(ReaderChromeTags.LABEL_BOTTOM).assertExists()
        }
    }

    // TC-56
    @Test
    fun bookmarkQuickActionAddsFromAnUnbookmarkedPage() {
        val events = mutableListOf<String>()
        setChrome(mutableStateOf(ChromeInputs(chromeVisible = true, menuOpen = true, menuRows = ALL_ROWS)), events)

        rule.onNodeWithContentDescription("Add bookmark")
            .assert(hasTestTag(ReaderChromeTags.BOOKMARK))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickAction())
            .performClick()
        rule.waitForIdle()

        assertEquals(listOf("bookmarkToggled"), events)
    }

    // TC-57
    @Test
    fun bookmarkQuickActionShowsRemoveWhenThePageIsBookmarked() {
        val inputs = mutableStateOf(ChromeInputs(chromeVisible = true, menuOpen = true, menuRows = ALL_ROWS, pageBookmarked = true))
        val events = mutableListOf<String>()
        setChrome(inputs, events)

        rule.onNodeWithContentDescription("Remove bookmark").assert(hasTestTag(ReaderChromeTags.BOOKMARK)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Add bookmark").assertCountEquals(0)

        rule.runOnIdle { inputs.value = inputs.value.copy(pageBookmarked = false) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Add bookmark").assert(hasTestTag(ReaderChromeTags.BOOKMARK))
        rule.onAllNodesWithContentDescription("Remove bookmark").assertCountEquals(0)

        assertEquals(listOf("bookmarkToggled"), events)
    }

    // TC-58
    @Test
    fun bookmarkQuickActionOnlyShowsWhileTheMenuIsOpen() {
        val inputs = mutableStateOf(ChromeInputs(chromeVisible = true, menuRows = ALL_ROWS, reduceMotion = true))
        setChrome(inputs, mutableListOf())

        for (reduceMotion in listOf(true, false)) {
            rule.runOnIdle { inputs.value = inputs.value.copy(reduceMotion = reduceMotion, menuOpen = false) }
            rule.waitForIdle()
            rule.onNodeWithTag(ReaderChromeTags.BOOKMARK).assertDoesNotExist()

            rule.runOnIdle { inputs.value = inputs.value.copy(menuOpen = true) }
            rule.waitForIdle()
            rule.onNodeWithTag(ReaderChromeTags.BOOKMARK).assertExists()

            rule.runOnIdle { inputs.value = inputs.value.copy(menuOpen = false) }
            rule.waitForIdle()
            rule.onNodeWithTag(ReaderChromeTags.BOOKMARK).assertDoesNotExist()
        }
    }

    // TC-59
    @Test
    fun bookmarkQuickActionIsDisabledWithoutACfi() {
        val events = mutableListOf<String>()
        setChrome(
            mutableStateOf(ChromeInputs(chromeVisible = true, menuOpen = true, menuRows = ALL_ROWS, canBookmark = false)),
            events,
        )

        rule.onNodeWithTag(ReaderChromeTags.BOOKMARK)
            .assertIsNotEnabled()
            .assert(hasContentDescription("Add bookmark"))
            .performClick()
        rule.waitForIdle()

        assertEquals(emptyList<String>(), events)
    }

    // TC-62
    @Test
    fun bookmarksRowSitsBetweenContentsAndSettingsWithItsCount() {
        val events = mutableListOf<String>()
        setChrome(
            mutableStateOf(ChromeInputs(chromeVisible = true, menuOpen = true, menuRows = ALL_ROWS, bookmarkCount = 3, percent = 42)),
            events,
        )

        val contents = ReaderChromeTags.menuRow(ReaderMenuRow.Contents)
        val bookmarks = ReaderChromeTags.menuRow(ReaderMenuRow.Bookmarks)
        val settings = ReaderChromeTags.menuRow(ReaderMenuRow.Settings)
        rule.onNodeWithTag(bookmarks).assert(hasText("Bookmarks")).assert(hasText("3"))

        val top = rule.onNodeWithTag(contents).getUnclippedBoundsInRoot()
        val middle = rule.onNodeWithTag(bookmarks).getUnclippedBoundsInRoot()
        val bottom = rule.onNodeWithTag(settings).getUnclippedBoundsInRoot()
        assertTrue("Contents sits above Bookmarks", top.bottom <= middle.top)
        assertTrue("Bookmarks sits above Themes & Settings", middle.bottom <= bottom.top)

        rule.onNodeWithTag(bookmarks).performClick()
        rule.waitForIdle()
        assertEquals(listOf("row:Bookmarks"), events)
    }

    // TC-63
    @Test
    fun bookmarksRowCountAppearsAndDisappearsWithTheCount() {
        val inputs = mutableStateOf(ChromeInputs(chromeVisible = true, menuOpen = true, menuRows = ALL_ROWS, bookmarkCount = 0))
        setChrome(inputs, mutableListOf())
        val bookmarks = ReaderChromeTags.menuRow(ReaderMenuRow.Bookmarks)

        rule.onNodeWithTag(bookmarks).assert(hasText("Bookmarks"))
        rule.onAllNodesWithText("0").assertCountEquals(0)

        rule.runOnIdle { inputs.value = inputs.value.copy(bookmarkCount = 1) }
        rule.waitForIdle()
        rule.onNodeWithTag(bookmarks).assert(hasText("1"))

        rule.runOnIdle { inputs.value = inputs.value.copy(bookmarkCount = 0) }
        rule.waitForIdle()
        rule.onAllNodesWithText("1").assertCountEquals(0)
    }
}
