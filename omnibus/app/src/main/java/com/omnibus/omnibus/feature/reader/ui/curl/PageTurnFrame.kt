package com.omnibus.omnibus.feature.reader.ui.curl

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.FrameLayout

/**
 * The frame around the reader's WebView. It draws the page cover and the page curl over the WebView, and reports
 * its size changes to the host. Touches still reach the WebView unmodified.
 *
 * The probes at the bottom are for instrumented tests; they read the host and the drawn state on the main thread.
 */
internal class PageTurnFrame(context: Context) : FrameLayout(context), CurlStage {
    /** The theme's ground, which the curl's flap is made of. */
    var paper: Int = 0

    /** The host that drives this frame. Set by the reader host before the first touch. */
    var host: PageCurlHost? = null

    /** Called when the frame changes size after it has been laid out once. */
    var onSize: (() -> Unit)? = null

    private var cover: BitmapPageImage? = null
    private var curl: CurlPicture? = null
    private val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Whether the cover has been shown at any point, so a test can tell an animated turn from a plain landing. */
    var coverEverShown = false
        private set

    /** How many curls have started. */
    var curlsPlayed = 0
        private set

    override val widthPx: Int get() = width
    override val heightPx: Int get() = height
    override val density: Float = context.resources.displayMetrics.density

    override fun showCover(image: PageImage?) {
        cover = image as BitmapPageImage?
        if (image != null) coverEverShown = true
        invalidate()
    }

    override fun showCurl(picture: CurlPicture?) {
        if (curl == null && picture != null) curlsPlayed++
        curl = picture
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val stageRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val picture = curl
        if (picture != null) {
            val front = (picture.front as? BitmapPageImage)?.bitmap
            val beneath = (picture.beneath as? BitmapPageImage)?.bitmap
            // A snapshot recycled under the curl is skipped, never drawn.
            if (front != null && beneath != null && !front.isRecycled && !beneath.isRecycled) {
                drawCurl(canvas, picture.scene, front, beneath, stageRect, paper)
            }
            return
        }
        cover?.bitmap?.takeUnless { it.isRecycled }?.let { canvas.drawBitmap(it, null, stageRect, filterPaint) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw != 0 && oldh != 0) onSize?.invoke()
    }

    /** Whether the host can curl right now. Read on the main thread. */
    val curlReady: Boolean get() = host?.isReadyToCurl == true

    /** Whether a cover is up. Read on the main thread. */
    val coverShown: Boolean get() = cover != null
}
