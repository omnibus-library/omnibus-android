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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.ReaderMenuRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
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

    // RC-26
    @Test
    fun rc26_pagesLeftLabelFollowsThePlural() {
        val inputs = mutableStateOf(ChromeInputs(chromeVisible = true, menuRows = listOf(ReaderMenuRow.Contents)))
        setChrome(inputs, mutableListOf())

        rule.onNodeWithTag(ReaderChromeTags.LABEL_TOP).assertTextEquals("7 pages left in chapter")

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
    fun bookmarksRowReportsItsTap() {
        val events = mutableListOf<String>()
        setChrome(
            mutableStateOf(ChromeInputs(chromeVisible = true, menuOpen = true, menuRows = ALL_ROWS, bookmarkCount = 3, percent = 42)),
            events,
        )

        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Bookmarks)).performClick()
        rule.waitForIdle()
        assertEquals(listOf("row:Bookmarks"), events)
    }
}
