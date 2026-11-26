package com.omnibus.omnibus.feature.reader.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

/**
 * The font size setting scales the book's text whatever relative sizing its own CSS uses. Paragraphs
 * the publisher pins to an absolute size (`p{font-size:14px}`) are not covered: they don't follow yet.
 */
class ReaderPublisherFontSizeTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun unstyledTextFollowsTheSetting() = assertParagraphFollowsTheSetting(stylesheet = "")

    @Test
    fun emSizedTextFollowsTheSetting() = assertParagraphFollowsTheSetting("p{font-size:0.9em}")

    @Test
    fun percentSizedTextFollowsTheSetting() = assertParagraphFollowsTheSetting("p{font-size:100%}")

    @Test
    fun textFollowsTheSettingOverAPixelSizedRoot() = assertParagraphFollowsTheSetting("html{font-size:14px}")

    @Test
    fun importantEmSizedTextFollowsTheSetting() = assertParagraphFollowsTheSetting("p{font-size:1em !important}")

    @Test
    fun remSizedTextFollowsTheSetting() = assertParagraphFollowsTheSetting("p{font-size:1rem}")

    @Test
    fun textFollowsTheSettingOverAnImportantBodySize() =
        assertParagraphFollowsTheSetting("body{font-size:12pt !important}")

    @Test
    fun remSizedTextOpensAtTheStoredSize() {
        showReader("p{font-size:1rem}", mutableStateOf(ReaderSettings(fontSize = LARGE)))

        awaitParagraphSize("the first paint") { abs(it - LARGE) <= TOLERANCE * LARGE }
    }

    @Test
    fun textSizedFromAShrunkRootOpensAtTheStoredSize() {
        showReader(SHRUNK_ROOT, mutableStateOf(ReaderSettings(fontSize = SMALL)))

        awaitParagraphSize("the first paint") { abs(it - SMALL) <= TOLERANCE * SMALL }
    }

    @Test
    fun textSizedFromAShrunkRootChangesToTheSetting() {
        val settings = mutableStateOf(ReaderSettings(fontSize = SMALL))
        showReader(SHRUNK_ROOT, settings)

        rule.runOnIdle { settings.value = settings.value.copy(fontSize = LARGE) }

        awaitParagraphSize("the size after $SMALL -> $LARGE") { abs(it - LARGE) <= TOLERANCE * LARGE }
    }

    /** Opens a book with [stylesheet] at [SMALL], changes the setting to [LARGE], and expects the text to scale by as much. */
    private fun assertParagraphFollowsTheSetting(stylesheet: String) {
        val settings = mutableStateOf(ReaderSettings(fontSize = SMALL))
        showReader(stylesheet, settings)
        val before = paragraphSize()

        rule.runOnIdle { settings.value = settings.value.copy(fontSize = LARGE) }

        val expected = LARGE.toDouble() / SMALL
        awaitParagraphSize("the size after $SMALL -> $LARGE from $before") { abs(it / before - expected) <= TOLERANCE * expected }
    }

    private fun showReader(stylesheet: String, settings: MutableState<ReaderSettings>) {
        val epub = TestEpub.build(chapters = 2, stylesheet = stylesheet)
        val assetLoaderFactory = ReaderAssetLoaderFactory(
            context,
            Dispatchers.Unconfined,
            BookFileSource { BookFileResult.Opened(ByteArrayInputStream(epub), epub.size.toLong()) },
        )
        val controller = ReaderController()
        rule.setContent {
            OmnibusTheme {
                Box(Modifier.fillMaxSize()) {
                    ReaderWebView(
                        bookUuid = "publisher-font-size",
                        settings = settings.value,
                        controller = controller,
                        assetLoaderFactory = assetLoaderFactory,
                        onEvent = {},
                    )
                }
            }
        }
        rule.waitUntil(timeoutMillis = 30_000) { controller.status == ReaderLoadStatus.Ready }
    }

    /** The first paragraph's computed font size in px. */
    private fun paragraphSize(): Double = evaluate(
        "String(parseFloat(getComputedStyle(document.querySelector('iframe').contentDocument.querySelector('body p')).fontSize))",
    ).trim('"').toDouble()

    /** Waits until the paragraph size satisfies [accept], and says what it was instead if it never does. */
    private fun awaitParagraphSize(what: String, accept: (Double) -> Boolean) {
        try {
            rule.waitUntil(timeoutMillis = 10_000) { accept(paragraphSize()) }
        } catch (e: Throwable) {
            throw AssertionError("$what was ${paragraphSize()}", e)
        }
    }

    private fun findWebView(view: View = rule.activity.window.decorView): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun evaluate(script: String): String {
        val result = arrayOfNulls<String>(1)
        rule.runOnUiThread { findWebView()?.evaluateJavascript(script) { result[0] = it } }
        val deadline = System.currentTimeMillis() + 10_000
        while (result[0] == null && System.currentTimeMillis() < deadline) Thread.sleep(50)
        return result[0].orEmpty()
    }

    private companion object {
        const val SMALL = 16
        const val LARGE = 28
        const val TOLERANCE = 0.02

        /** The common 10px-root pattern: body text is 1.6rem, the default 16px. */
        const val SHRUNK_ROOT = "html{font-size:62.5%} p{font-size:1.6rem}"
    }
}
