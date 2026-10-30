package com.omnibus.omnibus.feature.reader.ui

import android.app.UiAutomation
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rotates the real device, which recreates the activity, and checks the reader keeps its place. */
class ReaderRotationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val epub = TestEpub.build()
    private val factory = ReaderAssetLoaderFactory(
        instrumentation.targetContext,
        Dispatchers.Unconfined,
        BookFileSource { BookFileResult.Opened(ByteArrayInputStream(epub), epub.size.toLong()) },
    )
    private val controller = AtomicReference<ReaderController>()
    private val relocates = mutableListOf<ReaderPosition>()

    @After
    fun restoreRotation() {
        instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }

    private fun ActivityScenario<ComponentActivity>.show() = onActivity { activity ->
        activity.setContent {
            OmnibusTheme {
                val reader = rememberReaderController()
                controller.set(reader)
                ReaderWebView(
                    bookUuid = "test-book",
                    settings = ReaderSettings(),
                    controller = reader,
                    assetLoaderFactory = factory,
                    onMessage = { message ->
                        if (message.type == "relocate") {
                            ReaderPosition.parse(message.payload)?.let { synchronized(relocates) { relocates += it } }
                        }
                    },
                )
            }
        }
    }

    private fun ActivityScenario<ComponentActivity>.webView(): WebView {
        lateinit var found: WebView
        onActivity { found = findWebView(it.window.decorView)!! }
        return found
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun ActivityScenario<ComponentActivity>.evaluate(script: String): String {
        val result = AtomicReference<String>()
        onActivity { activity -> findWebView(activity.window.decorView)!!.evaluateJavascript(script) { result.set(it) } }
        val deadline = System.currentTimeMillis() + 10_000
        while (result.get() == null && System.currentTimeMillis() < deadline) Thread.sleep(50)
        return result.get().orEmpty()
    }

    private fun ActivityScenario<ComponentActivity>.visibleParagraphs(): List<String> =
        evaluate(
            """
            (function () {
              var frame = document.querySelector('iframe');
              var stage = document.getElementById('stage').getBoundingClientRect();
              var offset = frame.getBoundingClientRect();
              var out = [];
              var paragraphs = frame.contentDocument.querySelectorAll('p');
              for (var i = 0; i < paragraphs.length; i++) {
                var r = paragraphs[i].getBoundingClientRect();
                var left = r.left + offset.left, right = r.right + offset.left;
                if (right > stage.left + 1 && left < stage.right - 1) out.push(paragraphs[i].textContent.substring(0, 24));
              }
              return out.join('|');
            })()
            """.trimIndent(),
        ).trim('"').split('|').filter { it.isNotEmpty() }

    private fun awaitReady() {
        val deadline = System.currentTimeMillis() + 30_000
        while (controller.get()?.status != ReaderLoadStatus.Ready && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertEquals(ReaderLoadStatus.Ready, controller.get()?.status)
    }

    @Test
    fun tc229_rotatingTheDeviceKeepsTheReaderOnTheSamePage() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            scenario.show()
            awaitReady()
            // Move on a few pages, so staying put and returning to the start are different outcomes.
            repeat(3) {
                val before = synchronized(relocates) { relocates.size }
                controller.get().next()
                val deadline = System.currentTimeMillis() + 15_000
                while (synchronized(relocates) { relocates.size } <= before && System.currentTimeMillis() < deadline) Thread.sleep(100)
            }
            val opening = scenario.visibleParagraphs().firstOrNull()
            assertTrue("the reader should be past the first page", opening != null && !opening.contains("paragraph 1. "))
            val savedPosition = controller.get().restoreCfi

            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_90)
            // The activity is recreated and loses its content, as an app without configChanges does.
            val deadline = System.currentTimeMillis() + 15_000
            var rotated = false
            while (!rotated && System.currentTimeMillis() < deadline) {
                scenario.onActivity { rotated = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
                Thread.sleep(200)
            }
            assertTrue("the device did not rotate", rotated)
            scenario.show()
            awaitReady()

            assertEquals("the saved position should survive recreation", savedPosition, controller.get().restoreCfi)
            // The paragraph the reader was looking at is still on the page they see.
            val deadline2 = System.currentTimeMillis() + 15_000
            var after = emptyList<String>()
            while (System.currentTimeMillis() < deadline2) {
                after = scenario.visibleParagraphs()
                if (after.isNotEmpty()) break
                Thread.sleep(200)
            }
            assertTrue("expected $opening among $after", opening in after)
        }
    }
}
