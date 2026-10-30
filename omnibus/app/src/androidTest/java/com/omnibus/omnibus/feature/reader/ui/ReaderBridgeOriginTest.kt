package com.omnibus.omnibus.feature.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderAssets
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Checks that only the bundled reader page can reach `window.omnibus`. A page on any other origin,
 * or a sub-frame on the reader origin, must not get its messages into `onEvent`.
 */
class ReaderBridgeOriginTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ReaderEvent>()
    private var host: ReaderWebHost? = null

    @After
    fun release() {
        rule.runOnUiThread { host?.onForgotten() }
    }

    /** Builds a host on the UI thread and shows its WebView, without loading `reader.html`. */
    private fun showHost() {
        rule.runOnUiThread {
            val activity = rule.activity
            val factory = ReaderAssetLoaderFactory(
                activity,
                Dispatchers.Unconfined,
                BookFileSource { BookFileResult.NotFound },
            )
            val created = ReaderWebHost(
                context = activity,
                controller = ReaderController(),
                assetLoaderFactory = factory,
                initialTheme = ReaderTheme.Dark,
                initialInsets = PageInsets.None,
                onEvent = { synchronized(events) { events += it } },
                onRebuild = {},
            )
            host = created
            activity.setContentView(created.webView)
        }
    }

    private fun load(base: String, html: String) {
        rule.runOnUiThread { host!!.webView.loadDataWithBaseURL(base, html, "text/html", "utf-8", null) }
    }

    private fun eventCount(): Int = synchronized(events) { events.size }

    private fun hasEvent(event: ReaderEvent): Boolean = synchronized(events) { events.contains(event) }

    /** Runs [script] in the page and returns its JSON result, or null if it hasn't answered in time. */
    private fun evaluate(script: String): String? {
        val result = arrayOfNulls<String>(1)
        rule.runOnUiThread { host!!.webView.evaluateJavascript(script) { result[0] = it } }
        val deadline = System.currentTimeMillis() + 10_000
        while (result[0] == null && System.currentTimeMillis() < deadline) Thread.sleep(50)
        return result[0]
    }

    /** Polls [condition] until it holds or the timeout passes. */
    private fun awaitCondition(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(50)
    }

    private fun awaitPageComplete() {
        awaitCondition(15_000) { evaluate("document.readyState") == "\"complete\"" }
        assertEquals("\"complete\"", evaluate("document.readyState"))
    }

    /** A page that posts `hostReady` through the bridge from its main frame, as `reader.html` does. */
    private val postingPage =
        """
        <!doctype html><html><body>
        <script>
          window.omnibus && window.omnibus.postMessage(JSON.stringify({type: "hostReady", payload: null}));
        </script>
        </body></html>
        """.trimIndent()

    // TC-24: a page on a foreign origin has no bridge, so its posts never reach onEvent.
    @Test
    fun tc24_aForeignOriginCannotReachOnEvent() {
        showHost()
        load("https://other.example/", postingPage)
        awaitPageComplete()

        assertEquals("\"undefined\"", evaluate("typeof window.omnibus"))
        Thread.sleep(1_000)
        assertEquals("no event should arrive from a foreign origin", 0, eventCount())
    }

    // TC-25: the control for TC-24, the same page on the asset origin, does reach onEvent.
    @Test
    fun tc25_theAssetOriginReachesOnEvent() {
        showHost()
        load("${ReaderAssets.ORIGIN}/origin-test.html", postingPage)

        awaitCondition(15_000) { hasEvent(ReaderEvent.HostReady) }
        assertTrue("the asset origin's hostReady should reach onEvent", hasEvent(ReaderEvent.HostReady))
    }

    // TC-26: a sub-frame on the asset origin is rejected, because only the main frame is trusted.
    @Test
    fun tc26_aSubFramePostIsRejected() {
        showHost()
        // The sub-frame posts toggleChrome. The main frame posts hostReady from the frame's onload,
        // which proves the bridge works on the page, so only the sub-frame's post can be missing.
        val page =
            """
            <!doctype html><html><body>
            <iframe srcdoc='<script>window.omnibus && window.omnibus.postMessage(JSON.stringify({type: "toggleChrome", payload: null}));</script>'
                    onload='window.omnibus && window.omnibus.postMessage(JSON.stringify({type: "hostReady", payload: null}));'></iframe>
            </body></html>
            """.trimIndent()
        load("${ReaderAssets.ORIGIN}/origin-test.html", page)

        awaitCondition(15_000) { hasEvent(ReaderEvent.HostReady) }
        assertTrue("the main frame's hostReady should reach onEvent", hasEvent(ReaderEvent.HostReady))
        Thread.sleep(1_000)
        assertTrue(
            "a sub-frame's toggleChrome must not reach onEvent: ${synchronized(events) { events.toList() }}",
            !hasEvent(ReaderEvent.ToggleChrome),
        )
    }
}
