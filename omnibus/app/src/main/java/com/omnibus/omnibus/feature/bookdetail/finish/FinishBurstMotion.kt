package com.omnibus.omnibus.feature.bookdetail.finish

import androidx.compose.animation.core.CubicBezierEasing

/** The shape of a burst piece and its size in dp. */
internal enum class BurstKind(val widthDp: Float, val heightDp: Float) {
    /** A notched ribbon, 7 by 20 dp. */
    Ribbon(7f, 20f),

    /** A wavy squiggle, 24 by 10 dp. */
    Squiggle(24f, 10f),

    /** A diamond, 10 by 10 dp. */
    Diamond(10f, 10f),
}

/** The colour family of a burst piece: the accent, or one of the two fixed tones. */
internal enum class BurstTone { Accent, Cream, Gold }

/** Where a piece rests, in percent of the burst frame, what it is, and its rest rotation in degrees. */
internal data class BurstPiece(
    val x: Float,
    val y: Float,
    val kind: BurstKind,
    val tone: BurstTone,
    val rotation: Float,
)

/** The sixteen pieces, in the design's order (`OA_SHAPES`, iOS `BurstPiece.all`). */
internal val BURST_PIECES: List<BurstPiece> = listOf(
    BurstPiece(8f, 6f, BurstKind.Ribbon, BurstTone.Accent, -24f),
    BurstPiece(84f, 8f, BurstKind.Squiggle, BurstTone.Cream, 12f),
    BurstPiece(20f, 2f, BurstKind.Diamond, BurstTone.Gold, 0f),
    BurstPiece(66f, 0f, BurstKind.Ribbon, BurstTone.Cream, 32f),
    BurstPiece(2f, 30f, BurstKind.Squiggle, BurstTone.Gold, -40f),
    BurstPiece(92f, 32f, BurstKind.Ribbon, BurstTone.Gold, 18f),
    BurstPiece(6f, 54f, BurstKind.Diamond, BurstTone.Accent, 0f),
    BurstPiece(90f, 56f, BurstKind.Diamond, BurstTone.Cream, 0f),
    BurstPiece(0f, 76f, BurstKind.Ribbon, BurstTone.Cream, 42f),
    BurstPiece(93f, 78f, BurstKind.Squiggle, BurstTone.Accent, 64f),
    BurstPiece(40f, 0f, BurstKind.Squiggle, BurstTone.Accent, -12f),
    BurstPiece(12f, 90f, BurstKind.Squiggle, BurstTone.Cream, -22f),
    BurstPiece(84f, 92f, BurstKind.Ribbon, BurstTone.Accent, -30f),
    BurstPiece(52f, 4f, BurstKind.Diamond, BurstTone.Cream, 0f),
    BurstPiece(30f, 96f, BurstKind.Diamond, BurstTone.Gold, 0f),
    BurstPiece(70f, 96f, BurstKind.Ribbon, BurstTone.Gold, 14f),
)

/** One piece's throw, without its stagger. */
internal const val BURST_PIECE_MILLIS = 1_500f

/** The whole throw, including the longest stagger (2 x 50 ms). */
internal const val BURST_TOTAL_MILLIS = 1_600

/** How long the card takes to rise in. */
internal const val RISE_MILLIS = 560

/** How far below its place the card starts, in dp. */
internal const val RISE_OFFSET_DP = 26f

/** The design's ease, cubic-bezier(.16, 1, .3, 1). */
internal val FinishEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** A piece's pose: opacity, offset from its rest place in dp, scale and rotation in degrees. */
internal data class BurstPose(val alpha: Float, val dx: Float, val dy: Float, val scale: Float, val rotation: Float)

/** The pose of piece [index] at [elapsedMillis] since the throw began (design `replay` keyframes). */
internal fun burstPose(index: Int, elapsedMillis: Float): BurstPose {
    val piece = BURST_PIECES[index]
    val delay = (index % 3) * 50f
    val raw = ((elapsedMillis - delay) / BURST_PIECE_MILLIS).coerceIn(0f, 1f)
    val t = FinishEasing.transform(raw)
    val fromX = (50f - piece.x) * 2.6f
    val fromY = (50f - piece.y) * 3.2f
    return when {
        t < 0.2f -> {
            val k = t / 0.2f
            BurstPose(
                alpha = k,
                dx = fromX * (1 - k),
                dy = fromY * (1 - k),
                scale = 0.3f + 0.7f * k,
                rotation = piece.rotation * k,
            )
        }
        t < 0.7f -> {
            val k = (t - 0.2f) / 0.5f
            BurstPose(alpha = 1f, dx = 0f, dy = 14f * k, scale = 1f, rotation = piece.rotation + 12f * k)
        }
        else -> {
            val k = (t - 0.7f) / 0.3f
            BurstPose(alpha = 1f, dx = 0f, dy = 14f * (1 - k), scale = 1f, rotation = piece.rotation + 12f * (1 - k))
        }
    }
}
