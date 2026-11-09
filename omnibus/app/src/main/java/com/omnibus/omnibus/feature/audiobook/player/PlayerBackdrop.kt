package com.omnibus.omnibus.feature.audiobook.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.cover.CoverThumbnail
import com.omnibus.omnibus.feature.bookdetail.stage.washShowsImage
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.max

/** The bloom's three colors, from the book's [tone]: the core, a dimmer shoulder at half alpha, and a clear edge. */
internal fun playerBloomColors(tone: Oklch): List<Color> {
    val shoulder = Oklch(0.24, tone.c * 0.7, tone.h, alpha = 0.5)
    return listOf(
        Oklch(0.45, max(0.06, tone.c) * 1.05, tone.h).toColor(),
        shoulder.toColor(),
        shoulder.copy(alpha = 0.0).toColor(),
    )
}

private const val BLOOM_CENTER_Y = 0.32f
private val BloomRadius = 460.dp
private val CoverBlur = 70.dp
private const val COVER_SATURATION = 1.5f
private const val COVER_ALPHA = 0.4f
private const val FADE_TOP_ALPHA = 0.2f

/**
 * The player's ground, as on iOS: `bg0`, a bloom of the book's tone behind the artwork, the cover blurred heavily
 * under it, and a fade from a light `bg0` wash at the top to solid `bg0` at the foot. Without a cover, or below
 * API 31, only the bloom shows.
 */
@Composable
internal fun PlayerBackdrop(identity: CoverIdentity, modifier: Modifier = Modifier) {
    val bg0 = OmnibusTheme.palette.bg0
    val showsImage = washShowsImage(identity.hasCover, Build.VERSION.SDK_INT) && !LocalInspectionMode.current
    val bloom = remember(identity.tone) { playerBloomColors(identity.tone) }
    val context = LocalPlatformContext.current
    val request = remember(identity.uuid, context) {
        ImageRequest.Builder(context).data(CoverThumbnail(identity.uuid, CoverSize.Md)).build()
    }

    Box(modifier.clipToBounds().background(bg0)) {
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val center = Offset(size.width * 0.5f, size.height * BLOOM_CENTER_Y)
                    val radius = BloomRadius.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(colors = bloom, center = center, radius = radius),
                        radius = radius,
                        center = center,
                    )
                },
        )
        if (showsImage) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = COVER_ALPHA,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(COVER_SATURATION) }),
                modifier = Modifier.fillMaxSize().blur(CoverBlur, BlurredEdgeTreatment.Rectangle),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(bg0.copy(alpha = FADE_TOP_ALPHA), bg0)))
                },
        )
    }
}
