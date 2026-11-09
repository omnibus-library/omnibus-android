package com.omnibus.omnibus.feature.reader.ui.curl

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderPageTurn
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.feature.reader.ui.ReaderController
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.ReaderLoadStatus
import com.omnibus.omnibus.feature.reader.ui.ReaderWebView
import com.omnibus.omnibus.feature.reader.ui.TestEpub
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

// TC-336, TC-337, TC-340 to TC-342: the curl on the reader page, driven through the page turn frame. Touches are
// fractions of the frame, so the test does not depend on the window size.
class PageCurlTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val epub = TestEpub.build()
    private val messages = mutableListOf<ReaderEvent>()
    private val assetLoaderFactory = ReaderAssetLoaderFactory(
        context,
        Dispatchers.Unconfined,
        BookFileSource { BookFileResult.Opened(ByteArrayInputStream(epub), epub.size.toLong()) },
    )

    @Test
    fun tc336_aGutterTapCurls() {
        val controller = ReaderController()
        showReader(controller, reducedMotion = false)
        awaitReady(controller)
        rule.waitUntil(timeoutMillis = 30_000) { curlReady() }
        val before = movements().size

        repeatGesture(gesture = { tap(0.95f) }) { movements().size > before }

        rule.waitUntil(timeoutMillis = 15_000) { !coverShown() }
        assertEquals("the tap should play exactly one curl", 1, curlsPlayed())
    }

    @Test
    fun tc337_reducedMotionLandsWithoutACurl() {
        val controller = ReaderController()
        showReader(controller, reducedMotion = true)
        awaitReady(controller)
        awaitMovementCount(1)
        val before = movements().size

        repeatGesture(gesture = { tap(0.95f) }) { movements().size > before }

        assertFalse("reduced motion should never show a cover", coverEverShown())
        assertEquals("reduced motion should never play a curl", 0, curlsPlayed())
    }

    @Test
    fun tc340_aLongSwipeTurns() {
        val controller = ReaderController()
        showReader(controller, reducedMotion = false)
        awaitReady(controller)
        rule.waitUntil(timeoutMillis = 30_000) { curlReady() }
        val before = movements().size

        repeatGesture(gesture = { drag(from = 0.9f to 0.5f, to = 0.2f to 0.5f, steps = 10, stepMs = 15L) }) {
            movements().size > before
        }

        rule.waitUntil(timeoutMillis = 15_000) { !coverShown() }
        assertEquals("the swipe should play exactly one curl", 1, curlsPlayed())
    }

    @Test
    fun tc341_aShortSlowSwipeCancels() {
        val controller = ReaderController()
        showReader(controller, reducedMotion = false)
        awaitReady(controller)
        rule.waitUntil(timeoutMillis = 30_000) { curlReady() }
        val before = movements().size

        repeatGesture(gesture = { drag(from = 0.9f to 0.5f, to = 0.7f to 0.5f, steps = 20, stepMs = 40L) }) {
            curlsPlayed() > 0
        }

        rule.waitUntil(timeoutMillis = 15_000) { !coverShown() }
        assertEquals("the swipe should have followed the finger", 1, curlsPlayed())
        assertEquals("a cancelled swipe should not turn the page", before, movements().size)
    }

    @Test
    fun tc342_aLongPressDragSelects() {
        val controller = ReaderController()
        showReader(controller, reducedMotion = false)
        awaitReady(controller)
        rule.waitUntil(timeoutMillis = 30_000) { curlReady() }
        val (pressX, pressY) = glyphOnScreen()
        val before = movements().size

        val longPressDrag = {
            drag(from = pressX to pressY, to = pressX * 0.5f to pressY, steps = 10, stepMs = 15L, holdMs = 800L)
        }
        repeatGesture(gesture = longPressDrag) { selections() > 0 }

        assertEquals("a long press should not start a curl", 0, curlsPlayed())
        assertEquals("a long press should not turn the page", before, movements().size)
    }

    /** Shows the reader filling the window, in a curl that reads the given reduced-motion answer. */
    private fun showReader(controller: ReaderController, reducedMotion: Boolean) {
        val settings = mutableStateOf(ReaderSettings(pageTurn = ReaderPageTurn.Curl))
        rule.setContent {
            OmnibusTheme {
                CompositionLocalProvider(
                    LocalCurlEnvironment provides CurlEnvironment(
                        reducedMotion = { reducedMotion },
                        isLowRam = { false },
                    ),
                ) {
                    Box(Modifier.fillMaxSize().testTag(READER)) {
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

    /**
     * Performs [gesture] until [reacted]. The first touch after a boot can land while the glue is still settling, and
     * the page reports no signal to wait for, so a touch that gets no reaction is sent again, with a bound.
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

    /**
     * Drags the frame from [from] to [to] (fractions of its size) in [steps] moves, one [stepMs] apart on the event
     * clock, after holding the finger down for [holdMs] of real time. The hold is real so the page's own long-press
     * timer sees it; the frame decides on event times.
     */
    private fun drag(from: Pair<Float, Float>, to: Pair<Float, Float>, steps: Int, stepMs: Long, holdMs: Long = 0L) {
        val (width, height) = frameSize()
        fun pointAt(fraction: Float): Pair<Float, Float> {
            val x = from.first + (to.first - from.first) * fraction
            val y = from.second + (to.second - from.second) * fraction
            return width * x to height * y
        }
        val downTime = SystemClock.uptimeMillis()
        val (startX, startY) = pointAt(0f)
        dispatch(MotionEvent.ACTION_DOWN, downTime, downTime, startX, startY)
        if (holdMs > 0) Thread.sleep(holdMs)
        for (step in 1..steps) {
            val (x, y) = pointAt(step.toFloat() / steps)
            dispatch(MotionEvent.ACTION_MOVE, downTime, downTime + holdMs + step * stepMs, x, y)
        }
        val (endX, endY) = pointAt(1f)
        dispatch(MotionEvent.ACTION_UP, downTime, downTime + holdMs + (steps + 1) * stepMs, endX, endY)
    }

    private fun frameSize(): Pair<Int, Int> {
        var size = 0 to 0
        rule.runOnUiThread { size = frame().width to frame().height }
        return size
    }

    private fun selections(): Int = synchronized(messages) { messages.count { it is ReaderEvent.Selection } }

    /** Taps the frame at [xFraction] of its width, the way a finger would. */
    private fun tap(xFraction: Float, yFraction: Float = 0.5f) {
        var width = 0
        var height = 0
        rule.runOnUiThread {
            width = frame().width
            height = frame().height
        }
        val x = width * xFraction
        val y = height * yFraction
        val downTime = SystemClock.uptimeMillis()
        dispatch(MotionEvent.ACTION_DOWN, downTime, downTime, x, y)
        dispatch(MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis() + 60, x, y)
    }

    private fun dispatch(action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        rule.runOnUiThread { frame().dispatchTouchEvent(event) }
        event.recycle()
    }

    private fun curlReady(): Boolean {
        var ready = false
        rule.runOnUiThread { ready = frame().curlReady }
        return ready
    }

    private fun coverShown(): Boolean {
        var shown = false
        rule.runOnUiThread { shown = frame().coverShown }
        return shown
    }

    private fun coverEverShown(): Boolean {
        var shown = false
        rule.runOnUiThread { shown = frame().coverEverShown }
        return shown
    }

    private fun curlsPlayed(): Int {
        var played = 0
        rule.runOnUiThread { played = frame().curlsPlayed }
        return played
    }

    /** The frame around the reader's WebView, which holds the curl. */
    private fun frame(): PageTurnFrame = findWebView()!!.parent as PageTurnFrame

    /**
     * The centre of the first glyph on screen, as fractions of the page. A long press selects only on text, so the
     * point comes from the page's own layout rather than a guessed position.
     */
    private fun glyphOnScreen(): Pair<Float, Float> {
        val raw = evaluate(
            """
            (function () {
              var w = window.innerWidth, h = window.innerHeight;
              var frames = document.querySelectorAll('iframe');
              for (var i = 0; i < frames.length; i++) {
                var doc = frames[i].contentDocument;
                if (!doc || !doc.body) continue;
                var fr = frames[i].getBoundingClientRect();
                var walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT);
                for (var node = walker.nextNode(); node; node = walker.nextNode()) {
                  var first = node.nodeValue.search(/\S/);
                  if (first < 0) continue;
                  var range = doc.createRange();
                  range.setStart(node, first);
                  range.setEnd(node, first + 1);
                  var box = range.getBoundingClientRect();
                  var x = fr.left + box.left + box.width / 2;
                  var y = fr.top + box.top + box.height / 2;
                  if (box.width > 0 && x > 0 && x < w && y > 0 && y < h) return (x / w) + ',' + (y / h);
                }
              }
              return 'none';
            })()
            """.trimIndent(),
        ).trim('"')
        assertNotEquals("the first page should show text", "none", raw)
        val (x, y) = raw.split(',')
        return x.toFloat() to y.toFloat()
    }

    /** Runs [script] in the reader page and returns its JSON answer. */
    private fun evaluate(script: String): String {
        val result = arrayOfNulls<String>(1)
        rule.runOnUiThread { findWebView()?.evaluateJavascript(script) { result[0] = it } }
        val deadline = System.currentTimeMillis() + 10_000
        while (result[0] == null && System.currentTimeMillis() < deadline) Thread.sleep(50)
        return result[0] ?: error("the page did not answer the script")
    }

    private fun findWebView(view: View = rule.activity.window.decorView): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private companion object {
        const val READER = "reader"
    }
}
