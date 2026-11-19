package com.omnibus.omnibus.feature.reader.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.feature.reader.KnownHighlight
import com.omnibus.omnibus.feature.reader.PageHighlight
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.feature.reader.ReaderUIState
import com.omnibus.omnibus.feature.reader.highlightMenuOf
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A tap on overlapping highlights reports every highlight under the point, in book order. Runs the whole screen, as
 * [ReaderHighlightMarksTest] does, so the page draws the highlights the tap lands on.
 */
class ReaderHighlightTapTest {

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
    fun issue299_tc3_aTapOnOverlappingHighlightsReportsEveryHitInBookOrder() {
        val (controller, ui) = mount(menuOnTap = false)
        rule.waitUntil(timeoutMillis = 30_000) { controller.status == ReaderLoadStatus.Ready }

        // The ranges are selected before any highlight is drawn, so no mark sits over the text being selected.
        // "Chapter 1 paragraph" and "paragraph 1. The quick" overlap on "paragraph"; "paragraph 2" is elsewhere.
        val earlier = select(controller, paragraph = 0, from = 1, to = 15)
        val later = select(controller, paragraph = 0, from = 12, to = 29)
        val lone = select(controller, paragraph = 1, from = 3, to = null)

        // The highlights are given to the page in the reverse of book order.
        rule.runOnUiThread {
            ui.value = ui.value.copy(
                pageHighlights = listOf(later, earlier, lone).map {
                    PageHighlight(it.cfiRange!!, HighlightColor.Amber, hasNote = false)
                },
            )
        }
        rule.waitUntil(timeoutMillis = 15_000) { markCount(controller) == 3 }
        call(controller, ReaderScripts.WHEN_SETTLED)

        // The tap lands on the 16th character, which both overlapping highlights cover.
        val point = charPoint(controller, paragraph = 0, index = 15)
        repeatGesture({ tapAt(point.number("x"), point.number("y")) }) { taps().isNotEmpty() }

        val tap = taps().first()
        assertEquals(
            "the tap should report both overlapping highlights, earlier start first",
            listOf(earlier.cfiRange, later.cfiRange),
            tap.hits.map { it.cfiRange },
        )
        assertEquals("the top-level range should be the first hit", tap.hits.first().cfiRange, tap.cfiRange)
        assertTrue("every hit should carry its rects", tap.hits.all { it.rects.isNotEmpty() })
    }

