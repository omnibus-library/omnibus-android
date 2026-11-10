package com.omnibus.omnibus.feature.reader.ui.curl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.floor
import kotlin.math.max

/**
 * Draws the WebView itself rather than reading the window, so the cover over the window never lands in a snapshot.
 * Must be used on the main thread.
 */
internal class WebViewSnapshotter(
    private val webView: WebView,
    private val maxPixels: Int = MAX_PIXELS,
) : PageSnapshotter {
    private var requestId = 0L

    override suspend fun awaitDrawn(): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.VISUAL_STATE_CALLBACK)) return true
        return withTimeoutOrNull(VISUAL_STATE_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                WebViewCompat.postVisualStateCallback(webView, ++requestId) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        } != null
    }

    override suspend fun capture(): PageImage? {
        // A timed-out wait still captures what is drawn now; the host recaptures if the page moved.
        awaitDrawn()
        val width = webView.width
        val height = webView.height
        if (width == 0 || height == 0) return null
        val scale = captureScale(width, height, maxPixels)
        return try {
            val bitmap = Bitmap.createBitmap(
                max(1, floor(width * scale).toInt()),
                max(1, floor(height * scale).toInt()),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            canvas.translate(-webView.scrollX.toFloat(), -webView.scrollY.toFloat())
            webView.draw(canvas)
            BitmapPageImage(bitmap)
        } catch (_: OutOfMemoryError) {
            null
        }
    }
}
