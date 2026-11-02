package com.omnibus.omnibus.feature.bookdetail.stage

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.cover.CoverThumbnail
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.rememberReducedMotion

/** Where the wash image sits in one frame of its drift: the scale and the offsets as fractions of the box. */
data class WashTransform(val scale: Float, val dxFraction: Float, val dyFraction: Float)

/**
 * The wash's drift at [phase] (0 to 1). Reduced motion always returns the phase-0 pose.
 */
internal fun washTransform(phase: Float, reducedMotion: Boolean): WashTransform {
    val p = if (reducedMotion) 0f else phase
    return WashTransform(
        scale = lerp(1.02f, 1.07f, p),
        dxFraction = lerp(-0.02f, 0.02f, p),
        dyFraction = lerp(-0.015f, 0.015f, p),
    )
}

/** The cover image is only drawn when there is a cover and the platform can blur it (API 31+). */
internal fun washShowsImage(hasCover: Boolean, sdkInt: Int): Boolean = hasCover && sdkInt >= 31

/** The stage ground for a book without art: the book's tone at lightness 0.3. */
internal fun stageGroundTone(tone: Oklch): Oklch = Oklch(0.3, tone.c, tone.h)

/** The scrim's (offset, alpha) stops, fading the stage into bg0 at the foot. */
internal fun washScrimStops(lightGround: Boolean): List<Pair<Float, Float>> = if (lightGround) {
    listOf(0f to .78f, .30f to .62f, .76f to .86f, 1f to 1f)
} else {
    listOf(0f to .55f, .30f to .16f, .76f to .74f, 1f to 1f)
}

/** Saturates the cover and darkens it on a dark ground (brightness 0.8); light grounds keep full brightness. */
private fun washMatrix(lightGround: Boolean): ColorMatrix = ColorMatrix().apply {
    setToSaturation(1.3f)
    val brightness = if (lightGround) 1f else 0.8f
    timesAssign(ColorMatrix().apply { setToScale(brightness, brightness, brightness, 1f) })
}

/**
 * The per-tab pan and glow that the stage reads at draw time. A tab change then redraws the wash without recomposing
 * the stage. The lambdas read animated state, so the values change between frames.
 */
@Stable
internal class BookStageWashPose(val pan: () -> Float, val warmth: () -> Float)

/** The wash pose the stage reads at draw time. Null means the stage uses its own `panFraction` and `warmth` parameters. */
internal val LocalBookStageWashPose = staticCompositionLocalOf<BookStageWashPose?> { null }

/**
 * The blurred, drifting cover behind the stage, with a warm glow and a scrim into bg0. Without a cover, or
 * below API 31, the stage shows the book's tone ground instead.
 *
 * @param panFraction How far the image is panned, as a fraction of the width. Used when no [LocalBookStageWashPose] is
 * provided.
 * @param warmth The glow's opacity. Used when no [LocalBookStageWashPose] is provided.
 */
@Composable
internal fun BookStageWash(
    identity: CoverIdentity,
    lightGround: Boolean,
    modifier: Modifier = Modifier,
    panFraction: Float = 0.165f,
    warmth: Float = 0.5f,
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val palette = OmnibusTheme.palette
    val showsImage = washShowsImage(identity.hasCover, Build.VERSION.SDK_INT) && !LocalInspectionMode.current
    val drifts = showsImage && !reducedMotion
    // Read inside the draw lambda, so the drift never recomposes the stage.
    val phase: State<Float> = if (drifts) {
        rememberInfiniteTransition(label = "wash").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(22_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "wash-phase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val pose = LocalBookStageWashPose.current
    val context = LocalPlatformContext.current
    val request = remember(identity.uuid, context) {
        ImageRequest.Builder(context).data(CoverThumbnail(identity.uuid, CoverSize.Md)).build()
    }

    Box(
        modifier
            .clipToBounds()
            .testTag(BookDetailTestTags.WASH)
            .background(if (showsImage) palette.bg0 else stageGroundTone(identity.tone).toColor()),
    ) {
        if (showsImage) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(washMatrix(lightGround)),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val t = washTransform(phase.value, reducedMotion)
                        scaleX = 1.4f * t.scale
                        scaleY = 1.4f * t.scale
                        translationX = size.width * ((pose?.pan?.invoke() ?: panFraction) + t.dxFraction)
                        translationY = size.height * t.dyFraction
                    }
                    .blur(20.dp, BlurredEdgeTreatment.Rectangle),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val center = Offset(size.width * 0.5f, size.height * 0.26f)
                    val ry = size.height * 0.5f
                    val rx = size.width * 0.85f
                    scale(scaleX = rx / ry, scaleY = 1f, pivot = center) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to Color(1f, 168 / 255f, 96 / 255f, 0.32f),
                                0.7f to Color.Transparent,
                                center = center,
                                radius = ry,
                            ),
                            radius = ry,
                            center = center,
                            alpha = pose?.warmth?.invoke() ?: warmth,
                        )
                    }
                },
        )
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            *washScrimStops(lightGround).map { (offset, alpha) -> offset to palette.bg0.copy(alpha = alpha) }
                                .toTypedArray(),
                        ),
                    )
                },
        )
    }
}
