package com.omnibus.omnibus.feature.reader.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.feature.reader.PageHighlight
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.feature.reader.ReaderUIState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger
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
import org.junit.Rule
import org.junit.Test

/**
 * Saved highlights drawn on the page once it is ready, and kept drawn through a font change and a renderer reboot. Runs
 * the whole screen, so the ViewModel's `pageHighlights` reach the controller through [ReaderScreenContent].
 */
class ReaderHighlightMarksTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val epub = TestEpub.build()
    private val messages = mutableListOf<ReaderEvent>()
    private val bookOpens = AtomicInteger()
    private val assetLoaderFactory = ReaderAssetLoaderFactory(
        context,
        Dispatchers.Unconfined,
        BookFileSource {
            bookOpens.incrementAndGet()
            BookFileResult.Opened(ByteArrayInputStream(epub), epub.size.toLong())
        },
    )

    @Test
    fun issue54_tc21_aSavedHighlightIsDrawnOnceThroughFontAndRendererChanges() {
        val controller = ReaderController()
        var state by mutableStateOf(ReaderUIState(book = ReaderBookState.Ready(title = "Test Book")))
        rule.setContent {
            OmnibusTheme {
                ReaderScreenContent(
                    uiState = state,
                    bookUuid = "highlight-marks",
                    controller = controller,
                    assetLoaderFactory = assetLoaderFactory,
                    commands = emptyFlow(),
                    onPageEvent = { event -> synchronized(messages) { messages += event } },
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
        rule.waitUntil(timeoutMillis = 30_000) { controller.status == ReaderLoadStatus.Ready }

        // The CFI comes from the page's own selection of its first word, so it is one the glue can locate.
        val letter = firstLetterCenter(controller)
        call(controller, "OmnibusReader.beginSelectionAt(${letter.number("x")}, ${letter.number("y")})")
        rule.waitUntil(timeoutMillis = 15_000) { selections().isNotEmpty() }
        // The range is only reported once the selection settles, so end the press's drag first.
        rule.runOnUiThread { controller.endEdgeDrag() }
        rule.waitUntil(timeoutMillis = 15_000) { selections().any { !it.dragging && it.cfiRange != null } }
        val cfi = selections().first { !it.dragging && it.cfiRange != null }.cfiRange!!
        rule.runOnUiThread { controller.clearSelection() }

        rule.runOnUiThread {
            state = state.copy(pageHighlights = listOf(PageHighlight(cfi, HighlightColor.Amber, hasNote = false)))
        }
        rule.waitUntil(timeoutMillis = 15_000) { markCount(controller) == 1 }

        var target = 0
        rule.runOnUiThread {
            val next = state.settings.withFontSizeStep(if (state.settings.canIncreaseFontSize) 1 else -1)
            target = next.fontSize
            state = state.copy(settings = next)
        }
        rule.waitUntil(timeoutMillis = 15_000) { bodyFontSize(controller) == target }
        call(controller, ReaderScripts.WHEN_SETTLED)
        assertEquals("a font change should keep exactly one mark", 1, markCount(controller))

        val opensBefore = bookOpens.get()
        // `chrome://crash` kills the renderer process, which is what the platform reports as gone.
        rule.runOnUiThread { findWebView()!!.loadUrl("chrome://crash") }
        rule.waitUntil(timeoutMillis = 30_000) {
            bookOpens.get() > opensBefore && controller.status == ReaderLoadStatus.Ready
        }
        call(controller, ReaderScripts.WHEN_SETTLED)
        assertEquals("the reboot should draw exactly one mark", 1, markCount(controller))
    }

    private fun selections(): List<SelectionData> = synchronized(messages) {
        messages.filterIsInstance<ReaderEvent.Selection>().map { it.data }
    }

    /** The host-window point at the centre of the first letter of the chapter's first paragraph. */
    private fun firstLetterCenter(controller: ReaderController): JsonObject = call(
        controller,
        "(function(){var f=document.querySelector('iframe');var o=f.getBoundingClientRect();" +
            "var d=f.contentDocument;var t=d.createTreeWalker(d.querySelector('p'),NodeFilter.SHOW_TEXT).nextNode();" +
            "var g=d.createRange();g.setStart(t,0);g.setEnd(t,1);var r=g.getBoundingClientRect();" +
            "return {x:o.left+r.left+r.width/2,y:o.top+r.top+r.height/2};})()",
    ).jsonObject

    /** The number of highlight marks the page draws in the amber colour, in the top document. */
    private fun markCount(controller: ReaderController): Int =
        call(controller, "document.querySelectorAll(\"#stage svg g[ref='hl-amber']\").length").jsonPrimitive.int

    /** The body's computed font size in CSS pixels, which is the reader's font size setting. */
    private fun bodyFontSize(controller: ReaderController): Int =
        call(
            controller,
            "parseInt(getComputedStyle(document.querySelector('iframe').contentDocument.body).fontSize)",
        ).jsonPrimitive.int

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
