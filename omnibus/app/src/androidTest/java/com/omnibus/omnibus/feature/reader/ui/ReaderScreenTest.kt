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
import com.omnibus.omnibus.feature.reader.ReaderSheet
import com.omnibus.omnibus.feature.reader.ReaderUIState
import com.omnibus.omnibus.feature.reader.ReaderUnavailableReason
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
}