    // TC-23
    @Test
    fun issue299_tc23_tappingAHighlightOpensTheMenuBesideIt() {
        val (controller, ui) = mount(menuOnTap = true)
        rule.waitUntil(timeoutMillis = 30_000) { ui.value.loadStatus == ReaderLoadStatus.Ready }

        val highlight = select(controller, paragraph = 0, from = 1, to = 15)
        rule.runOnUiThread {
            ui.value = ui.value.copy(pageHighlights = listOf(PageHighlight(highlight.cfiRange!!, HighlightColor.Amber, hasNote = false)))
        }
        rule.waitUntil(timeoutMillis = 15_000) { markCount(controller) == 1 }
        call(controller, ReaderScripts.WHEN_SETTLED)

        val point = charPoint(controller, paragraph = 0, index = 8)
        repeatGesture({ tapAt(point.number("x"), point.number("y")) }) { ui.value.highlightMenu != null }
        rule.waitUntil(timeoutMillis = 5_000) { menuNodes().isNotEmpty() }

        val tap = taps().first()
        val density = rule.activity.resources.displayMetrics.density
        val webView = rule.runOnUiThread { findWebView() }!!
        val origin = IntArray(2)
        rule.runOnUiThread { webView.getLocationInWindow(origin) }
        val highlightLeft = origin[0] + tap.rects.minOf { it.x } * density
        val highlightRight = origin[0] + tap.rects.maxOf { it.x + it.width } * density
        val highlightTop = origin[1] + tap.rects.minOf { it.y } * density
        val highlightBottom = origin[1] + tap.rects.maxOf { it.y + it.height } * density
        val menu = rule.onNodeWithTag(ReaderHighlightMenuTags.MENU, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        val root = rule.onRoot().fetchSemanticsNode().boundsInWindow

        assertTrue("the menu should overlap the highlight's horizontal span", menu.left < highlightRight && menu.right > highlightLeft)
        assertTrue(
            "the menu should sit wholly below or above the highlight",
            menu.top >= highlightBottom || menu.bottom <= highlightTop,
        )
        assertTrue(
            "the menu should lie inside the window",
            menu.left >= root.left && menu.right <= root.right && menu.top >= root.top && menu.bottom <= root.bottom,
        )
    }

    // TC-24
    @Test
    fun issue299_tc24_aTapOnAHighlightNearTheEdgeTurnsNoPageAndTogglesNoChrome() {
        val (controller, ui) = mount(menuOnTap = false)
        rule.waitUntil(timeoutMillis = 30_000) { controller.status == ReaderLoadStatus.Ready }

        // The menu is not mounted here, so no scrim can be what stops the page turning.
        val word = select(controller, paragraph = 0, from = 1, to = null)
        rule.runOnUiThread {
            ui.value = ui.value.copy(pageHighlights = listOf(PageHighlight(word.cfiRange!!, HighlightColor.Amber, hasNote = false)))
        }
        rule.waitUntil(timeoutMillis = 15_000) { markCount(controller) == 1 }
        call(controller, ReaderScripts.WHEN_SETTLED)

        val mark = synchronized(messages) { messages.size }
        val edge = word.rects.first()
        repeatGesture({ tapAt(edge.x + 2, edge.y + edge.height / 2) }) { taps().isNotEmpty() }
        call(controller, ReaderScripts.WHEN_SETTLED)

        val events = synchronized(messages) { messages.drop(mark) }
        val tapIndex = events.indexOfFirst { it is ReaderEvent.AnnotationTap }
        assertTrue("the tap should reach the highlight", tapIndex >= 0)
        val afterTap = events.drop(tapIndex + 1)
        assertTrue("the tap should not toggle the chrome", afterTap.none { it is ReaderEvent.ToggleChrome })
        assertTrue("the tap should not turn the page", afterTap.none { it is ReaderEvent.TurnRequest })
        assertTrue("the tap should not move the page", afterTap.none { it is ReaderEvent.Relocate && !it.data.echo })
    }

    /**
     * Mounts the whole reader on the test book, not yet tapped. With [menuOnTap], the state follows the two events the
     * highlight menu needs, as the ViewModel does: the load status, and a menu over the tapped highlight.
     */
    private fun mount(menuOnTap: Boolean): Pair<ReaderController, MutableState<ReaderUIState>> {
        val controller = ReaderController()
        val ui = mutableStateOf(ReaderUIState(book = ReaderBookState.Ready(title = "Test Book")))
        rule.setContent {
            OmnibusTheme {
                ReaderScreenContent(
                    uiState = ui.value,
                    bookUuid = "highlight-tap",
                    controller = controller,
                    assetLoaderFactory = assetLoaderFactory,
                    commands = emptyFlow(),
                    onPageEvent = { event ->
                        synchronized(messages) { messages += event }
                        if (menuOnTap) reduce(ui, event)
                    },
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
        return controller to ui
    }

    /** The part of the ViewModel these tests need: the load status, and the menu a tap opens over the drawn highlights. */
    private fun reduce(ui: MutableState<ReaderUIState>, event: ReaderEvent) {
        when (event) {
            is ReaderEvent.LoadStatusChanged -> ui.value = ui.value.copy(loadStatus = event.status)
            is ReaderEvent.AnnotationTap -> {
                val known = ui.value.pageHighlights.associate { it.cfiRange.trim() to KnownHighlight(it.color, "text", it.hasNote) }
                ui.value = ui.value.copy(highlightMenu = highlightMenuOf(event.data, known))
            }
            else -> Unit
        }
    }

    private fun menuNodes() = rule.onAllNodesWithTag(ReaderHighlightMenuTags.MENU, useUnmergedTree = true).fetchSemanticsNodes()

    /**
     * Selects the text from the character at [from] to the one at [to] in paragraph [paragraph], or the word at [from]
     * when [to] is null, and returns the selection once the page reports it settled.
     */
    private fun select(controller: ReaderController, paragraph: Int, from: Int, to: Int?): SelectionData {
        val mark = synchronized(messages) { messages.size }
        val start = charPoint(controller, paragraph, from)
        call(controller, "OmnibusReader.beginSelectionAt(${start.number("x")}, ${start.number("y")})")
        if (to != null) {
            val end = charPoint(controller, paragraph, to)
            // The glue's extendSelectionTo returns nothing, which the page reports as no answer, so the call returns true.
            val extend = ReaderScripts.extendSelectionTo(end.number("x"), end.number("y"), end.number("x"))
            call(controller, "(function(){$extend;return true;})()")
        }
        // The range is only reported once the selection settles, so end the press's drag first.
        rule.runOnUiThread { controller.endEdgeDrag() }
        rule.waitUntil(timeoutMillis = 15_000) { settledSince(mark) != null }
        val selection = settledSince(mark)!!
        rule.runOnUiThread { controller.clearSelection() }
        return selection
    }

    /** The last selection the page reported after [mark] that has settled, or null. */
    private fun settledSince(mark: Int): SelectionData? = synchronized(messages) {
        messages.drop(mark)
            .filterIsInstance<ReaderEvent.Selection>()
            .map { it.data }
            .lastOrNull { !it.dragging && it.cfiRange != null }
    }

    /** The host-window point at the centre of character [index] of paragraph [paragraph] in the chapter. */
    private fun charPoint(controller: ReaderController, paragraph: Int, index: Int): JsonObject = call(
        controller,
        "(function(){var f=document.querySelector('iframe');var o=f.getBoundingClientRect();" +
            "var d=f.contentDocument;var t=d.createTreeWalker(d.querySelectorAll('p')[$paragraph],NodeFilter.SHOW_TEXT).nextNode();" +
            "var g=d.createRange();g.setStart(t,$index);g.setEnd(t,${index + 1});var r=g.getBoundingClientRect();" +
            "return {x:o.left+r.left+r.width/2,y:o.top+r.top+r.height/2};})()",
    ).jsonObject

    /** Taps the WebView at [x] and [y] CSS px, as a finger's down and up 60 ms apart. */
    private fun tapAt(x: Double, y: Double) {
        val density = rule.activity.resources.displayMetrics.density
        val viewX = (x * density).toFloat()
        val viewY = (y * density).toFloat()
        val downTime = SystemClock.uptimeMillis()
        rule.runOnUiThread {
            val webView = findWebView()!!
            dispatch(webView, MotionEvent.ACTION_DOWN, downTime, downTime, viewX, viewY)
            dispatch(webView, MotionEvent.ACTION_UP, downTime, downTime + 60, viewX, viewY)
        }
    }

    private fun dispatch(webView: WebView, action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        webView.dispatchTouchEvent(event)
        event.recycle()
    }

    /** Performs [gesture] until [reacted], up to four times: the first touch after a boot can land while the glue settles. */
    private fun repeatGesture(gesture: () -> Unit, reacted: () -> Boolean) {
        repeat(4) {
            gesture()
            val deadline = System.currentTimeMillis() + 5_000
            while (!reacted() && System.currentTimeMillis() < deadline) Thread.sleep(100)
            if (reacted()) return
        }
        error("no reaction among " + synchronized(messages) { messages.map { it.javaClass.simpleName } })
    }

    private fun taps(): List<AnnotationTapData> = synchronized(messages) {
        messages.filterIsInstance<ReaderEvent.AnnotationTap>().map { it.data }
    }

    /** The number of highlight marks the page draws in the amber colour, in the top document. */
    private fun markCount(controller: ReaderController): Int =
        call(controller, "document.querySelectorAll(\"#stage svg g[ref='hl-amber']\").length").jsonPrimitive.int

    private fun findWebView(view: View = rule.activity.window.decorView): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun JsonObject.number(key: String): Double = getValue(key).jsonPrimitive.double

    /** Awaits [expr] on the page. [ReaderController.call] must run on the main thread, where its replies arrive. */
    private fun call(controller: ReaderController, expr: String): JsonElement =
        runBlocking(Dispatchers.Main) { controller.call(expr) } ?: error("the page did not answer: $expr")
}
