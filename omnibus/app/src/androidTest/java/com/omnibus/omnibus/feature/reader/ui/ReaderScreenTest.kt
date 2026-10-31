package com.omnibus.omnibus.feature.reader.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
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
import com.omnibus.omnibus.feature.reader.ReaderSheet
import com.omnibus.omnibus.feature.reader.ReaderUIState
import com.omnibus.omnibus.feature.reader.ReaderUnavailableReason
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

// TC-64 to TC-81 (the screen cases). Tests assert on text and test tags, never on pixel positions or
// fixed dp, so they hold on the 320dp CI AVD.
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
        onMenuOpened: () -> Unit = {},
        onMenuDismissed: () -> Unit = {},
        onMenuRowChosen: (ReaderMenuRow) -> Unit = {},
        onRetryBookRequested: () -> Unit = {},
        onReaderClosed: () -> Unit = {},
        onClose: () -> Unit = {},
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
                        onMenuOpened = onMenuOpened,
                        onMenuDismissed = onMenuDismissed,
                        onMenuRowChosen = onMenuRowChosen,
                        onRetryBookRequested = onRetryBookRequested,
                        onReaderClosed = onReaderClosed,
                        onClose = onClose,
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

    // TC-64
    @Test
    fun tc64_loadingWithoutTitleShowsTheGenericLabel() {
        show(uiState = { ReaderUIState(book = ReaderBookState.Loading) })

        rule.onNodeWithText("Opening your book").assertIsDisplayed()
    }

    // TC-65
    @Test
    fun tc65_loadingWithATitleShowsTheTitleOnce() {
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Ready(title = "Moby Dick"), loadStatus = ReaderLoadStatus.Loading) },
            assetLoaderFactory = epubLoader,
        )

        rule.onNodeWithText("Opening Moby Dick").assertIsDisplayed()
    }

    // TC-66
    @Test
    fun tc66_aReadyEpubRendersTheWebView() {
        val controller = ReaderController()
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Ready(title = "Test Book")) },
            controller = controller,
            assetLoaderFactory = epubLoader,
        )

        rule.waitUntil(timeoutMillis = 30_000) { controller.status == ReaderLoadStatus.Ready }

        rule.onNodeWithTag(ReaderScreenTags.ROOT).assertIsDisplayed()
        assertNotNull(findWebView())
        rule.onAllNodesWithText("Couldn't open this book").assertCountEquals(0)
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

    // TC-68
    @Test
    fun tc68_contentRendersFromPlainStateAndCallbacks() {
        show(uiState = { ReaderUIState(book = ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound)) })

        rule.onNodeWithText("This book isn't in your library.").assertIsDisplayed()
    }

    // TC-69
    @Test
    fun tc69_theReaderFillsTheWindowAndPassesTheSystemInsets() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        val insetsMatch = mutableStateOf<Boolean?>(null)
        rule.setContent {
            val density = LocalDensity.current
            val defaults = ReaderWebViewDefaults.insets
            val expected = WindowInsets.systemBars.union(WindowInsets.displayCutout)
            SideEffect {
                insetsMatch.value = defaults.getTop(density) == expected.getTop(density) &&
                    defaults.getBottom(density) == expected.getBottom(density)
            }
            OmnibusTheme {
                ReaderScreenContent(
                    uiState = ReaderUIState(book = ReaderBookState.Loading),
                    bookUuid = "book-1",
                    controller = ReaderController(),
                    assetLoaderFactory = noBookLoader,
                    commands = emptyFlow(),
                    onPageEvent = {},
                    onSheetDismissed = {},
                    onMenuOpened = {},
                    onMenuDismissed = {},
                    onMenuRowChosen = {},
                    onRetryBookRequested = {},
                    onReaderClosed = {},
                    onClose = {},
                )
            }
        }
        rule.waitUntil { insetsMatch.value != null }
        assertTrue("insets passed to the page are systemBars plus displayCutout", insetsMatch.value == true)

        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val reader = rule.onNodeWithTag(ReaderScreenTags.ROOT).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(reader.left - root.left) <= 1f)
        assertTrue(abs(reader.top - root.top) <= 1f)
        assertTrue(abs(reader.right - root.right) <= 1f)
        assertTrue(abs(reader.bottom - root.bottom) <= 1f)
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
                        onMenuOpened = {},
                        onMenuDismissed = {},
                        onMenuRowChosen = {},
                        onRetryBookRequested = {},
                        onReaderClosed = {},
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

    // TC-72
    @Test
    fun tc72_backClosesAnOpenSheetFirst() {
        val sheet = mutableStateOf<ReaderSheet?>(ReaderSheet.Contents)
        val events = mutableListOf<String>()
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound), sheet = sheet.value) },
            onSheetDismissed = {
                events += "dismissed"
                sheet.value = null
            },
            onClose = { events += "close" },
        )

        pressBack()
        rule.waitForIdle()

        assertEquals(listOf("dismissed"), events)
        rule.onNodeWithText("This book isn't in your library.").assertIsDisplayed()
    }

    // TC-73
    @Test
    fun tc73_backLeavesTheReaderWhenNoSheetIsOpen() {
        val events = mutableListOf<String>()
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound)) },
            onReaderClosed = { events += "closed" },
            onClose = { events += "close" },
        )

        pressBack()
        rule.waitForIdle()

        assertEquals(listOf("closed", "close"), events)
    }

    // TC-74
    @Test
    fun tc74_theSecondBackLeavesAfterTheSheetCloses() {
        val sheet = mutableStateOf<ReaderSheet?>(ReaderSheet.Settings)
        val events = mutableListOf<String>()
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound), sheet = sheet.value) },
            onSheetDismissed = {
                events += "dismissed"
                sheet.value = null
            },
            onReaderClosed = { events += "closed" },
            onClose = { events += "close" },
        )

        pressBack()
        rule.waitForIdle()
        assertEquals(listOf("dismissed"), events)

        pressBack()
        rule.waitForIdle()
        assertEquals(listOf("dismissed", "closed", "close"), events)
    }

    // TC-76
    @Test
    fun tc76_notFoundShowsItsError() {
        show(uiState = { ReaderUIState(book = ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound)) })

        rule.onNodeWithText("Couldn't open this book").assertIsDisplayed()
        rule.onNodeWithText("This book isn't in your library.").assertIsDisplayed()
        rule.onNodeWithText("Back").assertIsDisplayed()
    }

    // TC-77
    @Test
    fun tc77_noEpubShowsItsError() {
        show(uiState = { ReaderUIState(book = ReaderBookState.Unavailable(ReaderUnavailableReason.NoEpub)) })

        rule.onNodeWithText("This book has no EPUB to read.").assertIsDisplayed()
        rule.onNodeWithText("Back").assertIsDisplayed()
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

    // TC-78
    @Test
    fun tc78_theBackActionLeavesFromNoEpub() {
        val closes = mutableListOf<String>()
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Unavailable(ReaderUnavailableReason.NoEpub)) },
            onClose = { closes += "close" },
        )

        rule.onNodeWithText("Back").performClick()
        rule.waitForIdle()

        assertEquals(listOf("close"), closes)
    }

    // TC-79
    @Test
    fun tc79_failedUnreachableShowsTheMappedMessageAndRetries() {
        val retries = mutableListOf<String>()
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Failed(LibraryUIError.Failed(LibraryError.Unreachable))) },
            onRetryBookRequested = { retries += "retry" },
        )

        rule.onNodeWithText("Could not connect to the server.").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()
        rule.waitForIdle()

        assertEquals(listOf("retry"), retries)
    }

    // TC-79
    @Test
    fun tc79_failedHttpShowsTheMappedMessage() {
        show(uiState = { ReaderUIState(book = ReaderBookState.Failed(LibraryUIError.Failed(LibraryError.Http(500, null)))) })

        rule.onNodeWithText("Server error (500).").assertIsDisplayed()
        rule.onNodeWithText("Try again").assertIsDisplayed()
    }

    // TC-79
    @Test
    fun tc79_failedNoServerShowsTheMappedMessage() {
        show(uiState = { ReaderUIState(book = ReaderBookState.Failed(LibraryUIError.NoServer)) })

        rule.onNodeWithText("No server configured.").assertIsDisplayed()
        rule.onNodeWithText("Try again").assertIsDisplayed()
    }

    // TC-80
    @Test
    fun tc80_backLeavesFromNotFoundAndTheScreenIsNeverBlank() {
        val closes = mutableListOf<String>()
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound)) },
            onClose = { closes += "close" },
        )

        pressBack()
        rule.waitForIdle()

        assertEquals(listOf("close"), closes)
        rule.onNodeWithText("This book isn't in your library.").assertIsDisplayed()
    }

    // TC-80
    @Test
    fun tc80_backLeavesFromFailedAndTheScreenIsNeverBlank() {
        val closes = mutableListOf<String>()
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Failed(LibraryUIError.NoServer)) },
            onClose = { closes += "close" },
        )

        pressBack()
        rule.waitForIdle()

        assertEquals(listOf("close"), closes)
        rule.onNodeWithText("No server configured.").assertIsDisplayed()
    }

    // TC-81
    @Test
    fun tc81_readyContentUnderInspectionModeShowsThePlaceholderAndNoWebView() {
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Ready(title = "Test Book"), loadStatus = ReaderLoadStatus.Ready) },
            inspection = true,
        )

        rule.onNodeWithTag(ReaderScreenTags.PREVIEW_PAGE).assertIsDisplayed()
        assertNull(findWebView())
    }

    /** The chrome's nodes, none of which may be drawn over a page that isn't Ready. */
    private fun assertNoChrome() {
        listOf(
            ReaderChromeTags.CLOSE,
            ReaderChromeTags.MENU,
            ReaderChromeTags.LABEL_TOP,
            ReaderChromeTags.LABEL_BOTTOM,
            ReaderChromeTags.MENU_SCRIM,
        ).forEach { rule.onNodeWithTag(it).assertDoesNotExist() }
    }

    // RC-45
    @Test
    fun rc45_closeButtonLeavesThroughTheBackPath() {
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

        rule.onNodeWithTag(ReaderChromeTags.CLOSE).performClick()
        rule.waitForIdle()

        assertEquals(listOf("closed", "close"), events)
    }

    // RC-46
    @Test
    fun rc46_screenWiresIndicatorsIntoTheChrome() {
        val chromeVisible = mutableStateOf(false)
        val location = RelocateData(
            page = 361,
            totalPages = 661,
            pct = 54,
            chapter = 3,
            totalChapters = 12,
            chapterPagesLeft = 7,
        )
        show(
            uiState = {
                ReaderUIState(
                    book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
                    loadStatus = ReaderLoadStatus.Ready,
                    location = location,
                    chromeVisible = chromeVisible.value,
                )
            },
            inspection = true,
        )

        rule.onNodeWithTag(ReaderChromeTags.LABEL_TOP).assertTextEquals("Nineteen Eighty-Four")
        rule.onNodeWithTag(ReaderChromeTags.LABEL_BOTTOM).assertTextEquals("361")
        rule.onNodeWithTag(ReaderChromeTags.CLOSE).assertDoesNotExist()

        rule.runOnIdle { chromeVisible.value = true }
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderChromeTags.LABEL_TOP).assertTextEquals("7 pages left in chapter")
        rule.onNodeWithTag(ReaderChromeTags.LABEL_BOTTOM).assertTextEquals("361 of 661")
        rule.onNodeWithTag(ReaderChromeTags.CLOSE).assertIsDisplayed()
    }

    // RC-47
    @Test
    fun rc47_choosingARowClosesTheMenuAndRaisesTheSheet() {
        val state = mutableStateOf(
            ReaderUIState(
                book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
                loadStatus = ReaderLoadStatus.Ready,
                chromeVisible = true,
                menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Settings),
            ),
        )
        show(
            uiState = { state.value },
            inspection = true,
            onMenuOpened = { state.value = state.value.withMenuOpened() },
            onMenuDismissed = { state.value = state.value.withMenuDismissed() },
            onMenuRowChosen = { state.value = state.value.withMenuRowChosen(it) },
        )

        rule.onNodeWithTag(ReaderChromeTags.MENU).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Contents)).assertIsDisplayed()

        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Contents)).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Contents)).assertDoesNotExist()
        assertEquals(ReaderSheet.Contents, state.value.sheet)
        assertEquals(false, state.value.menuOpen)
    }

    // RC-48
    @Test
    fun rc48_backOrderIsSheetThenMenuThenLeave() {
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
            onReaderClosed = { events += "closed" },
            onClose = { events += "close" },
        )

        pressBack()
        rule.waitForIdle()
        assertEquals(listOf("sheet"), events)
        assertEquals(true, state.value.chromeVisible)

        pressBack()
        rule.waitForIdle()
        assertEquals(listOf("sheet", "menu"), events)
        assertEquals(true, state.value.chromeVisible)

        pressBack()
        rule.waitForIdle()
        assertEquals(listOf("sheet", "menu", "closed", "close"), events)
        assertEquals(true, state.value.chromeVisible)
    }

    // RC-49
    @Test
    fun rc49_chromeOnlyDrawsOverAReadyPage() {
        val book = mutableStateOf<ReaderBookState>(ReaderBookState.Ready(title = "Nineteen Eighty-Four"))
        val load = mutableStateOf<ReaderLoadStatus>(ReaderLoadStatus.Loading)
        val location = RelocateData(
            page = 361,
            totalPages = 661,
            pct = 54,
            chapter = 3,
            totalChapters = 12,
            chapterPagesLeft = 7,
        )
        show(
            uiState = {
                ReaderUIState(
                    book = book.value,
                    loadStatus = load.value,
                    location = location,
                    chromeVisible = true,
                    menuOpen = true,
                    menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Settings),
                )
            },
            inspection = true,
        )

        assertNoChrome()

        rule.runOnIdle { load.value = ReaderLoadStatus.Failed(ReaderLoadFailure.Render) }
        rule.waitForIdle()
        assertNoChrome()

        rule.runOnIdle { load.value = ReaderLoadStatus.Ready }
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderChromeTags.CLOSE).assertIsDisplayed()
        rule.onNodeWithTag(ReaderChromeTags.LABEL_BOTTOM).assertTextEquals("361 of 661")

        rule.runOnIdle { book.value = ReaderBookState.Loading }
        rule.waitForIdle()
        assertNoChrome()

        rule.runOnIdle { book.value = ReaderBookState.Failed(LibraryUIError.NoServer) }
        rule.waitForIdle()
        assertNoChrome()

        rule.runOnIdle { book.value = ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound) }
        rule.waitForIdle()
        assertNoChrome()
    }

    // RC-50
    @Test
    fun rc50_togglingChromeNeverResizesThePage() {
        val controller = ReaderController()
        val chromeVisible = mutableStateOf(false)
        val menuOpen = mutableStateOf(false)
        val menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Settings)
        val movements = mutableListOf<RelocateData>()
        show(
            uiState = {
                ReaderUIState(
                    book = ReaderBookState.Ready(title = "Test Book"),
                    loadStatus = controller.status,
                    chromeVisible = chromeVisible.value,
                    menuOpen = menuOpen.value,
                    menuRows = menuRows,
                )
            },
            controller = controller,
            assetLoaderFactory = epubLoader,
            onPageEvent = { event ->
                if (event is ReaderEvent.Relocate && event.data.isMovement) movements += event.data
            },
        )
        rule.waitUntil(timeoutMillis = 30_000) { controller.status == ReaderLoadStatus.Ready }
        rule.waitForIdle()
        // Let the first layout and any first relocate settle before the baseline is taken.
        Thread.sleep(1_000)
        rule.waitForIdle()

        val baseline = pageSnapshot()
        val baselineMovements = movements.size

        rule.runOnIdle { chromeVisible.value = true }
        rule.waitForIdle()
        assertEquals("chrome shown", baseline, pageSnapshot())

        rule.runOnIdle { menuOpen.value = true }
        rule.waitForIdle()
        assertEquals("menu open", baseline, pageSnapshot())

        rule.runOnIdle {
            menuOpen.value = false
            chromeVisible.value = false
        }
        rule.waitForIdle()
        assertEquals("chrome hidden", baseline, pageSnapshot())

        assertEquals("no new movement", baselineMovements, movements.size)
    }

    /** What a chrome toggle could change about the page: its size, the stage box, the insets and the system bars. */
    private data class PageSnapshot(
        val width: Int,
        val height: Int,
        val stage: String,
        val insets: List<Int?>,
        val barsVisible: Boolean,
    )

    private fun pageSnapshot(): PageSnapshot {
        val size = rule.runOnUiThread { findWebView()?.let { it.width to it.height } }
        assertNotNull("the page is a WebView", size)
        // `systemBars()` also includes the caption bar, which a phone never shows, so check the two bars that exist.
        val barsVisible = rule.runOnUiThread {
            val insets = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
            insets?.isVisible(WindowInsetsCompat.Type.statusBars()) == true &&
                insets.isVisible(WindowInsetsCompat.Type.navigationBars())
        }
        assertTrue("the system bars stay visible", barsVisible)
        return PageSnapshot(
            width = size!!.first,
            height = size.second,
            stage = evaluate("JSON.stringify(document.getElementById('stage').getBoundingClientRect())"),
            insets = listOf("--inset-top", "--inset-bottom", "--inset-left", "--inset-right").map { cssInset(it) },
            barsVisible = barsVisible,
        )
    }
}
