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
import androidx.compose.runtime.MutableState
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
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderMargins
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.ReaderTypeface
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
    ) = showReaderState(controller, mutableStateOf(settings), modifier)

    /** Shows the reader with [settings] read on every composition, so a change to it reaches the live page. */
    private fun showReaderState(
        controller: ReaderController,
        settings: MutableState<ReaderSettings>,
        modifier: Modifier = Modifier.fillMaxSize(),
    ) {
        rule.setContent {
            OmnibusTheme {
                Box(modifier.testTag(READER)) {
                    ReaderWebView(
                        bookUuid = "test-book",
                        settings = settings.value,
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

    private fun awaitMovementCount(count: Int) {
        rule.waitUntil(timeoutMillis = 15_000) { movements().size >= count }
    }

    /** Performs [gesture] until the reader reports one more movement. */
    private fun moveBy(gesture: () -> Unit) {
        val target = movements().size + 1
        repeatGesture(gesture) { movements().size >= target }
    }

    /**
     * Performs [gesture] until [reacted]. The first touch after a boot can land while the glue is
     * still settling, a slow emulator makes that likely, and the page reports no signal to wait for.
     */
    private fun repeatGesture(gesture: () -> Unit, reacted: () -> Boolean) {
        repeat(4) {
            gesture()
            val deadline = System.currentTimeMillis() + 5_000
            while (!reacted() && System.currentTimeMillis() < deadline) Thread.sleep(100)
            if (reacted()) return
        }
        error("no reaction among " + synchronized(messages) { messages.map { it.javaClass.simpleName } })
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
    fun tc221_theCentreTapSignalsTheChrome() {
        val controller = ReaderController()
        showReader(controller)
        awaitReady(controller)
        awaitMovementCount(1)

        repeatGesture(gesture = { tap(0.5f) }) { synchronized(messages) { messages.any { it is ReaderEvent.ToggleChrome } } }
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

    // TC-21: the host decodes the page's messages, and the callback carries typed events.
    @Test
    fun tc21_onEventReceivesTheBooksToc() {
        val controller = ReaderController()
        showReader(controller)
        awaitReady(controller)
        rule.waitUntil(timeoutMillis = 15_000) { synchronized(messages) { messages.any { it is ReaderEvent.Toc } } }

        val toc = synchronized(messages) { messages.filterIsInstance<ReaderEvent.Toc>().last() }
        assertTrue("the toc should list the book's chapters", toc.entries.isNotEmpty())
    }

    @Test
    fun tc308_pageCallsRoundTrip() {
        val controller = ReaderController()
        showReader(controller)
        awaitReady(controller)
        awaitMovementCount(1)

        assertEquals(JsonPrimitive(2), call(controller, "1+1"))
        assertEquals(buildJsonObject { put("a", 1) }, call(controller, "Promise.resolve({a:1})"))
        assertNull(call(controller, "(()=>{throw 1})()"))
    }

    /** Awaits [expr] on the page. [ReaderController.call] must run on the main thread, where its replies arrive. */
    private fun call(controller: ReaderController, expr: String): JsonElement? =
        runBlocking(Dispatchers.Main) { controller.call(expr) }

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

    /** The body's computed [property] as the page reports it, with the JSON quotes. */
    private fun bodyStyle(property: String): String =
        evaluate("getComputedStyle(document.querySelector('iframe').contentDocument.body).$property")

    /** The body's computed [property] as a whole number, the way the existing size check reads it. */
    private fun bodyNumber(property: String): String =
        evaluate("String(parseFloat(getComputedStyle(document.querySelector('iframe').contentDocument.body).$property))")
            .trim('"')

    /** The text column's width as a fraction of the stage's, which the margins set. */
    private fun columnRatio(): Double = evaluate(
        "String(document.getElementById('column').getBoundingClientRect().width / " +
            "document.getElementById('stage').getBoundingClientRect().width)",
    ).trim('"').toDouble()

    /** Waits until the column is within 0.02 of [expected] of the stage, since a resize settles after a debounce. */
    private fun awaitColumnRatio(what: String, expected: Double) {
        try {
            rule.waitUntil(timeoutMillis = 15_000) { kotlin.math.abs(columnRatio() - expected) <= 0.02 }
        } catch (e: Throwable) {
            throw AssertionError("$what should be about $expected of the stage but is ${columnRatio()}", e)
        }
    }

    /** Waits for a paragraph to be on screen, then checks it is the one [shownBefore] named. */
    private fun assertKeptPlace(shownBefore: String) {
        rule.waitUntil(timeoutMillis = 15_000) { firstVisibleParagraph().isNotEmpty() }
        assertEquals("the reader must keep its place", shownBefore, firstVisibleParagraph())
    }

    /** The body's computed line height over its font size, which is the unitless multiplier. */
    private fun bodyLineHeightRatio(): Double {
        val raw = evaluate(
            """
            (function () {
              var style = getComputedStyle(document.querySelector('iframe').contentDocument.body);
              return String(parseFloat(style.lineHeight) / parseFloat(style.fontSize));
            })()
            """.trimIndent(),
        ).trim('"')
        return raw.toDouble()
    }

    /** The host page's `--page` ground, without the quotes the result is wrapped in. */
    private fun pageGround(): String =
        evaluate("getComputedStyle(document.documentElement).getPropertyValue('--page').trim()").trim('"')

    /** Waits until [read] gives [expected], and says what it gave instead if it never does. */
    private fun awaitValue(what: String, expected: String, read: () -> String) {
        try {
            rule.waitUntil(timeoutMillis = 10_000) { read() == expected }
        } catch (e: Throwable) {
            throw AssertionError("$what should be $expected but is ${read()}", e)
        }
    }

    /** Waits until [read] satisfies [accept], and says what it gave instead if it never does. */
    private fun awaitWhen(what: String, accept: (String) -> Boolean, read: () -> String) {
        try {
            rule.waitUntil(timeoutMillis = 10_000) { accept(read()) }
        } catch (e: Throwable) {
            throw AssertionError("$what was ${read()}", e)
        }
    }

    private fun changeSettings(change: () -> Unit) = rule.runOnIdle(change)

    private fun bootCount(): Int = synchronized(messages) { messages.count { it == ReaderEvent.HostReady } }

    @Test
    fun firstPaintUsesTheStoredSettingsWithoutAnyChange() {
        val controller = ReaderController()
        val settings = ReaderSettings(
            theme = ReaderTheme.Sepia,
            typeface = ReaderTypeface.Classic,
            fontSize = 24,
            margins = ReaderMargins.Wide,
        )
        showReader(controller, settings)
        awaitReady(controller)

        assertEquals(settings.theme.groundHex.lowercase(), pageGround())
        assertEquals(settings.fontSize.toString(), bodyNumber("fontSize"))
        awaitColumnRatio("the Wide column", 0.65)
        assertTrue(bodyStyle("fontFamily").contains("EB Garamond"))
        assertEquals(1, bootCount())
    }

    @Test
    fun aSettingChangeUpdatesThePaintedPageWithoutReloadingIt() {
        val controller = ReaderController()
        val settings = mutableStateOf(ReaderSettings())
        showReaderState(controller, settings)
        awaitReady(controller)
        awaitMovementCount(1)
        val bootsBefore = bootCount()
        val shownBefore = firstVisibleParagraph()
        val originalFace = bodyStyle("fontFamily")

        changeSettings { settings.value = settings.value.copy(theme = ReaderTheme.Light) }
        awaitValue("the page ground", ReaderTheme.Light.groundHex.lowercase(), ::pageGround)

        changeSettings { settings.value = settings.value.copy(fontSize = 24) }
        awaitValue("the font size", "24", { bodyNumber("fontSize") })

        changeSettings { settings.value = settings.value.copy(lineHeight = 2.0) }
        awaitWhen("the line height ratio", { kotlin.math.abs(it.toDouble() - 2.0) <= 0.05 }, { bodyLineHeightRatio().toString() })

        changeSettings { settings.value = settings.value.copy(justify = false) }
        awaitValue("the text alignment", "\"start\"", { bodyStyle("textAlign") })
        changeSettings { settings.value = settings.value.copy(justify = true) }
        awaitValue("the text alignment", "\"justify\"", { bodyStyle("textAlign") })

        changeSettings { settings.value = settings.value.copy(margins = ReaderMargins.Narrow) }
        awaitColumnRatio("the Narrow column", 0.95)

        changeSettings { settings.value = settings.value.copy(typeface = ReaderTypeface.Mono) }
        awaitWhen("the face", { it.contains("Droid Sans Mono") }, { bodyStyle("fontFamily") })
        changeSettings { settings.value = settings.value.copy(typeface = ReaderTypeface.Original) }
        awaitValue("the book's own face", originalFace, { bodyStyle("fontFamily") })

        assertEquals("the page must not reload for a change", bootsBefore, bootCount())
        assertKeptPlace(shownBefore)
    }

    /**
     * The midpoints of the stage's two gutters beside the column, as fractions of the page width.
     * Asserts each point lies in its gutter: inside the stage and outside the column.
     */
    private fun gutterFractions(): Pair<Float, Float> {
        val raw = evaluate(
            """
            (function () {
              var s = document.getElementById('stage').getBoundingClientRect();
              var c = document.getElementById('column').getBoundingClientRect();
              var w = window.innerWidth;
              var leftPoint = (s.left + c.left) / 2;
              var rightPoint = (c.right + s.right) / 2;
              return [leftPoint / w, rightPoint / w,
                      s.left < leftPoint && leftPoint < c.left,
                      c.right < rightPoint && rightPoint < s.right].join(',');
            })()
            """.trimIndent(),
        ).trim('"').split(',')
        assertTrue("the left point should sit in the left gutter", raw[2] == "true")
        assertTrue("the right point should sit in the right gutter", raw[3] == "true")
        return raw[0].toFloat() to raw[1].toFloat()
    }

    @Test
    fun edgeTapsTurnPagesBesideANarrowedColumn() {
        val settings = mutableStateOf(ReaderSettings(margins = ReaderMargins.Wide))
        val controller = ReaderController()
        showReaderState(controller, settings)
        awaitReady(controller)
        awaitColumnRatio("the Wide column", 0.65)
        awaitMovementCount(1)

        val (left, right) = gutterFractions()
        val start = movements().last()

        // Right first: a left tap on the book's first page has nowhere to go.
        moveBy { tap(right) }
        assertNotEquals("a right gutter tap should move forward", start, movements().last())

        moveBy { tap(left) }
        assertEquals("a left gutter tap should move back", start, movements().last())
    }

    /** The first paragraph on the column, or "" when none is: a null result means the page was mid-relayout. */
    private fun firstVisibleParagraph(): String =
        evaluate(
            """
            (function () {
              var frame = document.querySelector('iframe');
              // The column is what the reader sees: epub.js clips the neighbouring pages lying under the stage's gutters.
              var column = document.getElementById('column').getBoundingClientRect();
              var offset = frame.getBoundingClientRect();
              var paragraphs = frame.contentDocument.querySelectorAll('p');
              for (var i = 0; i < paragraphs.length; i++) {
                var r = paragraphs[i].getBoundingClientRect();
                var left = r.left + offset.left, right = r.right + offset.left;
                if (right > column.left + 1 && left < column.right - 1) return paragraphs[i].textContent.substring(0, 24);
              }
              return '';
            })()
            """.trimIndent(),
        ).trim('"').takeIf { it != "null" }.orEmpty()

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
