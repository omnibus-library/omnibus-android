package com.omnibus.omnibus.feature.reader.ui.curl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.core.graphics.withClip
import androidx.core.graphics.withMatrix
import androidx.core.graphics.withTranslation

/**
 * Draws the curl of [scene] over [stage], as `curl.html` draws it. [front] is the page that lifts and [beneath] the one
 * it reveals, both snapshots of the whole stage. [paper] is the theme's ground, which the flap is made of.
 */
internal fun drawCurl(canvas: Canvas, scene: CurlScene, front: Bitmap, beneath: Bitmap, stage: RectF, paper: Int) {
    val g = CurlGeometry.of(scene)
    val u = scene.turning.width / 220f
    val n = g.normal
    val m = g.mid
    canvas.drawBitmap(front, null, stage, bitmapPaint)
    if (g.isFlat) return
    canvas.withClip(scene.turning.toRectF()) { drawBitmap(beneath, null, stage, bitmapPaint) }
    if (g.isTurned) return

    val farPath = g.far.toPath()
    shadowPaint.shader = LinearGradient(
        m.x, m.y, m.x + n.x * 46 * u, m.y + n.y * 46 * u,
        intArrayOf(0x61000000, 0x24000000, 0x00000000), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP,
    )
    canvas.drawPath(farPath, shadowPaint)

    val keptPath = g.kept.toPath()
    canvas.withClip(keptPath) { drawBitmap(front, null, stage, bitmapPaint) }

    val flapPath = g.flap.toPath()
    val reflection = Matrix().apply { setValues(floatArrayOf(g.a, g.b, g.e, g.b, g.d, g.f, 0f, 0f, 1f)) }
    canvas.withClip(keptPath) {
        val sx = -n.x * 5 * u
        val sy = (-n.y * 5 + 1) * u
        if (Build.VERSION.SDK_INT >= 28) {
            flapShadowPaint.color = paper
            flapShadowPaint.setShadowLayer(6 * u, sx, sy, 0x66000000)
            drawPath(flapPath, flapShadowPaint)
        } else {
            withTranslation(sx, sy) { drawPath(flapPath, flatShadowPaint) }
        }
        withClip(scene.flapBounds.toRectF()) {
            // The far side is clipped in the reflected space, so the flap lands on the kept side of the fold.
            withMatrix(reflection) {
                withClip(farPath) {
                    drawColor(paper)
                    drawBitmap(front, null, stage, showThroughPaint)
                    cylinderPaint.shader = LinearGradient(
                        m.x, m.y, m.x + n.x * 52 * u, m.y + n.y * 52 * u,
                        intArrayOf(0x57000000, 0x4DFFFFFF, 0x00FFFFFF, 0x12000000),
                        floatArrayOf(0f, 0.16f, 0.45f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                    drawPaint(cylinderPaint)
                }
            }
        }
    }
}

private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
private val showThroughPaint = Paint(bitmapPaint).apply { alpha = 36 }
private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
private val cylinderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
private val flapShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
private val flatShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }

private fun List<Vec>.toPath(): Path = Path().apply {
    forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    if (isNotEmpty()) close()
}

private fun CurlRect.toRectF() = RectF(left, top, right, bottom)
