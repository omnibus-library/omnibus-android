package com.omnibus.omnibus.feature.reader.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// TC-22: the existing host cases below are ported from onMessage to onEvent, and keep their behaviour.
class ReaderWebViewTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val epub = TestEpub.build()
    private val messages = mutableListOf<ReaderEvent>()

    private var bookResult: () -> BookFileResult = { BookFileResult.Opened(ByteArrayInputStream(epub), epub.size.toLong()) }
    private var bookRequests = 0
    private val assetLoaderFactory = ReaderAssetLoaderFactory(
        context,
        Dispatchers.Unconfined,
        BookFileSource {
            bookRequests++
            bookResult()
        },
    )

    @Before
    fun clearLocationCache() {
        // epub.js caches a book's page numbers in localStorage under its uuid; start every test cold.
        messages.clear()
    }

    private fun showReader(
        controller: ReaderController,
        settings: ReaderSettings = ReaderSettings(),
        modifier: Modifier = Modifier.fillMaxSize(),
    ) {
        rule.setContent {
            OmnibusTheme {
                Box(modifier.testTag(READER)) {
                    ReaderWebView(
                        bookUuid = "test-book",
                        settings = settings,
                        controller = controller,
                        assetLoaderFactory = assetLoaderFactory,
                        onEvent = { synchronized(messages) { messages += it } },
                    )
                }
            }
        }
    }

    private fun awaitReady(controller: ReaderController) {
        rule.waitUntil(timeoutMillis = 30_000) { controller.status == ReaderLoadStatus.Ready }
    }

    /** The cfi of every movement relocate so far, ignoring the echoes a layout change reports. */
    private fun movements(): List<String> = synchronized(messages) {
        messages.filterIsInstance<ReaderEvent.Relocate>()
            .map { it.data }
            .filter { it.isMovement }
            .mapNotNull { it.cfi }
    }

    /** The host's load status, in the order the screen saw it. */
    private fun loadStatuses(): List<ReaderLoadStatus> = synchronized(messages) {
        messages.filterIsInstance<ReaderEvent.LoadStatusChanged>().map { it.status }
    }

    private fun awaitMovementCount(count: Int) {
        rule.waitUntil(timeoutMillis = 15_000) { movements().size >= count }
    }

    /**
     * Performs [gesture] until the reader reports one more movement. The first touch after a boot
     * can land while the glue is still settling, and a slow emulator makes that likely.
     */
    private fun moveBy(gesture: () -> Unit) {
        val target = movements().size + 1
        repeat(4) {
            gesture()
            val deadline = System.currentTimeMillis() + 5_000
            while (movements().size < target && System.currentTimeMillis() < deadline) Thread.sleep(100)
            if (movements().size >= target) return
        }
        error("the reader never moved")
    }

    private fun findWebView(view: View = rule.activity.window.decorView): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    @Test
    fun tc220_aTestEpubOpensAndSwipesAndEdgeTapsTurnThePage() {
        val controller = ReaderController()
        showReader(controller)
        awaitReady(controller)
        awaitMovementCount(1)
        val first = movements().last()

        moveBy { swipe(from = 0.8f, to = 0.2f) }
        val second = movements().last()
        assertNotEquals("a swipe left should move forward", first, second)

        moveBy { tap(0.95f) }
        val third = movements().last()
        assertNotEquals("a right-edge tap should move forward", second, third)

        moveBy { tap(0.05f) }
        assertEquals("a left-edge tap should move back", second, movements().last())

        moveBy { swipe(from = 0.2f, to = 0.8f) }
        assertEquals("a swipe right should move back", first, movements().last())
    }

    @Test
    fun tc221_theControllerTurnsPagesAndTheCentreTapSignalsTheChrome() {
        val controller = ReaderController()
        showReader(controller)
        awaitReady(controller)
        awaitMovementCount(1)

        controller.next()
        awaitMovementCount(2)
        controller.previous()
        awaitMovementCount(3)
        assertEquals(movements()[0], movements()[2])

        tap(0.5f)
        try {
            rule.waitUntil(timeoutMillis = 10_000) { synchronized(messages) { messages.any { it is ReaderEvent.ToggleChrome } } }
        } catch (e: Throwable) {
            throw AssertionError("no toggleChrome among " + synchronized(messages) { messages.map { it.javaClass.simpleName } }, e)
        }
    }

    @Test
    fun tc222_killingTheRendererReloadsTheBookAtTheLastPageWithTheSameSettings() {
        val settings = ReaderSettings(theme = ReaderTheme.Sepia, fontSize = 24)
        val controller = ReaderController()
        showReader(controller, settings)
        awaitReady(controller)
        awaitMovementCount(1)
        controller.next()
        awaitMovementCount(2)
        controller.next()
        awaitMovementCount(3)
        val lastPage = controller.restoreCfi
        assertNotNull(lastPage)
        assertEquals(movements().last(), lastPage)
        val requestsBefore = bookRequests
        val shownBefore = firstVisibleParagraph()
        assertTrue("the reader should be past the first page", shownBefore.isNotEmpty())

        // `chrome://crash` kills the renderer process, which is what the platform reports as gone.
        rule.runOnUiThread { findWebView()!!.loadUrl("chrome://crash") }
        rule.waitUntil(timeoutMillis = 30_000) { bookRequests > requestsBefore && controller.status == ReaderLoadStatus.Ready }

        assertEquals("the reboot must not lose the reader's place", lastPage, controller.restoreCfi)
        // The page that booted is the one the reader was on, not the start of the book.
        rule.waitUntil(timeoutMillis = 15_000) { firstVisibleParagraph().isNotEmpty() }
        assertEquals(shownBefore, firstVisibleParagraph())
        val fontSize = evaluate("String(parseInt(getComputedStyle(document.querySelector('iframe').contentDocument.body).fontSize))")
        assertEquals("\"24\"", fontSize)
    }

    @Test
    fun tc223_aServerFailureShowsAnErrorWithRetryNotAPermanentLoadingState() {
        bookResult = { BookFileResult.NotFound }
        val controller = ReaderController()
        showReader(controller)

        rule.waitUntil(timeoutMillis = 30_000) { controller.status is ReaderLoadStatus.Failed }
        assertEquals(ReaderLoadStatus.Failed(ReaderLoadFailure.NotFound), controller.status)
        rule.onNodeWithText("Try again").assertExists()
        assertNull("the failed page should not still be in the hierarchy", findWebView())

        bookResult = { BookFileResult.Opened(ByteArrayInputStream(epub), epub.size.toLong()) }
        rule.onNodeWithText("Try again").performClick()

        awaitReady(controller)
        assertNotNull(findWebView())
    }

    @Test
    fun tc224_aDarkThemeHasItsGroundOnTheWebViewAndThePageBeforeAnythingIsBooted() {
        val controller = ReaderController()
        showReader(controller, ReaderSettings(theme = ReaderTheme.Black))
        rule.waitUntil(timeoutMillis = 15_000) { findWebView() != null }

        // Without a controller boot there is no OmnibusReader.init to paint the ground, so this is
        // the document-start script's doing.
        rule.waitUntil(timeoutMillis = 15_000) {
            evaluate("getComputedStyle(document.documentElement).getPropertyValue('--page').trim()") == "\"#000000\""
        }
    }

    @Test
    fun tc225_theWebViewIsDestroyedWithTheComposableAndNothingKeepsDrivingIt() {
        val controller = ReaderController()
        var visible by mutableStateOf(true)
        rule.setContent {
            OmnibusTheme {
                if (visible) {
                    ReaderWebView("test-book", ReaderSettings(), controller, assetLoaderFactory)
                }
            }
        }
        awaitReady(controller)
        val webView = findWebView()!!
        rule.runOnUiThread { assertNotNull(webView.url) }

        rule.runOnUiThread { visible = false }
        rule.waitForIdle()

        rule.runOnUiThread {
            assertNull("the page should be out of the hierarchy", webView.parent)
            assertTrue("the WebView should be detached", !webView.isAttachedToWindow)
            // A destroyed WebView has no content left to report.
            assertNull("the WebView should have been destroyed", webView.url)
        }
        // The controller no longer reaches the page: a command after leaving is dropped, not run
        // against a destroyed WebView.
        val messagesBefore = synchronized(messages) { messages.size }
        controller.next()
        rule.waitForIdle()
        assertEquals(messagesBefore, synchronized(messages) { messages.size })
    }

    @Test
    fun tc228_theWebViewIsPausedAndResumedWithTheLifecycle() {
        val controller = ReaderController()
        showReader(controller)
        awaitReady(controller)
        fun ticks() = evaluate("String(window.__ticks)").trim('"').toInt()
        evaluate("window.__ticks = 0; setInterval(function () { window.__ticks++; }, 20); 0")
        rule.waitUntil(timeoutMillis = 15_000) { ticks() > 5 }

        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        val atPause = ticks()
        // A running 20 ms timer would tick about 35 times in this window. Proving that it stays
        // still needs elapsed time; a slow device can only make this pass, never fail.
        Thread.sleep(700)
        val whilePaused = ticks()
        assertTrue("timers should stop while paused ($atPause -> $whilePaused)", whilePaused - atPause <= 3)

        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitUntil(timeoutMillis = 15_000) { ticks() > whilePaused + 5 }
    }

    @Test
    fun tc226_aResizeKeepsTheReaderOnItsPageAndNeverCountsAsMovement() {
        val controller = ReaderController()
        // A fraction of the window, not a dp size: a fixed width is capped by a narrow emulator's
        // window, which leaves nothing to resize.
        var widthFraction by mutableStateOf(0.6f)
        rule.setContent {
            OmnibusTheme {
                Box(Modifier.fillMaxWidth(widthFraction).fillMaxHeight(0.8f).testTag(READER)) {
                    ReaderWebView(
                        "test-book",
                        ReaderSettings(),
                        controller,
                        assetLoaderFactory,
                        onEvent = { synchronized(messages) { messages += it } },
                    )
                }
            }
        }
        awaitReady(controller)
        awaitMovementCount(1)
        controller.next()
        awaitMovementCount(2)
        controller.next()
        awaitMovementCount(3)
        val before = controller.restoreCfi
        val movementsBefore = movements().size
        val relocatesBefore = synchronized(messages) { messages.count { it is ReaderEvent.Relocate } }

        val widthBefore = rule.onNodeWithTag(READER).fetchSemanticsNode().size.width
        rule.runOnUiThread { widthFraction = 1f }
        rule.waitForIdle()
        assertNotEquals("the resize must change the reader's width", widthBefore, rule.onNodeWithTag(READER).fetchSemanticsNode().size.width)
        rule.waitUntil(timeoutMillis = 15_000) { synchronized(messages) { messages.count { it is ReaderEvent.Relocate } } > relocatesBefore }
        rule.waitForIdle()

        assertEquals("a resize's re-pagination must not move the restore position", before, controller.restoreCfi)
        assertEquals("a resize must be reported as an echo", movementsBefore, movements().size)
    }

    @Test
    fun tc227_theReadingPositionSurvivesSavedStateRestore() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(rule)
        lateinit var controller: ReaderController
        restoration.setContent {
            OmnibusTheme {
                controller = rememberReaderController(startCfi = null)
                ReaderWebView("test-book", ReaderSettings(), controller, assetLoaderFactory)
            }
        }
        awaitReady(controller)
        rule.waitUntil(timeoutMillis = 15_000) { controller.restoreCfi != null }
        val firstPage = controller.restoreCfi
        controller.next()
        rule.waitUntil(timeoutMillis = 15_000) { controller.restoreCfi != firstPage }
        val saved = controller.restoreCfi

        restoration.emulateSavedInstanceStateRestore()

        assertEquals(saved, controller.restoreCfi)
    }

    // TC-21: the host decodes the page's messages, and the callback carries typed events.
    @Test
    fun tc21_onEventReceivesTypedPageEvents() {
        val controller = ReaderController()
        showReader(controller)
        awaitReady(controller)
        awaitMovementCount(1)
        rule.waitUntil(timeoutMillis = 15_000) { synchronized(messages) { messages.any { it is ReaderEvent.Toc } } }

        val pageEvents = synchronized(messages) { messages.filter { it !is ReaderEvent.LoadStatusChanged } }
        val hostReady = pageEvents.indexOfFirst { it == ReaderEvent.HostReady }
        val firstRelocate = pageEvents.indexOfFirst { it is ReaderEvent.Relocate }
        assertTrue("HostReady should arrive before the first relocate", hostReady >= 0 && firstRelocate > hostReady)
        val relocate = pageEvents.filterIsInstance<ReaderEvent.Relocate>().first().data
        assertTrue("the relocate should carry a cfi", !relocate.cfi.isNullOrBlank())
        val toc = pageEvents.filterIsInstance<ReaderEvent.Toc>().last()
        assertTrue("the toc should list the book's chapters", toc.entries.isNotEmpty())
    }

    // TC-84: the host reports each load status once, from Loading to Ready.
    @Test
    fun tc84_onEventReportsEachLoadStatusOnce() {
        val controller = ReaderController()
        showReader(controller)
        awaitReady(controller)
        // The screen collects the host's events asynchronously, so wait for the last one to arrive.
        rule.waitUntil(timeoutMillis = 15_000) { loadStatuses().lastOrNull() == ReaderLoadStatus.Ready }

        val statuses = loadStatuses()
        assertEquals("the first load status should be Loading", ReaderLoadStatus.Loading, statuses.first())
        assertEquals("the last load status should be Ready", ReaderLoadStatus.Ready, statuses.last())
        assertTrue("no two consecutive load statuses should be equal: $statuses", statuses.zipWithNext().none { it.first == it.second })
    }

    // TC-23: a failed open that is retried reports Failed, then Loading, then Ready.
    @Test
    fun tc23_loadStatusFollowsFailureAndRetry() {
        bookResult = { BookFileResult.NotFound }
        val controller = ReaderController()
        showReader(controller)
        rule.waitUntil(timeoutMillis = 30_000) { loadStatuses().contains(ReaderLoadStatus.Failed(ReaderLoadFailure.NotFound)) }

        bookResult = { BookFileResult.Opened(ByteArrayInputStream(epub), epub.size.toLong()) }
        rule.onNodeWithText("Try again").performClick()
        awaitReady(controller)
        rule.waitUntil(timeoutMillis = 15_000) { loadStatuses().lastOrNull() == ReaderLoadStatus.Ready }

        val statuses = loadStatuses()
        val failed = statuses.indexOf(ReaderLoadStatus.Failed(ReaderLoadFailure.NotFound))
        val loading = statuses.indexOfFirstAfter(failed, ReaderLoadStatus.Loading)
        val ready = statuses.indexOfFirstAfter(loading, ReaderLoadStatus.Ready)
        assertTrue("expected Failed, Loading, Ready in order among $statuses", failed >= 0 && loading > failed && ready > loading)
    }

    /** The index of the first [status] after [from], or -1. */
    private fun List<ReaderLoadStatus>.indexOfFirstAfter(from: Int, status: ReaderLoadStatus): Int =
        withIndex().indexOfFirst { it.index > from && it.value == status }

    /** Injects a tap through the window's input pipeline, as a finger would deliver it. */
    private fun tap(xFraction: Float, yFraction: Float = 0.5f) {
        val (x, y) = pointInWindow(xFraction, yFraction)
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, downTime, downTime, x, y)
        inject(MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis() + 60, x, y)
    }

    /** Injects a horizontal swipe from [from] to [to], as fractions of the WebView's width. */
    private fun swipe(from: Float, to: Float) {
        val (startX, y) = pointInWindow(from, 0.5f)
        val (endX, _) = pointInWindow(to, 0.5f)
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, downTime, downTime, startX, y)
        val steps = 10
        for (step in 1..steps) {
            val x = startX + (endX - startX) * step / steps
            inject(MotionEvent.ACTION_MOVE, downTime, downTime + step * 15L, x, y)
            Thread.sleep(15)
        }
        inject(MotionEvent.ACTION_UP, downTime, downTime + (steps + 1) * 15L, endX, y)
    }

    private fun pointInWindow(xFraction: Float, yFraction: Float): Pair<Float, Float> {
        val location = IntArray(2)
        lateinit var size: Pair<Int, Int>
        rule.runOnUiThread {
            val view = findWebView()!!
            view.getLocationOnScreen(location)
            size = view.width to view.height
        }
        return (location[0] + size.first * xFraction) to (location[1] + size.second * yFraction)
    }

    private fun inject(action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        val delivered = booleanArrayOf(false)
        rule.runOnUiThread {
            // Screen coordinates to the WebView's own.
            val location = IntArray(2)
            val view = findWebView()!!
            view.getLocationOnScreen(location)
            event.offsetLocation(-location[0].toFloat(), -location[1].toFloat())
            delivered[0] = view.dispatchTouchEvent(event)
        }
        event.recycle()
    }

    /** The text that opens the page on screen, found by what the section's paragraphs occupy. */
    private fun firstVisibleParagraph(): String =
        evaluate(
            """
            (function () {
              var frame = document.querySelector('iframe');
              var stage = document.getElementById('stage').getBoundingClientRect();
              var offset = frame.getBoundingClientRect();
              var paragraphs = frame.contentDocument.querySelectorAll('p');
              for (var i = 0; i < paragraphs.length; i++) {
                var r = paragraphs[i].getBoundingClientRect();
                var left = r.left + offset.left, right = r.right + offset.left;
                if (right > stage.left + 1 && left < stage.right - 1) return paragraphs[i].textContent.substring(0, 24);
              }
              return '';
            })()
            """.trimIndent(),
        ).trim('"')

    private fun evaluate(script: String): String {
        val result = arrayOfNulls<String>(1)
        rule.runOnUiThread { findWebView()?.evaluateJavascript(script) { result[0] = it } }
        val deadline = System.currentTimeMillis() + 10_000
        while (result[0] == null && System.currentTimeMillis() < deadline) Thread.sleep(50)
        return result[0].orEmpty()
    }

    private companion object {
        const val READER = "reader"
    }
}
