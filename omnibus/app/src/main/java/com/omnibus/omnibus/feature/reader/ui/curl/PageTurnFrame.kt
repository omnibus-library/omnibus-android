package com.omnibus.omnibus.feature.reader.ui.curl

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * The frame around the reader's WebView. It draws the page cover and the page curl over the WebView, reports its size
 * changes to the host, and takes a horizontal swipe the host claims. Every other touch reaches the WebView unmodified.
 * A claimed swipe reaches the WebView as one cancel, and the rest of the gesture belongs to the host.
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

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var touchState = TouchState.Idle
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var velocity: VelocityTracker? = null

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

    /**
     * Watches each touch before the WebView gets it. A horizontal swipe the host claims is taken from the WebView,
     * which gets one cancel; anything else is passed on unmodified.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val host = host ?: return super.dispatchTouchEvent(ev)
        when (touchState) {
            TouchState.Dragging -> {
                track(ev)
                when (ev.actionMasked) {
                    MotionEvent.ACTION_MOVE -> host.dragTo(ev.x, ev.y)
                    MotionEvent.ACTION_UP -> {
                        host.endDrag(velocityDp())
                        touchState = TouchState.Idle
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        host.endDrag(0f)
                        touchState = TouchState.Idle
                    }
                }
                return true
            }
            TouchState.Swallowing -> {
                if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                    touchState = TouchState.Idle
                }
                return true
            }
            TouchState.Idle, TouchState.Watching -> Unit
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                downTime = ev.eventTime
                velocity?.clear()
                track(ev)
                touchState = TouchState.Watching
            }
            MotionEvent.ACTION_POINTER_DOWN -> touchState = TouchState.Idle
            MotionEvent.ACTION_MOVE -> if (touchState == TouchState.Watching) {
                track(ev)
                val dx = ev.x - downX
                val dy = ev.y - downY
                val elapsed = ev.eventTime - downTime
                when (val claim = host.claimFor(dx, dy, touchSlop, elapsed, ev.pointerCount)) {
                    is CurlClaim.Drag -> {
                        cancelChild(ev)
                        host.beginDrag(claim.dir, downX, downY)
                        host.dragTo(ev.x, ev.y)
                        touchState = TouchState.Dragging
                        return true
                    }
                    is CurlClaim.Turn -> {
                        cancelChild(ev)
                        host.tapTurn(claim.dir)
                        touchState = TouchState.Swallowing
                        return true
                    }
                    CurlClaim.Pass -> {
                        // Once the WebView has had the touch past slop or a long press, the swipe is its own for good.
                        val settled = abs(dx) >= touchSlop || elapsed >= CurlClaim.LONG_PRESS_MS || ev.pointerCount != 1
                        if (settled) touchState = TouchState.Idle
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchState = TouchState.Idle
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onDetachedFromWindow() {
        velocity?.recycle()
        velocity = null
        touchState = TouchState.Idle
        super.onDetachedFromWindow()
    }

    /** Sends the WebView a cancel for [ev], so it drops the touch as a gesture it had given up on. */
    private fun cancelChild(ev: MotionEvent) {
        val cancel = MotionEvent.obtain(ev)
        cancel.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    private fun track(ev: MotionEvent) {
        (velocity ?: VelocityTracker.obtain().also { velocity = it }).addMovement(ev)
    }

    /** The finger's horizontal velocity in dp per second, negative leftwards. */
    private fun velocityDp(): Float {
        val tracker = velocity ?: return 0f
        tracker.computeCurrentVelocity(1000)
        return tracker.xVelocity / density
    }

    /** Where a touch stands in the swipe the frame may take. */
    private enum class TouchState {
        /** No touch in progress. */
        Idle,

        /** A touch is down and may still become a claimed swipe. The WebView has every event so far. */
        Watching,

        /** The host is following a drag; the WebView gets nothing more from this touch. */
        Dragging,

        /** The host has taken a turn from this touch; the WebView gets nothing more from it. */
        Swallowing,
    }

    /** Whether the host can curl right now. Read on the main thread. */
    val curlReady: Boolean get() = host?.isReadyToCurl == true

    /** Whether a cover is up. Read on the main thread. */
    val coverShown: Boolean get() = cover != null
}
