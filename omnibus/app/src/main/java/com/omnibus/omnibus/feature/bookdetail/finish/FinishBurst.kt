package com.omnibus.omnibus.feature.bookdetail.finish

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The burst around the cover and the cover's replay tap. Draws nothing, and takes no taps, before a celebration
 * ([bursts] 0), when the book is not Finished, or with [reducedMotion]. Must sit in the cover's overlay box.
 */
@Composable
internal fun FinishBurst(
    finished: Boolean,
    bursts: Int,
    reducedMotion: Boolean,
    accent: Color,
    latch: PlayLatch,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!finished || bursts == 0) return
    if (reducedMotion) {
        LaunchedEffect(bursts) { latch.claim(bursts) }
        return
    }
    val elapsed = remember { Animatable(if (latch.isPending(bursts)) 0f else BURST_TOTAL_MILLIS.toFloat()) }
    LaunchedEffect(bursts) {
        if (latch.claim(bursts)) {
            elapsed.snapTo(0f)
            elapsed.animateTo(BURST_TOTAL_MILLIS.toFloat(), tween(BURST_TOTAL_MILLIS, easing = LinearEasing))
        }
    }
    val currentReplay by rememberUpdatedState(onReplay)
    Box(
        modifier
            .fillMaxSize()
            .testTag(FinishTestTags.COVER_TAP)
            .pointerInput(Unit) { detectTapGestures(onTap = { currentReplay() }) },
    ) {
        Canvas(Modifier.matchParentSize().testTag(FinishTestTags.BURST)) {
            drawBurst(elapsed.value, accent)
        }
    }
}

/** A ribbon: a 7 by 20 dp strip notched at the bottom. */
private val ribbonPath = PathParser().parsePathString("M0 0h7v20l-3.5-3L0 20z").toPath()

/** A diamond, 10 by 10 dp. */
private val diamondPath = PathParser().parsePathString("M5 0l5 5-5 5-5-5z").toPath()

/** A wavy squiggle, drawn as an open stroke. */
private val squigglePath = PathParser().parsePathString("M1 7c3-6 5-6 8 0s5 6 8 0 4-4 6-3").toPath()

/** Draws every piece at [elapsedMillis] of the throw, in the burst frame that covers the cover and its margins. */
private fun DrawScope.drawBurst(elapsedMillis: Float, accent: Color) {
    val frameLeft = -0.4f * size.width
    val frameTop = -0.2f * size.height
    val frameWidth = 1.8f * size.width
    val frameHeight = 1.4f * size.height
    val pxPerDp = density
    BURST_PIECES.forEachIndexed { index, piece ->
        val pose = burstPose(index, elapsedMillis)
        if (pose.alpha <= 0f) return@forEachIndexed
        val color = when (piece.tone) {
            BurstTone.Accent -> accent
            BurstTone.Cream -> BurstCream
            BurstTone.Gold -> BurstGold
        }.copy(alpha = pose.alpha)
        val w = piece.kind.widthDp.dp.toPx()
        val h = piece.kind.heightDp.dp.toPx()
        val left = frameLeft + frameWidth * piece.x / 100f + pose.dx.dp.toPx()
        val top = frameTop + frameHeight * piece.y / 100f + pose.dy.dp.toPx()
        val center = Offset(w / 2f, h / 2f)
        withTransform({
            translate(left, top)
            rotate(pose.rotation, pivot = center)
            scale(pose.scale, pose.scale, pivot = center)
            scale(pxPerDp, pxPerDp, pivot = Offset.Zero)
        }) {
            when (piece.kind) {
                BurstKind.Ribbon -> drawPath(ribbonPath, color)
                BurstKind.Diamond -> drawPath(diamondPath, color)
                BurstKind.Squiggle -> drawPath(squigglePath, color, style = Stroke(width = 3f, cap = StrokeCap.Round))
            }
        }
    }
}
