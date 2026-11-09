package com.omnibus.omnibus.feature.reader.ui.curl

import kotlin.math.sqrt

/** Largest capture, in pixels, the snapshotter draws. Keeps a page bitmap well inside the heap. */
internal const val MAX_PIXELS = 2_500_000

/** How long a snapshot waits for the WebView to draw the DOM as it is now. */
internal const val VISUAL_STATE_TIMEOUT_MS = 1_000L

/** The scale that keeps a [width] x [height] capture within [maxPixels]; never above 1. */
internal fun captureScale(width: Int, height: Int, maxPixels: Int): Float =
    if (width <= 0 || height <= 0 || width.toLong() * height <= maxPixels) 1f
    else sqrt(maxPixels.toDouble() / (width.toDouble() * height)).toFloat()
