package com.omnibus.omnibus.feature.reader.ui

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Ease
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.Motion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Test tags on the ribbon. */
internal object ReaderRibbonTags {
    const val RIBBON = "reader_ribbon"
}

/** The ribbon's motion alpha, 0 when hidden and 1 at rest, before the overlap alpha, so a test can read it. */
internal val ReaderRibbonAlphaKey = SemanticsPropertyKey<Float>("ReaderRibbonAlpha")

/**
 * The bookmark ribbon hanging from the top of the page, in [color]. It fills its parent, takes no touches and is
 * hidden from accessibility (the quick action's label already says whether the page is bookmarked).
 * Each new [frame] is mapped to a motion by [ribbonMotion]; a new frame cancels the running one, which then
 * continues from wherever the ribbon is. Previews show it at rest, without animating.
 */
@Composable
internal fun ReaderRibbon(
    frame: RibbonFrame,
    color: Color,
    placement: RibbonPlacement,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val inspection = LocalInspectionMode.current
    val offset = remember { Animatable(0f) } // dp; 0 is rest, negative is up
    val fade = remember { Animatable(if (inspection && frame.shown) 1f else 0f) }
    val sway = remember { Animatable(0f) } // degrees, clockwise
    val last = remember { RibbonFrameHolder() }
    LaunchedEffect(frame) {
        val motion = ribbonMotion(last.frame, frame, reduceMotion)
        last.frame = frame
        if (inspection) {
            // Previews and inspection-mode tests: no motion, straight to the end state.
            fade.snapTo(if (frame.shown) 1f else 0f)
            offset.snapTo(0f)
            sway.snapTo(0f)
        } else {
            play(motion, offset, fade, sway)
        }
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) } // android.graphics.Paint
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(AbsoluteAlignment.TopLeft) // physical left in LTR and RTL
                .absoluteOffset(x = placement.startDp.dp)
                .size(ReaderRibbonMetrics.WIDTH.dp, ReaderRibbonMetrics.HEIGHT.dp)
                .graphicsLayer {
                    translationY = offset.value.dp.toPx()
                    rotationZ = sway.value
                    alpha = fade.value * if (placement.translucent) ReaderRibbonMetrics.OVERLAP_ALPHA else 1f
                    transformOrigin = TransformOrigin(0.5f, 0f) // top centre, on the screen's top edge
                }
                .testTag(ReaderRibbonTags.RIBBON)
                .semantics {
                    this[ReaderRibbonAlphaKey] = fade.value
                    hideFromAccessibility()
                }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val top = -ReaderRibbonMetrics.TOP_BLEED.dp.toPx()
                    val notchY = h - ReaderRibbonMetrics.NOTCH_DEPTH * (h - top)
                    val path = Path().apply {
                        moveTo(0f, top)
                        lineTo(w, top)
                        lineTo(w, h)
                        lineTo(w / 2f, notchY)
                        lineTo(0f, h)
                        close()
                    }
                    // The mock's second background layer, the fabric, and the top shade over the fill.
                    val fabric = Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.20f),
                        0.28f to Color.Transparent,
                        0.70f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.16f),
                        startX = 0f,
                        endX = w,
                    )
                    val shade = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        1f to Color.Transparent,
                        startY = top,
                        endY = top + 44.dp.toPx(),
                    )
                    val box = Size(w, h - top)
                    onDrawBehind {
                        paint.color = color.toArgb()
                        paint.setShadowLayer(2.dp.toPx(), 1.dp.toPx(), 2.dp.toPx(), Color.Black.copy(alpha = 0.35f).toArgb())
                        drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
                        clipPath(path) {
                            drawRect(fabric, topLeft = Offset(0f, top), size = box)
                            drawRect(shade, topLeft = Offset(0f, top), size = box)
                        }
                    }
                },
        )
    }
}

/** The motion currently shown, kept outside the animation so the next frame can be mapped from it. */
private class RibbonFrameHolder {
    var frame: RibbonFrame? = null
}

/** The sway after an unfurl: 2.6 degrees, then -1.5, then 0.6, settling, after a 420 ms delay. */
private val SwaySpec = keyframes<Float> {
    durationMillis = 1500
    delayMillis = 420
    0f at 0 using EaseOut
    2.6f at 330 using EaseOut // 22%
    -1.5f at 720 using EaseOut // 48%
    0.6f at 1080 using EaseOut // 72%
    0f at 1500
}

/** Runs [motion] on the ribbon's animations. Each motion starts from wherever the ribbon is, so it can be interrupted. */
private suspend fun play(
    motion: RibbonMotion,
    offset: Animatable<Float, AnimationVector1D>,
    fade: Animatable<Float, AnimationVector1D>,
    sway: Animatable<Float, AnimationVector1D>,
) = coroutineScope {
    when (motion) {
        RibbonMotion.None -> Unit
        RibbonMotion.Drop -> {
            fade.snapTo(0f)
            offset.snapTo(0f)
            sway.snapTo(0f)
        }
        RibbonMotion.FadeIn -> {
            fade.snapTo(0f)
            offset.snapTo(0f)
            sway.snapTo(0f)
            fade.animateTo(1f, Motion.pageFade())
        }
        RibbonMotion.CrossfadeIn -> {
            fade.snapTo(0f)
            offset.snapTo(0f)
            sway.snapTo(0f)
            fade.animateTo(1f, Motion.crossfade())
        }
        RibbonMotion.FadeOut -> {
            sway.snapTo(0f)
            fade.animateTo(0f, Motion.pageFade())
        }
        RibbonMotion.CrossfadeOut -> {
            sway.snapTo(0f)
            fade.animateTo(0f, Motion.crossfade())
            offset.snapTo(0f)
        }
        RibbonMotion.Unfurl -> {
            sway.snapTo(0f)
            // A furl that was interrupted leaves the ribbon visible, so it continues from there.
            if (fade.value == 0f) offset.snapTo(ReaderRibbonMetrics.HIDDEN_OFFSET)
            launch { fade.animateTo(1f, tween(durationMillis = 220, easing = Ease)) }
            launch { sway.animateTo(0f, SwaySpec) }
            offset.animateTo(0f, Motion.unfurl())
        }
        RibbonMotion.Furl -> {
            sway.snapTo(0f)
            offset.animateTo(ReaderRibbonMetrics.HIDDEN_OFFSET, Motion.furl())
            fade.snapTo(0f)
        }
    }
}
