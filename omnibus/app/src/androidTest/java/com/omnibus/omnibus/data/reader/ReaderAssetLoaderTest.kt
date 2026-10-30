package com.omnibus.omnibus.data.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReaderAssetLoaderTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val consoleErrors = mutableListOf<String>()
    private val bookFailures = mutableListOf<BookFileResult.Failure>()
    private val requestedBooks = mutableListOf<String>()
    private var bookResult: BookFileResult = BookFileResult.Opened(ByteArrayInputStream("epub-bytes".toByteArray()), 10)
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    @Before
    fun loadReaderPage() {
        val source = BookFileSource { uuid ->
            requestedBooks += uuid
            bookResult
        }
        val loader = ReaderAssetLoaderFactory(context, Dispatchers.Unconfined, source)
            .create { synchronized(bookFailures) { bookFailures += it } }
        val pageFinished = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            consoleErrors += message.message()
                        }
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

                    override fun onPageFinished(view: WebView, url: String) {
                        pageFinished.countDown()
                    }

                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = Unit
                }
                loadUrl(ReaderAssets.ENTRY_URL)
            }
        }
        assertTrue("reader.html never finished loading", pageFinished.await(15, TimeUnit.SECONDS))
    }

    @After
    fun destroyWebView() {
        instrumentation.runOnMainSync { webView.destroy() }
    }

    @Test
    fun tc214_readerPageLoadsItsScriptsAndDefinesOmnibusReader() {
        assertEquals("\"function\"", evaluate("typeof window.OmnibusReader.init"))
        assertEquals("\"function\"", evaluate("typeof window.JSZip"))
        assertEquals("\"function\"", evaluate("typeof window.ePub"))
        assertEquals(emptyList<String>(), consoleErrors)
    }

    @Test
    fun tc215_stageIsInsetByTheInsetVariablesAndFallsBackToZero() {
        // Unset, the variables contribute nothing and only the reserved bands remain.
        assertEquals("\"58px\"", evaluate("getComputedStyle(document.getElementById('stage')).top"))
        assertEquals("\"10px\"", evaluate("getComputedStyle(document.getElementById('stage')).left"))

        evaluate(
            "document.documentElement.style.setProperty('--inset-top', '24px');" +
                "document.documentElement.style.setProperty('--inset-left', '8px')",
        )

        assertEquals("\"82px\"", evaluate("getComputedStyle(document.getElementById('stage')).top"))
        assertEquals("\"18px\"", evaluate("getComputedStyle(document.getElementById('stage')).left"))
    }

    @Test
    fun tc216_anUnknownReaderFileIsAnswered404NotLeftToTheNetwork() {
        val status = evaluate(
            "fetch('/reader/missing.js').then(function (r) { window.__status = r.status; })" +
                ".catch(function () { window.__status = -1; }); 0",
        )
        assertEquals("0", status)
        waitFor("window.__status !== undefined")
        assertEquals("404", evaluate("String(window.__status)").trim('"'))
    }

    @Test
    fun tc217_bookPathServesTheBookBytesAsEpub() {
        val body = fetchStatusAndBody("/book/abc-123.epub")

        assertEquals("200:epub-bytes", body)
        assertEquals(listOf("abc-123"), requestedBooks)
    }

    @Test
    fun tc218_aBookThatFailsToOpenIsAnErrorStatusAndIsReportedToTheHost() {
        bookResult = BookFileResult.NotFound

        assertEquals("404:", fetchStatusAndBody("/book/abc-123.epub"))
        assertEquals(listOf<BookFileResult.Failure>(BookFileResult.NotFound), synchronized(bookFailures) { bookFailures.toList() })
    }

    @Test
    fun tc219_bookPathRefusesAnythingThatIsNotAUuidDotEpub() {
        assertEquals("404:", fetchStatusAndBody("/book/..%2Fsecret"))
        assertEquals(emptyList<String>(), requestedBooks)
    }

    private fun fetchStatusAndBody(path: String): String {
        evaluate(
            "window.__result = undefined;" +
                "fetch('$path').then(function (r) { return r.text().then(function (t) {" +
                "window.__result = r.status + ':' + t; }); })" +
                ".catch(function () { window.__result = 'failed'; }); 0",
        )
        waitFor("window.__result !== undefined")
        return evaluate("window.__result").trim('"')
    }

    private fun evaluate(script: String): String {
        val result = arrayOfNulls<String>(1)
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(script) {
                result[0] = it
                done.countDown()
            }
        }
        assertTrue("script did not return: $script", done.await(10, TimeUnit.SECONDS))
        return result[0].orEmpty()
    }

    private fun waitFor(condition: String) {
        repeat(50) {
            if (evaluate(condition) == "true") return
            Thread.sleep(100)
        }
        error("Timed out waiting for $condition")
    }
}
