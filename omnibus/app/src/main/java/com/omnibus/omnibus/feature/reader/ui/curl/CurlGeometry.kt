package com.omnibus.omnibus.feature.reader.ui.curl

import kotlin.math.hypot

/** A point in stage pixels. */
internal data class Vec(val x: Float, val y: Float)

/** An axis-aligned rectangle in stage pixels. */
internal data class CurlRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** The corners, clockwise from the top left, as `curl.html` lists them. */
    fun corners(): List<Vec> = listOf(Vec(left, top), Vec(right, top), Vec(right, bottom), Vec(left, bottom))
}

/**
 * One frame of a curl. [turning] is the page that lifts and [flapBounds] is where its flap may show. [corner] is the
 * lifted corner C and [fold] is the fold point F that the finger drives.
 */
internal data class CurlScene(val turning: CurlRect, val flapBounds: CurlRect, val corner: Vec, val fold: Vec)

/**
 * The fold of a curl: the perpendicular bisector of C and F. The polygons are the parts of [turning] on each side of it,
 * and [reflect] maps a point across it, which is how the flap is drawn.
 */
internal class CurlGeometry private constructor(
    val normal: Vec,
    val mid: Vec,
    /** The side away from C: the part of the page that is still lying flat. */
    val kept: List<Vec>,
    /** The side of C: the part that has lifted. */
    val far: List<Vec>,
    /** The lifted part reflected across the fold and clipped to the flap bounds. */
    val flap: List<Vec>,
    /** The reflection of [reflect] as affine terms: x' = a·x + b·y + e and y' = b·x + d·y + f. */
    val a: Float,
    val b: Float,
    val d: Float,
    val e: Float,
    val f: Float,
    /** Whether C and F are under a pixel apart, so the page draws flat. */
    val isFlat: Boolean,
) {
    /** Nothing of the turning page is left lying: only the page beneath shows. */
    val isTurned: Boolean get() = !isFlat && kept.size < 3

    /** [p] reflected across the fold. */
    fun reflect(p: Vec) = Vec(a * p.x + b * p.y + e, b * p.x + d * p.y + f)

    companion object {
        /** The geometry for [scene]; a fold under a pixel from its corner is flat. */
        fun of(scene: CurlScene): CurlGeometry {
            val c = scene.corner
            val fp = scene.fold
            val dx = c.x - fp.x
            val dy = c.y - fp.y
            val raw = hypot(dx, dy)
            val len = if (raw == 0f) 1f else raw
            val n = Vec(dx / len, dy / len)
            val m = Vec((c.x + fp.x) / 2, (c.y + fp.y) / 2)
            val rect = scene.turning.corners()
            val a = 1 - 2 * n.x * n.x
            val b = -2 * n.x * n.y
            val d = 1 - 2 * n.y * n.y
            val e = m.x - (a * m.x + b * m.y)
            val f = m.y - (b * m.x + d * m.y)
            if (raw < 1f) return CurlGeometry(n, m, rect, emptyList(), emptyList(), a, b, d, e, f, isFlat = true)
            val kept = clipHalf(rect, m, Vec(-n.x, -n.y))
            val far = clipHalf(rect, m, n)
            val reflected = far.map { Vec(a * it.x + b * it.y + e, b * it.x + d * it.y + f) }
            val flap = clipToRect(reflected, scene.flapBounds)
            return CurlGeometry(n, m, kept, far, flap, a, b, d, e, f, isFlat = false)
        }

        /** Sutherland-Hodgman clip of [poly] to the half-plane `(p - point) · normal >= 0`, as `curl.html`'s `clipHalf`. */
        fun clipHalf(poly: List<Vec>, point: Vec, normal: Vec): List<Vec> {
            val out = ArrayList<Vec>()
            for (i in poly.indices) {
                val p = poly[i]
                val q = poly[(i + 1) % poly.size]
                val dp = (p.x - point.x) * normal.x + (p.y - point.y) * normal.y
                val dq = (q.x - point.x) * normal.x + (q.y - point.y) * normal.y
                if (dp >= 0) out += p
                if ((dp >= 0) != (dq >= 0)) {
                    val k = dp / (dp - dq)
                    out += Vec(p.x + k * (q.x - p.x), p.y + k * (q.y - p.y))
                }
            }
            return out
        }

        /** [poly] clipped to [rect] by its four inward half-planes. */
        fun clipToRect(poly: List<Vec>, rect: CurlRect): List<Vec> = poly
            .let { clipHalf(it, Vec(rect.left, 0f), Vec(1f, 0f)) }
            .let { clipHalf(it, Vec(rect.right, 0f), Vec(-1f, 0f)) }
            .let { clipHalf(it, Vec(0f, rect.top), Vec(0f, 1f)) }
            .let { clipHalf(it, Vec(0f, rect.bottom), Vec(0f, -1f)) }
    }
}
