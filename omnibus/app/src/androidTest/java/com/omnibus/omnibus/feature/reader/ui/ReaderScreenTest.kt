package com.omnibus.omnibus.feature.reader.ui

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
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
import com.omnibus.omnibus.feature.reader.withMenuDismissed
import com.omnibus.omnibus.feature.reader.withMenuOpened
import com.omnibus.omnibus.feature.reader.withMenuRowChosen
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import androidx.compose.ui.graphics.toArgb
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
        /** The app's dark setting for the test, or null to keep the device's. */
        appNight: Boolean? = null,
        /** The dock below the page; empty, as in the app without a book in the player. */
        bottomDock: @Composable () -> Unit = {},
    ) {
        rule.setContent {
            val configuration = appNight?.let { night ->
                Configuration(LocalConfiguration.current).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
                }
            } ?: LocalConfiguration.current
            CompositionLocalProvider(
                LocalInspectionMode provides inspection,
                LocalConfiguration provides configuration,
            ) {
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
        // CS-4
        rule.onNodeWithTag(ReaderContentsTags.SHEET).assertIsDisplayed()
        assertEquals(ReaderSheet.Contents, state.value.sheet)
        assertEquals(false, state.value.menuOpen)
    }

    // TC-75
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
                menuOpen = true,
                bookmarks = bookmarks,
            ),
        )
        var toggles = 0
        val chosen = mutableListOf<Bookmark>()
        show(
            uiState = { state.value },
            inspection = true,
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

        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Bookmarks)).assert(hasText("2")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderContentsTags.segment(ReaderContentsTab.Bookmarks)).assertIsSelected()
        rule.onNodeWithTag(ReaderContentsTags.bookmarkRow(0)).assertIsDisplayed()

        rule.onNodeWithTag(ReaderContentsTags.bookmarkRow(1)).performClick()
        rule.waitForIdle()
        assertEquals(listOf(bookmarks[1]), chosen)
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

        rule.onNodeWithText("Opening Test Book").assertIsDisplayed()
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
    private fun showOpenContentsSheet(
        events: MutableList<String>,
        toc: List<TocEntry> = emptyList(),
        readingTheme: ReaderTheme = ReaderTheme.Light,
        appNight: Boolean? = null,
    ) {
        val sheet = mutableStateOf<ReaderSheet?>(ReaderSheet.Contents)
        show(
            uiState = {
                ReaderUIState(
                    book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
                    loadStatus = ReaderLoadStatus.Ready,
                    settings = ReaderSettings(theme = readingTheme),
                    toc = toc,
                    sheet = sheet.value,
                )
            },
            inspection = true,
            appNight = appNight,
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
    fun cs20_tappingAContentsRowJumpsAndClosesTheSheet() {
        val toc = listOf(
            TocEntry(label = "One", href = "1.xhtml", level = 0),
            TocEntry(label = "Two", href = "2.xhtml", level = 1),
            TocEntry(label = "Three", href = "3.xhtml", level = 0),
        )
        val state = mutableStateOf(
            ReaderUIState(
                book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
                loadStatus = ReaderLoadStatus.Ready,
                chromeVisible = true,
                toc = toc,
                sheet = ReaderSheet.Contents,
            ),
        )
        val chosen = mutableListOf<TocEntry>()
        var closes = 0
        show(
            uiState = { state.value },
            inspection = true,
            onTocEntryChosen = { entry ->
                chosen += entry
                state.value = state.value.copy(sheet = null)
            },
            onClose = { closes++ },
        )

        rule.onNodeWithTag(ReaderContentsTags.row(1)).performClick()
        rule.waitForIdle()

        assertEquals(listOf(toc[1]), chosen)
        rule.onNodeWithTag(ReaderContentsTags.SHEET).assertDoesNotExist()
        rule.onNodeWithTag(ReaderScreenTags.ROOT).assertIsDisplayed()
        assertEquals(true, state.value.chromeVisible)
        assertEquals(0, closes)
    }

    // CS-43
    @Test
    fun cs43_sheetFollowsAppDarkThemeOverALightPage() {
        showOpenContentsSheet(events = mutableListOf(), readingTheme = ReaderTheme.Light, appNight = true)

        val pixels = rule.onNodeWithTag(ReaderContentsTags.SHEET).captureToImage().toPixelMap()
        assertColourNear(OmnibusPalette.Atrium.bg0, pixels[4, pixels.height - 4], "dark app sheet over a Light page")
    }

    // CS-44
    @Test
    fun cs44_sheetFollowsAppLightThemeOverADarkPage() {
        showOpenContentsSheet(events = mutableListOf(), readingTheme = ReaderTheme.Black, appNight = false)

        val pixels = rule.onNodeWithTag(ReaderContentsTags.SHEET).captureToImage().toPixelMap()
        assertColourNear(OmnibusPalette.Light.bg0, pixels[4, pixels.height - 4], "light app sheet over a Black page")
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

    // CS-51
    @Test
    fun cs51_backOrderWithTheContentsSheetIsSheetThenMenuThenLeave() {
        val state = mutableStateOf(
            ReaderUIState(
                book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
                loadStatus = ReaderLoadStatus.Ready,
                chromeVisible = true,
                menuRows = listOf(ReaderMenuRow.Contents),
                menuOpen = true,
                sheet = ReaderSheet.Contents,
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

        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals(listOf("sheet"), events)

        pressBack()
        rule.waitForIdle()
        assertEquals(listOf("sheet", "menu"), events)

        pressBack()
        rule.waitForIdle()
        assertEquals(listOf("sheet", "menu", "closed", "close"), events)
    }

    /** What a chrome toggle could change about the page: its size, the stage box, the insets and the system bars. */
    private data class PageSnapshot(
        val width: Int,
        val height: Int,
        val stage: String,
        val insets: List<Int?>,
        val bars: List<Boolean?>,
    )

    /** Each channel within 3/255 of the expected colour, which absorbs the sRGB round trip. */
    private fun assertColourNear(expected: Color, actual: Color, message: String) {
        val tolerance = 3f / 255f
        assertTrue(
            "$message: expected $expected but was $actual",
            abs(expected.red - actual.red) <= tolerance &&
                abs(expected.green - actual.green) <= tolerance &&
                abs(expected.blue - actual.blue) <= tolerance,
        )
    }

    private fun pageSnapshot(): PageSnapshot {
        val size = rule.runOnUiThread { findWebView()?.let { it.width to it.height } }
        assertNotNull("the page is a WebView", size)
        // Which bars exist depends on the device (the CI AVD has no navigation bar), so record each bar's state
        // and compare it across the toggle rather than assuming it.
        val bars = rule.runOnUiThread {
            val insets = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
            listOf(WindowInsetsCompat.Type.statusBars(), WindowInsetsCompat.Type.navigationBars())
                .map { insets?.isVisible(it) }
        }
        return PageSnapshot(
            width = size!!.first,
            height = size.second,
            stage = evaluate("JSON.stringify(document.getElementById('stage').getBoundingClientRect())"),
            insets = listOf("--inset-top", "--inset-bottom", "--inset-left", "--inset-right").map { cssInset(it) },
            bars = bars,
        )
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

    @Test
    fun themeChangeRepaintsTheHostGroundAndTheSystemBars() {
        val window = rule.activity.window
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        val theme = mutableStateOf(ReaderTheme.Dark)
        show(
            uiState = {
                ReaderUIState(
                    book = ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound),
                    settings = ReaderSettings(theme = theme.value),
                )
            },
        )
        rule.waitForIdle()
        assertEquals(ReaderTheme.Dark.groundArgb, rule.onNodeWithTag(ReaderScreenTags.ROOT).captureToImage().toPixelMap()[2, 2].toArgb())
        assertEquals(false, bars.isAppearanceLightStatusBars)

        rule.runOnIdle { theme.value = ReaderTheme.Light }
        rule.waitForIdle()
        assertEquals(ReaderTheme.Light.groundArgb, rule.onNodeWithTag(ReaderScreenTags.ROOT).captureToImage().toPixelMap()[2, 2].toArgb())
        assertEquals(true, bars.isAppearanceLightStatusBars)
    }

    @Test
    fun sheetKeepsTheAppPaletteOverEveryReadingTheme() {
        val configuration = context.resources.configuration
        val systemDark = (configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val appPalette = if (systemDark) OmnibusPalette.Atrium else OmnibusPalette.Light
        // Precondition: the app palette differs from the reading one for at least one of the two themes.
        val readingThemes = listOf(ReaderTheme.Sepia, ReaderTheme.Black)
        assertTrue(readingThemes.any { (if (it.isLight) OmnibusPalette.Light else OmnibusPalette.Atrium) != appPalette })

        val theme = mutableStateOf(ReaderTheme.Sepia)
        show(
            uiState = {
                ReaderUIState(
                    book = ReaderBookState.Ready(title = "Moby Dick"),
                    loadStatus = ReaderLoadStatus.Ready,
                    sheet = ReaderSheet.Settings,
                    settings = ReaderSettings(theme = theme.value),
                )
            },
            inspection = true,
        )
        rule.waitForIdle()

        readingThemes.forEach { reading ->
            rule.runOnIdle { theme.value = reading }
            rule.waitForIdle()
            val pixel = rule.onNodeWithTag(ReaderSettingsSheetTags.SHEET).captureToImage().toPixelMap()[2, 2]
            assertEquals(appPalette.bg0.toArgb(), pixel.toArgb())
            assertTrue(pixel.toArgb() != Color(reading.groundArgb).toArgb())
        }
    }

    @Test
    fun backClosesTheSettingsSheetWithoutLeavingTheReader() {
        val sheet = mutableStateOf<ReaderSheet?>(ReaderSheet.Settings)
        val events = mutableListOf<String>()
        show(
            uiState = {
                ReaderUIState(book = ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound), sheet = sheet.value)
            },
            onSheetDismissed = {
                events += "dismissed"
                sheet.value = null
            },
            onClose = { events += "close" },
        )

        pressBack()
        rule.waitForIdle()

        assertEquals(listOf("dismissed"), events)
    }

    // TC-41, P-MINI-4
    @Test
    fun dockTheDefaultDockLeavesTheReaderUnchanged() {
        show(uiState = { ReaderUIState(book = ReaderBookState.Loading) })
        rule.waitForIdle()

        val root = rule.onNodeWithTag(ReaderScreenTags.ROOT).fetchSemanticsNode().boundsInRoot
        val page = rule.onNodeWithTag(ReaderScreenTags.PAGE_AREA).fetchSemanticsNode().boundsInRoot
        val dock = rule.onNodeWithTag(ReaderScreenTags.DOCK).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(page.top - root.top) <= 1f)
        assertTrue(abs(page.bottom - root.bottom) <= 1f)
        assertTrue(abs(page.left - root.left) <= 1f)
        assertTrue(abs(page.right - root.right) <= 1f)
        assertEquals(0f, dock.height, 1f)
    }

    // TC-42, P-MINI-4
    @Test
    fun dockThePageSitsAboveTheDockWithoutOverlap() {
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Loading) },
            bottomDock = { Box(Modifier.fillMaxWidth().fillMaxHeight(0.1f).testTag("test_dock")) },
        )
        rule.waitForIdle()

        val root = rule.onNodeWithTag(ReaderScreenTags.ROOT).fetchSemanticsNode().boundsInRoot
        val page = rule.onNodeWithTag(ReaderScreenTags.PAGE_AREA).fetchSemanticsNode().boundsInRoot
        val dock = rule.onNodeWithTag(ReaderScreenTags.DOCK).fetchSemanticsNode().boundsInRoot
        assertTrue("the dock has a height", dock.height > 0f)
        assertTrue("the page ends at the dock's top edge", page.bottom <= dock.top + 1f)
        assertTrue(abs(page.top - root.top) <= 1f)
        assertTrue(abs(dock.bottom - root.bottom) <= 1f)
        assertTrue(abs(dock.left - root.left) <= 1f)
        assertTrue(abs(dock.right - root.right) <= 1f)
    }

    // TC-43, P-MINI-4 (stopping from the dock returns the page to full height)
    @Test
    fun dockRemovingTheDockRestoresTheFullPageHeight() {
        val docked = mutableStateOf(true)
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Loading) },
            bottomDock = {
                if (docked.value) Box(Modifier.fillMaxWidth().fillMaxHeight(0.1f).testTag("test_dock"))
            },
        )
        rule.waitForIdle()

        val rootHeight = rule.onNodeWithTag(ReaderScreenTags.ROOT).fetchSemanticsNode().size.height
        assertTrue("the dock has a height before removal", rule.onNodeWithTag(ReaderScreenTags.DOCK).fetchSemanticsNode().size.height > 0)
        assertTrue(rule.onNodeWithTag(ReaderScreenTags.PAGE_AREA).fetchSemanticsNode().size.height < rootHeight)

        rule.runOnIdle { docked.value = false }
        rule.waitForIdle()

        assertEquals(0, rule.onNodeWithTag(ReaderScreenTags.DOCK).fetchSemanticsNode().size.height)
        assertTrue(abs(rule.onNodeWithTag(ReaderScreenTags.PAGE_AREA).fetchSemanticsNode().size.height - rootHeight) <= 1)
    }

    // TC-44, P-MINI-4 (page content ends above the dock)
    @Test
    fun dockPageContentNeverSitsUnderTheDock() {
        show(
            uiState = { ReaderUIState(book = ReaderBookState.Loading) },
            bottomDock = { Box(Modifier.fillMaxWidth().fillMaxHeight(0.1f).testTag("test_dock")) },
        )
        rule.waitForIdle()

        val dockTop = rule.onNodeWithTag(ReaderScreenTags.DOCK).fetchSemanticsNode().boundsInRoot.top
        val loading = rule.onNodeWithText(context.getString(R.string.reader_loading)).fetchSemanticsNode().boundsInRoot
        assertTrue("the loading text ends above the dock", loading.bottom <= dockTop + 1f)
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
