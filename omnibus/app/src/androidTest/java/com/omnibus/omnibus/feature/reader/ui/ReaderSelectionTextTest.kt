package com.omnibus.omnibus.feature.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The selection text the page's glue reports on the device's WebView, driven through the controller's page calls. */
class ReaderSelectionTextTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val messages = mutableListOf<ReaderEvent>()

    @Test
    fun issue54_tc17_hiddenMarkupNeverReachesTheSelectedText() {
        val epub = TestEpub.build(hiddenMarkup = true)
        val controller = ReaderController()
        showReader(controller, epub, bookUuid = "selection-hidden-markup")
        awaitReady(controller)
        awaitMovementCount(1)

        val start = pointOn(controller, "omn-start")
        call(controller, "OmnibusReader.beginSelectionAt(${start.number("x")}, ${start.number("y")})")
        rule.waitUntil(timeoutMillis = 15_000) { selections().isNotEmpty() }
        assertEquals("a press on a word welded to hidden markup should select only the visible word", "world", selections().first().text)

        val end = pointOn(controller, "omn-end")
        rule.runOnUiThread { controller.beginEdgeDrag(SelectionEdge.End) }
        rule.runOnUiThread {
            controller.dragEdge(end.number("right").toFloat(), end.number("y").toFloat(), end.number("right").toFloat())
        }
        val beforeSettle = synchronized(messages) { messages.size }
        rule.runOnUiThread { controller.endEdgeDrag() }
        rule.waitUntil(timeoutMillis = 15_000) {
            synchronized(messages) { messages.drop(beforeSettle).any { it is ReaderEvent.Selection && !it.data.dragging } }
        }

        val settled = synchronized(messages) {
            messages.drop(beforeSettle).filterIsInstance<ReaderEvent.Selection>().last { !it.data.dragging }.data
        }
        assertTrue("a settled selection should start on the visible word, not ${settled.text}", settled.text.startsWith("world"))
        assertTrue("the selection should run on to the end handle's text", settled.text.contains("Omega"))
        listOf("PAGEMARK", "HIDDENBLOCK", "color").forEach { hidden ->
            assertFalse("the selected text should not contain $hidden", settled.text.contains(hidden))
        }
    }

    @Test
    fun issue54_tc18_aPressOnTheBlankPageIsNotASelection() {
        val epub = TestEpub.build(chapters = 1, paragraphsPerChapter = 2)
        val controller = ReaderController()
        showReader(controller, epub, bookUuid = "selection-blank-page")
        awaitReady(controller)
        awaitMovementCount(1)

        assertEquals(
            JsonPrimitive(false),
            call(controller, "OmnibusReader.beginSelectionAt(innerWidth / 2, innerHeight * 0.85)"),
        )
        // The reply to this call is ordered after anything the press emitted, so the check below is not an absence by timeout.
        assertEquals(JsonPrimitive(1), call(controller, "1"))
        assertTrue(
            "a press on the blank page should emit no selection",
            synchronized(messages) { messages.none { it is ReaderEvent.Selection } },
        )
    }

    private fun showReader(controller: ReaderController, epub: ByteArray, bookUuid: String) {
        val assetLoaderFactory = ReaderAssetLoaderFactory(
            context,
            Dispatchers.Unconfined,
            BookFileSource { BookFileResult.Opened(ByteArrayInputStream(epub), epub.size.toLong()) },
        )
        rule.setContent {
            OmnibusTheme {
                Box(Modifier.fillMaxSize()) {
                    ReaderWebView(
                        bookUuid = bookUuid,
                        settings = ReaderSettings(),
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

    private fun awaitMovementCount(count: Int) {
        rule.waitUntil(timeoutMillis = 15_000) {
            synchronized(messages) {
                messages.count { it is ReaderEvent.Relocate && it.data.isMovement } >= count
            }
        }
    }

    private fun selections(): List<SelectionData> = synchronized(messages) {
        messages.filterIsInstance<ReaderEvent.Selection>().map { it.data }
    }

    /** The host-window point at the middle of the element [id] on the chapter, with the right edge of its box. */
    private fun pointOn(controller: ReaderController, id: String): JsonObject = call(
        controller,
        "(function(){var f=document.querySelector('iframe');var o=f.getBoundingClientRect();" +
            "var r=f.contentDocument.getElementById('$id').getBoundingClientRect();" +
            "return {x:o.left+r.left+r.width/2,y:o.top+r.top+r.height/2,right:o.left+r.right-1};})()",
    ).jsonObject

    private fun JsonObject.number(key: String): Double = getValue(key).jsonPrimitive.double

    /** Awaits [expr] on the page. [ReaderController.call] must run on the main thread, where its replies arrive. */
    private fun call(controller: ReaderController, expr: String): JsonElement =
        runBlocking(Dispatchers.Main) { controller.call(expr) } ?: error("the page did not answer: $expr")
}
