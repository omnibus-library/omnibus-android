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
import org.junit.Rule
import org.junit.Test

// TC-336, TC-337: the curl on the reader page, driven through the page turn frame. Touches are fractions of the frame,
// so the test does not depend on the window size.
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
