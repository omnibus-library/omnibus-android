package com.omnibus.omnibus.feature.reader.ui.curl

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.feature.reader.ui.ReaderController
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.ReaderLoadStatus
import com.omnibus.omnibus.feature.reader.ui.ReaderScripts
import com.omnibus.omnibus.feature.reader.ui.ReaderWebView
import com.omnibus.omnibus.feature.reader.ui.TestEpub
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WebViewSnapshotterTest {

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
    fun tc323_aSnapshotOfTheNextPageIsNotTheSnapshotOfThePageInFront() {
        val controller = bootedReader()
        val (front, next) = captureFrontAndNext(controller)

        try {
            assertFalse("the next page should draw differently", next.sameAs(front))
        } finally {
            front.recycle()
            next.recycle()
        }
    }

    @Test
    fun tc323_neitherSnapshotIsASingleFlatColour() {
        val controller = bootedReader()
        val (front, next) = captureFrontAndNext(controller)

        try {
            assertTrue("the page in front should show content", front.distinctSamples() > 1)
            assertTrue("the next page should show content", next.distinctSamples() > 1)
        } finally {
            front.recycle()
            next.recycle()
        }
    }

    @Test
    fun tc323_eachSnapshotFitsTheBudget() {
        val controller = bootedReader()
        val (front, next) = captureFrontAndNext(controller)

        try {
            assertTrue("the page in front is ${front.width}x${front.height}", front.pixelCount() <= MAX_PIXELS)
            assertTrue("the next page is ${next.width}x${next.height}", next.pixelCount() <= MAX_PIXELS)
        } finally {
            front.recycle()
            next.recycle()
        }
    }

    @Test
    fun tc323_takingSnapshotsDoesNotMoveThePage() {
        val controller = bootedReader()
        val snapshotter = onMain { WebViewSnapshotter(checkNotNull(findWebView())) }
        val front = onMain { snapshotter.capture() }
        val movementsBefore = movements().size

        val next = onMain {
            assertEquals(JsonPrimitive(true), controller.call(ReaderScripts.peek(1)))
            snapshotter.capture()
        }
        onMain { assertEquals(JsonPrimitive(true), controller.call(ReaderScripts.peek(0))) }
        // A relocate from the peeks would arrive on the main thread soon after; this window is the check's only bound.
        Thread.sleep(1_000)

        assertEquals(movementsBefore, movements().size)
        front?.recycle()
        next?.recycle()
    }

    /** Boots the fixture book and waits until the page has moved once, so the page is showing content. */
    private fun bootedReader(): ReaderController {
        val controller = ReaderController()
        showReader(controller)
        awaitReady(controller)
        awaitMovementCount(1)
        return controller
    }

    /**
     * Captures the page in front, peeks one page forward and captures that, then peeks back so the page in front is
     * showing again. The returned bitmaps belong to the caller.
     */
    private fun captureFrontAndNext(controller: ReaderController): Pair<Bitmap, Bitmap> = onMain {
        val snapshotter = WebViewSnapshotter(checkNotNull(findWebView()))
        val front = checkNotNull(snapshotter.capture()) as BitmapPageImage
        assertEquals(JsonPrimitive(true), controller.call(ReaderScripts.peek(1)))
        val next = checkNotNull(snapshotter.capture()) as BitmapPageImage
        assertEquals(JsonPrimitive(true), controller.call(ReaderScripts.peek(0)))
        front.bitmap to next.bitmap
    }

    /** Runs [block] on the main thread and waits for it, as the snapshotter and the page calls require. */
    private fun <T> onMain(block: suspend () -> T): T = runBlocking(Dispatchers.Main) { block() }

    /** Shows the reader filling the window, with the same wiring the reader screen uses. */
    private fun showReader(controller: ReaderController, settings: ReaderSettings = ReaderSettings()) {
        val state = mutableStateOf(settings)
        rule.setContent {
            OmnibusTheme {
                Box(Modifier.fillMaxSize().testTag(READER)) {
                    ReaderWebView(
                        bookUuid = "test-book",
                        settings = state.value,
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

    private fun findWebView(view: View = rule.activity.window.decorView): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    /** How many distinct colours a 16x16 grid of sample points over the bitmap takes. */
    private fun Bitmap.distinctSamples(): Int {
        val colours = mutableSetOf<Int>()
        for (row in 0 until SAMPLES) {
            for (column in 0 until SAMPLES) {
                val x = ((column + 0.5) * width / SAMPLES).toInt()
                val y = ((row + 0.5) * height / SAMPLES).toInt()
                colours += getPixel(x, y)
            }
        }
        return colours.size
    }

    private fun Bitmap.pixelCount(): Long = width.toLong() * height

    private companion object {
        const val READER = "reader"
        const val SAMPLES = 16
    }
}
