package com.omnibus.omnibus.feature.reader.ui.curl

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.reader.ReaderTheme
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// The curl scenes of `.dev-loop/page-curl/renders/page-curl-frames-*.html`, drawn with fake pages. The ink is preview
// data only; the app's pages are WebView snapshots.

private const val SINGLE_PAGE_W = 220f
private const val SINGLE_PAGE_H = 440f
private const val SPREAD_PAGE_W = 240f
private const val SPREAD_PAGE_H = 300f

private val LIGHT_INK = 0xFF2A2622.toInt()
private val DARK_INK = 0xFFD8D3CB.toInt()

/** Forward curl past halfway, single page, Light reading theme. */
@PreviewTest
@Preview(widthDp = 220, heightDp = 440)
@Composable
fun CurlForwardLight() {
    CurlPreview(Turn.Forward, ReaderTheme.Light)
}

/** Forward curl past halfway, single page, Dark reading theme. */
@PreviewTest
@Preview(widthDp = 220, heightDp = 440)
@Composable
fun CurlForwardDark() {
    CurlPreview(Turn.Forward, ReaderTheme.Dark)
}

/** Back curl, the previous page laid over the current one, Dark reading theme. */
@PreviewTest
@Preview(widthDp = 220, heightDp = 440)
@Composable
fun CurlBackDark() {
    CurlPreview(Turn.Back, ReaderTheme.Dark)
}

/** Two-page spread, only the right page lifts about the spine, Light reading theme. */
@PreviewTest
@Preview(widthDp = 480, heightDp = 300)
@Composable
fun CurlSpreadLight() {
    CurlPreview(Turn.Spread, ReaderTheme.Light)
}

private enum class Turn { Forward, Back, Spread }

@Composable
private fun CurlPreview(turn: Turn, theme: ReaderTheme) {
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { drawPreviewCurl(it.nativeCanvas, size.width, size.height, turn, theme) }
    }
}

private fun drawPreviewCurl(canvas: android.graphics.Canvas, w: Float, h: Float, turn: Turn, theme: ReaderTheme) {
    val ground = theme.groundArgb
    val ink = if (theme.isLight) LIGHT_INK else DARK_INK
    val stage = RectF(0f, 0f, w, h)
    val bounds = CurlRect(0f, 0f, w, h)
    val spine = w / 2
    val unit = w / SINGLE_PAGE_W
    when (turn) {
        Turn.Forward -> {
            val scene = CurlScene(
                turning = bounds,
                flapBounds = bounds,
                corner = Vec(w, 0.7f * h),
                fold = Vec(0.2f * w, 0.7f * h - 44f * unit * CurlDrag.TILT_DAMPING),
            )
            val front = singleStage(w, h, unit, ground, ink, seed = 1)
            val beneath = singleStage(w, h, unit, ground, ink, seed = 5)
            drawCurl(canvas, scene, front, beneath, stage, ground)
        }
        Turn.Back -> {
            val scene = CurlScene(
                turning = bounds,
                flapBounds = bounds,
                corner = Vec(w, 0.7f * h),
                fold = Vec(-0.4f * w, 0.7f * h - 24f * unit * CurlDrag.TILT_DAMPING),
            )
            val front = singleStage(w, h, unit, ground, ink, seed = 3)
            val beneath = singleStage(w, h, unit, ground, ink, seed = 1)
            drawCurl(canvas, scene, front, beneath, stage, ground)
        }
        Turn.Spread -> {
            val spreadUnit = w / (2 * SPREAD_PAGE_W)
            val turning = CurlLayout.Spread.turning(bounds, spine)
            val scene = CurlScene(
                turning = turning,
                flapBounds = CurlLayout.Spread.flapBounds(bounds, spine),
                corner = Vec(w, 0.72f * h),
                fold = Vec(0.7f * w, 0.72f * h + (0.66f * h - 0.72f * h) * CurlDrag.TILT_DAMPING),
            )
            val front = spreadStage(w, h, spreadUnit, ground, ink, seeds = listOf(2, 4))
            val beneath = spreadStage(w, h, spreadUnit, ground, ink, seeds = listOf(2, 7))
            drawCurl(canvas, scene, front, beneath, stage, ground)
            drawSpineBand(canvas, spine, h, spreadUnit)
        }
    }
}

/** A single page snapshot filling [w] by [h] px, with one fake page of body text. */
private fun singleStage(w: Float, h: Float, unit: Float, ground: Int, ink: Int, seed: Int): Bitmap =
    fakeStage(w, h, unit, ground, ink, pages = listOf(seed), pageW = SINGLE_PAGE_W, pageH = SINGLE_PAGE_H)

/** A two-page snapshot: the left page is [seeds] first, the right page second. */
private fun spreadStage(w: Float, h: Float, unit: Float, ground: Int, ink: Int, seeds: List<Int>): Bitmap =
    fakeStage(w, h, unit, ground, ink, pages = seeds, pageW = SPREAD_PAGE_W, pageH = SPREAD_PAGE_H)

private fun fakeStage(
    w: Float,
    h: Float,
    unit: Float,
    ground: Int,
    ink: Int,
    pages: List<Int>,
    pageW: Float,
    pageH: Float,
): Bitmap {
    val bitmap = Bitmap.createBitmap(w.roundToInt(), h.roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(ground)
    pages.forEachIndexed { i, seed ->
        canvas.save()
        canvas.translate(i * pageW * unit, 0f)
        canvas.scale(unit, unit)
        drawBodyText(canvas, pageW, pageH, ink, seed)
        canvas.restore()
    }
    return bitmap
}

/** One page of body text in page units, as the renders' `bodyInk` and `bars` lay it out. */
private fun drawBodyText(canvas: android.graphics.Canvas, w: Float, h: Float, ink: Int, seed: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun bar(x: Float, y: Float, width: Float, height: Float, alpha: Float) {
        paint.color = (ink and 0x00FFFFFF) or ((alpha * 255).roundToInt() shl 24)
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), height / 2, height / 2, paint)
    }
    bar(20f, 16f, w * 0.26f, 2.6f, 0.3f)
    bar(w / 2 - 7, h - 22, 14f, 2.6f, 0.3f)
    var y = 44f
    var line = 0
    while (y < h - 38) {
        val k = line % 8
        var x = 20f
        var width = w - 40
        if (k == 0 && line > 0) {
            x += 10
            width -= 10
        }
        width *= if (k == 7) 0.3f + 0.45f * fakeRandom(seed + line) else 0.92f + 0.08f * fakeRandom(seed * 3 + line)
        bar(x, y, width, 3.4f, 0.5f)
        y += 11
        line++
        if (k == 7) y += 6
    }
}

/** The spine's shadow band across the middle of a spread, drawn over the curl as the renders do. */
private fun drawSpineBand(canvas: android.graphics.Canvas, spine: Float, h: Float, unit: Float) {
    val left = spine - 14 * unit
    val paint = Paint().apply {
        shader = LinearGradient(
            left, 0f, left + 28 * unit, 0f,
            intArrayOf(0x00000000, 0x29000000, 0x00000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(left, 0f, left + 28 * unit, h, paint)
}

/** A deterministic pseudo-random value in [0, 1), the renders' `rnd`. */
private fun fakeRandom(i: Int): Float {
    val x = sin(i * 127.1 + 311.7) * 43758.5453
    return (x - floor(x)).toFloat()
}
