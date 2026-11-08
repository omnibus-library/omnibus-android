package com.omnibus.omnibus.feature.reader.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.feature.library.LibraryUIError
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.feature.reader.ReaderCommand
import com.omnibus.omnibus.feature.reader.ReaderMenuRow
import com.omnibus.omnibus.feature.reader.ReaderOpening
import com.omnibus.omnibus.feature.reader.ReaderSheet
import com.omnibus.omnibus.feature.reader.ReaderStart
import com.omnibus.omnibus.feature.reader.ReaderUIState
import com.omnibus.omnibus.feature.reader.ReaderUnavailableReason
import com.omnibus.omnibus.feature.reader.withContextLeft
import com.omnibus.omnibus.feature.reader.withMenuDismissed
import com.omnibus.omnibus.feature.reader.withMenuOpened
import com.omnibus.omnibus.feature.reader.withMenuRowChosen
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

// TC-64 to TC-81 (the screen cases). Tests assert on text and test tags, never on pixel positions or
// fixed dp, so they hold on the 320dp CI AVD.
/** Wiring, back handling, insets and the WebView. ReaderScreenScreenshotTest covers how the loading and error states look. */
class ReaderScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val epub = TestEpub.build()

    private val epubLoader = ReaderAssetLoaderFactory(
        context,
        Dispatchers.Unconfined,
        BookFileSource { BookFileResult.Opened(ByteArrayInputStream(epub), epub.size.toLong()) },
    )

    private val noBookLoader = ReaderAssetLoaderFactory(
        context,
        Dispatchers.Unconfined,
        BookFileSource { BookFileResult.NotFound },
    )

    /**
     * Sets the screen with [uiState] read on every composition, so a change to a mutable state the
     * lambda reads shows up on screen without another setContent.
     */
    private fun show(
        uiState: () -> ReaderUIState,
        controller: ReaderController = ReaderController(),
        assetLoaderFactory: ReaderAssetLoaderFactory = noBookLoader,
        commands: Flow<ReaderCommand> = emptyFlow(),
        inspection: Boolean = false,
        onPageEvent: (ReaderEvent) -> Unit = {},
        onSheetDismissed: () -> Unit = {},
        onTocEntryChosen: (TocEntry) -> Unit = {},
        onMenuOpened: () -> Unit = {},
        onMenuDismissed: () -> Unit = {},
        onMenuRowChosen: (ReaderMenuRow) -> Unit = {},
        onRetryBookRequested: () -> Unit = {},
        onReaderClosed: () -> Unit = {},
        onReaderStopped: () -> Unit = {},
        onSyncOfferAccepted: () -> Unit = {},
        onSyncOfferDismissed: () -> Unit = {},
        onClose: () -> Unit = {},
        onBookmarkToggled: () -> Unit = {},
        onBookmarkChosen: (Bookmark) -> Unit = {},
        onBookmarksRetryRequested: () -> Unit = {},
        onContextLeft: () -> Unit = {},
        /** The dock below the page; empty, as in the app without a book in the player. */
        bottomDock: @Composable () -> Unit = {},
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides inspection) {
                OmnibusTheme {
                    ReaderScreenContent(
                        uiState = uiState(),
                        bookUuid = "book-1",
                        controller = controller,
                        assetLoaderFactory = assetLoaderFactory,
                        commands = commands,
                        onPageEvent = onPageEvent,
                        onSheetDismissed = onSheetDismissed,
                        onTocEntryChosen = onTocEntryChosen,
                        onMenuOpened = onMenuOpened,
                        onMenuDismissed = onMenuDismissed,
                        onMenuRowChosen = onMenuRowChosen,
                        onRetryBookRequested = onRetryBookRequested,
                        onReaderClosed = onReaderClosed,
                        onReaderStopped = onReaderStopped,
                        onSyncOfferAccepted = onSyncOfferAccepted,
                        onSyncOfferDismissed = onSyncOfferDismissed,
                        onClose = onClose,
                        onBookmarkToggled = onBookmarkToggled,
                        onBookmarkChosen = onBookmarkChosen,
                        onBookmarksRetryRequested = onBookmarksRetryRequested,
                        onContextLeft = onContextLeft,
                        bottomDock = bottomDock,
                    )
                }
            }
        }
    }

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    }

    private fun findWebView(view: View = rule.activity.window.decorView): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    /** Runs [script] in the page and returns its raw JSON result, or "" if it never answers. */
    private fun evaluate(script: String): String {
        val result = arrayOfNulls<String>(1)
        rule.runOnUiThread { findWebView()?.evaluateJavascript(script) { result[0] = it } }
        val deadline = System.currentTimeMillis() + 10_000
        while (result[0] == null && System.currentTimeMillis() < deadline) Thread.sleep(50)
        return result[0].orEmpty()
    }

    /** The page's `--inset-top` in CSS px, or null while the page hasn't been given one. */
    private fun cssInset(name: String): Int? {
        val raw = evaluate("getComputedStyle(document.documentElement).getPropertyValue('$name')")
        return Regex("-?\\d+").find(raw)?.value?.toInt()
    }

    // TC-67 (instrumented half): a Display command reaches the controller's page exactly once.
    @Test
    fun tc67_displayCommandsReachTheController() {
        val scripts = mutableListOf<String>()
        val controller = ReaderController().apply { attach { scripts += it } }
        val commands = Channel<ReaderCommand>(Channel.BUFFERED)
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Loading) },
            controller = controller,
            commands = commands.receiveAsFlow(),
        )

        commands.trySend(ReaderCommand.Display("chapter2.xhtml"))
        rule.waitUntil { scripts.isNotEmpty() }

        assertEquals(listOf(ReaderScripts.display("chapter2.xhtml")), scripts)
    }

    // TC-70
    @Test
    fun tc70_thePageInsetVarsFollowTheWindowInsets() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        val controller = ReaderController()
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Ready(title = "Test Book")) },
            controller = controller,
            assetLoaderFactory = epubLoader,
        )
        rule.waitUntil(timeoutMillis = 30_000) { controller.status == ReaderLoadStatus.Ready }

        val decor = rule.activity.window.decorView
        val bars = ViewCompat.getRootWindowInsets(decor)!!
            .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val density = rule.activity.resources.displayMetrics.density
        rule.waitUntil(timeoutMillis = 10_000) { cssInset("--inset-top") != null }

        val cssTop = cssInset("--inset-top")!!
        val cssBottom = cssInset("--inset-bottom")!!
        // The page rounds each inset to CSS px, so allow half a CSS px plus a pixel of slack.
        val tolerance = density / 2 + 1

        if (bars.top == 0) {
            assertEquals(0, cssTop)
        } else {
            assumeTrue(bars.top > 0)
            assertTrue(cssTop > 0)
            assertTrue(abs(cssTop * density - bars.top) <= tolerance)
        }
        if (bars.bottom == 0) {
            assertEquals(0, cssBottom)
        } else {
            assertTrue(cssBottom > 0)
            assertTrue(abs(cssBottom * density - bars.bottom) <= tolerance)
        }
    }

    // TC-71
    @Test
    fun tc71_systemBarAppearanceFollowsTheThemeAndIsRestored() {
        val window = rule.activity.window
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        // Start with status light and navigation dark, so the restore is distinct from both themes.
        rule.runOnUiThread {
            bars.isAppearanceLightStatusBars = true
            bars.isAppearanceLightNavigationBars = false
        }
        val theme = mutableStateOf(ReaderTheme.Dark)
        val shown = mutableStateOf(true)
        rule.setContent {
            if (shown.value) {
                OmnibusTheme {
                    ReaderScreenContent(
                        uiState = ReaderUIState(
                            book = ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound),
                            settings = ReaderSettings(theme = theme.value),
                        ),
                        bookUuid = "book-1",
                        controller = ReaderController(),
                        assetLoaderFactory = noBookLoader,
                        commands = emptyFlow(),
                        onPageEvent = {},
                        onSheetDismissed = {},
                        onTocEntryChosen = {},
                        onMenuOpened = {},
                        onMenuDismissed = {},
                        onMenuRowChosen = {},
                        onRetryBookRequested = {},
                        onReaderClosed = {},
                        onReaderStopped = {},
                        onSyncOfferAccepted = {},
                        onSyncOfferDismissed = {},
                        onClose = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        assertEquals(false, bars.isAppearanceLightStatusBars)
        assertEquals(false, bars.isAppearanceLightNavigationBars)

        rule.runOnIdle { theme.value = ReaderTheme.Light }
        rule.waitForIdle()
        assertEquals(true, bars.isAppearanceLightStatusBars)
        assertEquals(true, bars.isAppearanceLightNavigationBars)

        rule.runOnIdle { shown.value = false }
        rule.waitForIdle()
        assertEquals(true, bars.isAppearanceLightStatusBars)
        assertEquals(false, bars.isAppearanceLightNavigationBars)
    }

    // TC-78
    @Test
    fun tc78_theBackActionLeavesFromNotFound() {
        val closes = mutableListOf<String>()
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound)) },
            onClose = { closes += "close" },
        )

        rule.onNodeWithText("Back").performClick()
        rule.waitForIdle()

        assertEquals(listOf("close"), closes)
    }

    // TC-79
    @Test
    fun tc79_tryAgainRetriesAFailedBook() {
        val retries = mutableListOf<String>()
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Failed(LibraryUIError.Failed(LibraryError.Unreachable))) },
            onRetryBookRequested = { retries += "retry" },
        )

        rule.onNodeWithText("Try again").performClick()
        rule.waitForIdle()

        assertEquals(listOf("retry"), retries)
    }

    /** The chrome's nodes, none of which may be drawn over a page that isn't Ready. */
    private fun assertNoChrome() {
        listOf(
            ReaderChromeTags.BAR,
            ReaderChromeTags.BACK,
            ReaderChromeTags.MORE,
            ReaderChromeTags.LABEL_TOP,
            ReaderChromeTags.LABEL_BOTTOM,
            ReaderChromeTags.MENU_SCRIM,
        ).forEach { rule.onNodeWithTag(it).assertDoesNotExist() }
    }

    // RC-45, TC-12
    @Test
    fun rc45_backArrowLeavesThroughTheBackPath() {
        val events = mutableListOf<String>()
        show(
            uiState = {
                ReaderUIState(
                    book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
                    loadStatus = ReaderLoadStatus.Ready,
                    chromeVisible = true,
                )
            },
            inspection = true,
            onReaderClosed = { events += "closed" },
            onClose = { events += "close" },
        )

        rule.onNodeWithTag(ReaderChromeTags.BACK).performClick()
        rule.waitForIdle()

        assertEquals(listOf("closed", "close"), events)
    }

    // RC-47, TC-11
    @Test
    fun rc47_choosingARowRaisesTheSheet() {
        val state = mutableStateOf(
            ReaderUIState(
                book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
                loadStatus = ReaderLoadStatus.Ready,
                chromeVisible = true,
                menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Bookmarks, ReaderMenuRow.Settings),
            ),
        )
        show(
            uiState = { state.value },
            inspection = true,
            onMenuOpened = { state.value = state.value.withMenuOpened() },
            onMenuDismissed = { state.value = state.value.withMenuDismissed() },
            onMenuRowChosen = { state.value = state.value.withMenuRowChosen(it) },
        )

        listOf(ReaderMenuRow.Contents, ReaderMenuRow.Bookmarks, ReaderMenuRow.Settings).forEach { row ->
            rule.onNodeWithTag(ReaderChromeTags.MORE).performClick()
            rule.waitForIdle()

            rule.onNodeWithTag(ReaderChromeTags.menuRow(row)).performClick()
            rule.waitForIdle()

            rule.onNodeWithTag(ReaderChromeTags.DROPDOWN).assertDoesNotExist()
            when (row) {
                ReaderMenuRow.Contents -> {
                    rule.onNodeWithTag(ReaderContentsTags.SHEET).assertIsDisplayed()
                    rule.onNodeWithTag(ReaderContentsTags.segment(ReaderContentsTab.Contents)).assertIsSelected()
                }
                ReaderMenuRow.Bookmarks -> {
                    rule.onNodeWithTag(ReaderContentsTags.SHEET).assertIsDisplayed()
                    rule.onNodeWithTag(ReaderContentsTags.segment(ReaderContentsTab.Bookmarks)).assertIsSelected()
                }
                ReaderMenuRow.Settings -> rule.onNodeWithTag(ReaderSettingsSheetTags.SHEET).assertIsDisplayed()
            }

            rule.runOnIdle { state.value = state.value.copy(sheet = null) }
            rule.waitForIdle()
        }
    }

    // TC-8 (RC-75)
    @Test
    fun bookmarkControlsAreWiredThroughTheScreenContent() {
        val cfi = "epubcfi(/6/8!/4/2/1:0)"
        val bookmarks = listOf(
            Bookmark(1L, "client-1", "book-1", cfi, "Here", "Chapter", 0.5f, null),
            Bookmark(2L, "client-2", "book-1", "epubcfi(/6/20!/4/2/1:0)", null, "Later", 0.8f, null),
        )
        val state = mutableStateOf(
            ReaderUIState(
                book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
                loadStatus = ReaderLoadStatus.Ready,
                location = RelocateData(cfi = cfi, chapterTitle = "Chapter"),
                chromeVisible = true,
                menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Bookmarks, ReaderMenuRow.Settings),
                menuOpen = false,
                bookmarks = bookmarks,
            ),
        )
        var toggles = 0
        val chosen = mutableListOf<Bookmark>()
        show(
            uiState = { state.value },
            inspection = true,
            onMenuOpened = { state.value = state.value.withMenuOpened() },
            onMenuRowChosen = { state.value = state.value.withMenuRowChosen(it) },
            onMenuDismissed = { state.value = state.value.withMenuDismissed() },
            onBookmarkToggled = { toggles++ },
            onBookmarkChosen = {
                chosen += it
                state.value = state.value.copy(sheet = null)
            },
        )

        rule.onNodeWithContentDescription("Remove bookmark").assert(hasTestTag(ReaderChromeTags.BOOKMARK)).performClick()
        rule.waitForIdle()
        assertEquals(1, toggles)

        state.value = state.value.copy(location = RelocateData(cfi = " ", chapterTitle = "Chapter"))
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Add bookmark").assertIsNotEnabled()
        state.value = state.value.copy(location = RelocateData(cfi = cfi, chapterTitle = "Chapter"))
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderChromeTags.MORE).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Bookmarks)).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderContentsTags.segment(ReaderContentsTab.Bookmarks)).assertIsSelected()

        rule.onNodeWithTag(ReaderContentsTags.bookmarkRow(1)).performClick()
        rule.waitForIdle()
        assertEquals(listOf(bookmarks[1]), chosen)
    }

    // RC-48, TC-13
    @Test
    fun rc48_backOrderIsSheetThenMenuThenContextThenLeave() {
        val state = mutableStateOf(
            ReaderUIState(
                book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
                loadStatus = ReaderLoadStatus.Ready,
                chromeVisible = true,
                menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Settings),
                menuOpen = true,
                sheet = ReaderSheet.Settings,
            ),
        )
        val events = mutableListOf<String>()
        show(
            uiState = { state.value },
            inspection = true,
            onSheetDismissed = {
                events += "sheet"
                state.value = state.value.copy(sheet = null)
            },
            onMenuDismissed = {
                events += "menu"
                state.value = state.value.withMenuDismissed()
            },
            onContextLeft = {
                events += "context"
                state.value = state.value.withContextLeft()
            },
            onReaderClosed = { events += "closed" },
            onClose = { events += "close" },
        )

        pressBack()
        rule.waitForIdle()
        assertEquals(listOf("sheet"), events)

        pressBack()
        rule.waitForIdle()
        assertEquals(listOf("sheet", "menu"), events)

        pressBack()
        rule.waitForIdle()
        assertEquals(listOf("sheet", "menu", "context"), events)

        pressBack()
        rule.waitForIdle()
        assertEquals(listOf("sheet", "menu", "context", "closed", "close"), events)
    }

    // RC-49
    @Test
    fun rc49_chromeOnlyDrawsOverAReadyPage() {
        val book = mutableStateOf<ReaderBookState>(ReaderBookState.Ready(title = "Nineteen Eighty-Four"))
        val load = mutableStateOf<ReaderLoadStatus>(ReaderLoadStatus.Loading)
        show(
            uiState = {
                ReaderUIState(
                    book = book.value,
                    loadStatus = load.value,
                    chromeVisible = true,
                    menuOpen = true,
                    menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Settings),
                )
            },
            inspection = true,
        )

        assertNoChrome()

        rule.runOnIdle { load.value = ReaderLoadStatus.Ready }
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderChromeTags.BAR).assertIsDisplayed()

        rule.runOnIdle { book.value = ReaderBookState.Failed(LibraryUIError.NoServer) }
        rule.waitForIdle()
        assertNoChrome()
    }

    // RC-52, TC-14
    @Test
    fun rc52_pageLabelsDoNotChangeInContextMode() {
        val state = mutableStateOf(
            ReaderUIState(
                book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
                loadStatus = ReaderLoadStatus.Ready,
                location = RelocateData(page = 361, totalPages = 661, pct = 54, chapter = 3, totalChapters = 12, chapterPagesLeft = 7),
            ),
        )
        show(uiState = { state.value }, inspection = true)
        rule.waitForIdle()
        assertEquals(listOf("Nineteen Eighty-Four", "361"), pageLabelTexts())

        rule.runOnIdle { state.value = state.value.copy(chromeVisible = true) }
        rule.waitForIdle()
        assertEquals(listOf("Nineteen Eighty-Four", "361"), pageLabelTexts())
    }

    /** The text of the top and bottom page labels, read from their merged nodes. */
    private fun pageLabelTexts(): List<String> =
        listOf(ReaderChromeTags.LABEL_TOP, ReaderChromeTags.LABEL_BOTTOM).map { tag ->
            rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        }

    // TC-43
    @Test
    fun tc43_pageWaitsForTheOpeningPosition() {
        val opening = mutableStateOf<ReaderOpening>(ReaderOpening.Resolving)
        val controller = ReaderController()
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Ready(title = "Test Book"), opening = opening.value) },
            controller = controller,
            assetLoaderFactory = epubLoader,
        )

        assertNull("no page while the opening position is unknown", rule.runOnUiThread { findWebView() })

        opening.value = ReaderOpening.Resolved(ReaderStart.AtCfi("epubcfi(/6/4)"))

        rule.waitUntil(timeoutMillis = 30_000) { rule.runOnUiThread { findWebView() } != null }
        assertEquals("epubcfi(/6/4)", controller.restoreCfi)
    }

    // TC-45
    @Test
    fun tc45_stoppingAndLeavingCallTheFlushCallbacks() {
        val visible = mutableStateOf(true)
        var stopped = 0
        var closed = 0
        rule.setContent {
            if (visible.value) {
                OmnibusTheme {
                    ReaderScreenContent(
                        uiState = ReaderUIState(book = ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound)),
                        bookUuid = "book-1",
                        controller = ReaderController(),
                        assetLoaderFactory = noBookLoader,
                        commands = emptyFlow(),
                        onPageEvent = {},
                        onSheetDismissed = {},
                        onTocEntryChosen = {},
                        onMenuOpened = {},
                        onMenuDismissed = {},
                        onMenuRowChosen = {},
                        onRetryBookRequested = {},
                        onReaderClosed = { closed++ },
                        onReaderStopped = { stopped++ },
                        onSyncOfferAccepted = {},
                        onSyncOfferDismissed = {},
                        onClose = {},
                    )
                }
            }
        }

        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.runOnIdle {
            assertEquals("stopping flushes once", 1, stopped)
            assertEquals("stopping does not close the reader", 0, closed)
        }

        rule.runOnIdle { visible.value = false }
        rule.runOnIdle { assertEquals("leaving composition flushes once", 1, closed) }
    }

    /** The contents sheet open over a Ready book, with dismissals recorded in [events] and clearing the sheet. */
    private fun showOpenContentsSheet(events: MutableList<String>) {
        val sheet = mutableStateOf<ReaderSheet?>(ReaderSheet.Contents)
        show(
            uiState = {
                ReaderUIState(
                    book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
                    loadStatus = ReaderLoadStatus.Ready,
                    sheet = sheet.value,
                )
            },
            inspection = true,
            onSheetDismissed = {
                events += "sheet"
                sheet.value = null
            },
            onClose = { events += "close" },
            onReaderClosed = { events += "closed" },
        )
    }

    // CS-20
    @Test
    fun cs20_tappingAContentsRowChoosesThatEntry() {
        val toc = listOf(
            TocEntry(label = "One", href = "1.xhtml", level = 0),
            TocEntry(label = "Two", href = "2.xhtml", level = 1),
            TocEntry(label = "Three", href = "3.xhtml", level = 0),
        )
        val state = mutableStateOf(
            ReaderUIState(
                book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
                loadStatus = ReaderLoadStatus.Ready,
                toc = toc,
                sheet = ReaderSheet.Contents,
            ),
        )
        val chosen = mutableListOf<TocEntry>()
        show(
            uiState = { state.value },
            inspection = true,
            onTocEntryChosen = { chosen += it },
        )

        rule.onNodeWithTag(ReaderContentsTags.row(1)).performClick()
        rule.waitForIdle()

        assertEquals(listOf(toc[1]), chosen)
    }

    // CS-47
    @Test
    fun cs47_systemBackClosesTheSheetOnceAndStaysInTheReader() {
        val events = mutableListOf<String>()
        showOpenContentsSheet(events)
        rule.onNodeWithTag(ReaderContentsTags.SHEET).assertIsDisplayed()

        // The sheet's own window takes the system back, so the reader's handler never runs.
        Espresso.pressBack()
        rule.waitForIdle()

        assertEquals(listOf("sheet"), events)
        rule.onNodeWithTag(ReaderContentsTags.SHEET).assertDoesNotExist()
        rule.onNodeWithTag(ReaderScreenTags.ROOT).assertIsDisplayed()
    }

    // CS-48
    @Test
    fun cs48_tappingTheScrimDismissesTheSheet() {
        val events = mutableListOf<String>()
        showOpenContentsSheet(events)
        rule.onNodeWithTag(ReaderContentsTags.SHEET).assertIsDisplayed()

        rule.onNodeWithContentDescription("Close sheet").performTouchInput {
            click(Offset(centerX, height * 0.03f))
        }
        rule.waitForIdle()

        assertEquals(listOf("sheet"), events)
        rule.onNodeWithTag(ReaderScreenTags.ROOT).assertIsDisplayed()
    }

    // CS-50
    @Test
    fun cs50_theCloseButtonLeavesTheReaderOpen() {
        val events = mutableListOf<String>()
        showOpenContentsSheet(events)

        rule.onNodeWithTag(ReaderContentsTags.CLOSE).performClick()
        rule.waitForIdle()

        assertEquals(listOf("sheet"), events)
        rule.onNodeWithTag(ReaderScreenTags.ROOT).assertIsDisplayed()
    }

    private fun webViewPresent(): Boolean {
        var found = false
        rule.runOnUiThread { found = findWebView() != null }
        return found
    }

    @Test
    fun webViewWaitsForTheStoredSettings() {
        val loaded = mutableStateOf(false)
        rule.setContent {
            OmnibusTheme {
                ReaderScreenContent(
                    uiState = ReaderUIState(
                        book = ReaderBookState.Ready(title = "Moby Dick"),
                        opening = ReaderOpening.Resolved(ReaderStart.Beginning),
                        settingsLoaded = loaded.value,
                    ),
                    bookUuid = "book-1",
                    controller = ReaderController(),
                    assetLoaderFactory = epubLoader,
                    commands = emptyFlow(),
                    onPageEvent = {},
                    onSheetDismissed = {},
                    onMenuOpened = {},
                    onMenuDismissed = {},
                    onMenuRowChosen = {},
                    onTocEntryChosen = {},
                    onRetryBookRequested = {},
                    onReaderClosed = {},
                    onReaderStopped = {},
                    onSyncOfferAccepted = {},
                    onSyncOfferDismissed = {},
                    onClose = {},
                )
            }
        }
        rule.waitForIdle()
        assertEquals(false, webViewPresent())

        rule.runOnIdle { loaded.value = true }
        rule.waitUntil(timeoutMillis = 10_000) { webViewPresent() }
    }

    // TC-20
    @Test
    fun rc53_onlyThePageAreaZoomsAboveTheDock() {
        val state = mutableStateOf(
            ReaderUIState(
                book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
                loadStatus = ReaderLoadStatus.Ready,
            ),
        )
        show(
            uiState = { state.value },
            inspection = true,
            bottomDock = { Box(Modifier.fillMaxWidth().fillMaxHeight(0.1f).testTag("test_dock")) },
        )
        rule.waitForIdle()
        val dockWhileFocused = rule.onNodeWithTag("test_dock").fetchSemanticsNode().boundsInRoot

        rule.runOnIdle { state.value = state.value.copy(chromeVisible = true) }
        rule.waitForIdle()

        val card = rule.onNodeWithTag(ReaderContextStageTags.CARD).fetchSemanticsNode().boundsInRoot
        val dock = rule.onNodeWithTag("test_dock").fetchSemanticsNode().boundsInRoot
        assertTrue("the zoomed card ends above the dock", card.bottom <= dock.top + 2f)
        assertEquals(dockWhileFocused.top, dock.top, 1f)
        assertEquals(dockWhileFocused.bottom, dock.bottom, 1f)
    }

    // TC-45, P-MINI-4 (the dock pads the navigation bar, so the page's bottom inset is 0 while docked)
    @Test
    fun dockPageBottomInsetIsZeroWhileDocked() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        val controller = ReaderController()
        val docked = mutableStateOf(true)
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Ready(title = "Test Book")) },
            controller = controller,
            assetLoaderFactory = epubLoader,
            bottomDock = {
                if (docked.value) Box(Modifier.fillMaxWidth().fillMaxHeight(0.1f).testTag("test_dock"))
            },
        )
        rule.waitUntil(timeoutMillis = 30_000) { controller.status == ReaderLoadStatus.Ready }
        rule.waitUntil(timeoutMillis = 10_000) { cssInset("--inset-bottom") != null }
        rule.waitForIdle()
        assertEquals(0, cssInset("--inset-bottom"))

        val decor = rule.activity.window.decorView
        val bars = ViewCompat.getRootWindowInsets(decor)!!
            .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val density = rule.activity.resources.displayMetrics.density
        val tolerance = density / 2 + 1
        rule.runOnIdle { docked.value = false }
        rule.waitForIdle()
        if (bars.bottom == 0) {
            rule.waitUntil(timeoutMillis = 10_000) { cssInset("--inset-bottom") == 0 }
        } else {
            rule.waitUntil(timeoutMillis = 10_000) {
                cssInset("--inset-bottom")?.let { abs(it * density - bars.bottom) <= tolerance } == true
            }
        }
    }
}
