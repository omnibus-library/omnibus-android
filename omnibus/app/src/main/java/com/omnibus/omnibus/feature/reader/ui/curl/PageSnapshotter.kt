package com.omnibus.omnibus.feature.reader.ui.curl

import android.graphics.Bitmap

/** A snapshot of the page. The interface is Android-free so the host can be tested on the JVM. */
internal interface PageImage {
    fun recycle()
}

internal class BitmapPageImage(val bitmap: Bitmap) : PageImage {
    override fun recycle() = bitmap.recycle()
}

internal interface PageSnapshotter {
    /** What the page shows now, or null when it can't be drawn (detached, zero size). */
    suspend fun capture(): PageImage?

    /** Waits until the WebView has drawn the DOM as it is now; false after [VISUAL_STATE_TIMEOUT_MS]. */
    suspend fun awaitDrawn(): Boolean
}
