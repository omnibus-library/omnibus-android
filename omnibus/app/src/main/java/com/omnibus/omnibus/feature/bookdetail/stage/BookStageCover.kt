package com.omnibus.omnibus.feature.bookdetail.stage

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.transition.bookCoverStageModifier
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.theme.Oklch

/**
 * The book's cover on the stage: tilted in perspective, lit by a warm halo, standing on a floor shadow.
 * The [overlay] sits over the cover, outside the tilt.
 */
@Composable
internal fun BookStageCover(
    book: Book,
    lightGround: Boolean,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val identity = book.coverIdentity()
    val haloTone = Oklch(0.62, identity.tone.c, identity.tone.h)
    val description = stringResource(R.string.book_detail_cover_description, book.displayTitle)

    Box(
        modifier
            .width(168.dp)
            .aspectRatio(2f / 3f)
            .testTag(BookDetailTestTags.COVER)
            .semantics { contentDescription = description }
            .drawBehind { drawHaloAndFloor(haloTone) },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .then(bookCoverStageModifier(book.uuid))
                .graphicsLayer {
                    rotationZ = -2.5f
                    rotationX = 2f
                    rotationY = 8f
                    cameraDistance = 12f * density
                }
                .stageCoverShadows(lightGround),
        ) {
            BookCover(
                identity = identity,
                modifier = Modifier.fillMaxSize(),
                size = CoverSize.Lg,
                cornerRadius = 5.dp,
            )
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(5.dp))
                    .drawBehind {
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = .55f),
                                .022f to Color.Black.copy(alpha = .15f),
                                .032f to Color.White.copy(alpha = .22f),
                                .055f to Color.White.copy(alpha = 0f),
                                .065f to Color.Black.copy(alpha = .18f),
                                .09f to Color.Black.copy(alpha = 0f),
                                startX = 0f,
                                endX = size.width,
                            ),
                        )
                    },
            )
        }
        Box(Modifier.matchParentSize(), content = overlay)
    }
}

/** The warm halo behind the cover, the top fall of light and the elliptical floor shadow under it. */
private fun DrawScope.drawHaloAndFloor(haloTone: Oklch) {
    val w = size.width
    val h = size.height

    // Warm halo: CSS box inset -45% -85% -25%, radial closest-side.
    val haloCenter = Offset(w * 0.5f, h * 0.40f)
    scale(scaleX = (1.35f * w) / (0.85f * h), scaleY = 1f, pivot = haloCenter) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(1f, 190 / 255f, 122 / 255f, 0.24f),
                0.42f to haloTone.copy(alpha = 0.36).toColor(),
                0.72f to haloTone.copy(alpha = 0.08).toColor(),
                1f to Color.Transparent,
                center = haloCenter,
                radius = 0.85f * h,
            ),
            radius = 0.85f * h,
            center = haloCenter,
        )
    }

    // Top fall of light, clear by 74% of its radius.
    val fallCenter = Offset(w * 0.5f, -0.62f * h)
    val fallRy = 0.95f * h
    scale(scaleX = (0.828f * w) / fallRy, scaleY = 1f, pivot = fallCenter) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(1f, 228 / 255f, 192 / 255f, 0.18f),
                0.74f to Color.Transparent,
                center = fallCenter,
                radius = fallRy,
            ),
            radius = fallRy,
            center = fallCenter,
        )
    }

    // Floor: an ellipse under the cover, 0.52w by 17 dp, darkest at its centre.
    val floorCenter = Offset(w * 0.54f, h + 5.dp.toPx())
    val floorRy = 17.dp.toPx()
    val floorRx = 0.52f * w
    scale(scaleX = floorRx / floorRy, scaleY = 1f, pivot = floorCenter) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.72f),
                1f to Color.Transparent,
                center = floorCenter,
                radius = floorRy,
            ),
            radius = floorRy,
            center = floorCenter,
        )
    }
}

/**
 * The near contact shadow and the far floor shadow of the tilted cover. Below API 28 a framework shadow
 * layer is not available, so the elevation shadow stands in for the far one.
 */
private fun Modifier.stageCoverShadows(lightGround: Boolean): Modifier {
    val farAlpha = if (lightGround) 0.28f else 0.62f
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawBehind {
            drawIntoCanvas { canvas ->
                val radius = 5.dp.toPx()
                val near = Paint().apply {
                    asFrameworkPaint().setShadowLayer(2.dp.toPx(), 0f, 2.dp.toPx(), Color.Black.copy(alpha = 0.40f).toArgb())
                }
                canvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, near)
                val far = Paint().apply {
                    asFrameworkPaint().setShadowLayer(
                        30.dp.toPx(),
                        22.dp.toPx(),
                        30.dp.toPx(),
                        Color.Black.copy(alpha = farAlpha).toArgb(),
                    )
                }
                canvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, far)
            }
        }
    } else {
        shadow(
            elevation = 16.dp,
            shape = RoundedCornerShape(5.dp),
            ambientColor = Color.Transparent,
            spotColor = Color.Black.copy(alpha = farAlpha),
        )
    }
}
