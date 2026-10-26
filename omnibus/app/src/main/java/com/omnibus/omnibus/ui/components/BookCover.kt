package com.omnibus.omnibus.ui.components

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.cover.CoverThumbnail
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.max

/** Width over height of every cover box. */
private const val COVER_ASPECT_RATIO = 2f / 3f

/**
 * A book cover: the generated plate, the book's art over it, and the lighting that makes a grid read
 * as objects on a shelf.
 *
 * The cover is a 2:3 box the art fills, so rows stay even when source art disagrees about aspect.
 * The plate is a permanent backdrop rather than a loading placeholder, since some covers are
 * transparent PNGs. The cover is decorative to accessibility services; callers describe the book.
 * Add [coverShadow] to [modifier] for the lift off the shelf.
 *
 * @param identity The book to draw. Art is only requested when [CoverIdentity.hasCover] is true.
 * @param size The thumbnail size to request: [CoverSize.Md] for the grid and Continue card,
 * [CoverSize.Sm] for shelf mosaic slots.
 * @param cornerRadius 5 dp on the grid, 6 dp on the Continue card and shelf tiles, 0 for mosaic slots.
 * @param keepsAspect When false the cover fills whatever box it's given instead of imposing 2:3, so
 * mosaic slots tile without gaps.
 */
@Composable
fun BookCover(
    identity: CoverIdentity,
    modifier: Modifier = Modifier,
    size: CoverSize = CoverSize.Md,
    cornerRadius: Dp = 5.dp,
    keepsAspect: Boolean = true,
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .then(if (keepsAspect) Modifier.aspectRatio(COVER_ASPECT_RATIO) else Modifier)
            .clip(shape)
            // A hairline keeps a white-edged cover from dissolving into the dark ground without
            // reading as a drawn border.
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)
            .clearAndSetSemantics {},
    ) {
        GeneratedCoverPlate(identity, Modifier.fillMaxSize())
        if (identity.hasCover && !LocalInspectionMode.current) {
            CoverArt(identity.uuid, size)
        }
        CoverLighting(Modifier.fillMaxSize())
    }
}

@Composable
private fun CoverArt(uuid: String, size: CoverSize) {
    val context = LocalPlatformContext.current
    val request = remember(uuid, size, context) {
        ImageRequest.Builder(context).data(CoverThumbnail(uuid, size)).build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * What turns a cover from a picture into an object: a gutter shadow down the spine edge and a faint
 * specular wash across the top-left, as if the shelf were lit from above. Both are weak enough that
 * they register as depth rather than as an effect laid over the art.
 */
@Composable
private fun CoverLighting(modifier: Modifier = Modifier) {
    Box(
        modifier.drawBehind {
            val gutter = max(3.dp.toPx(), size.width * 0.07f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.22f),
                        Color.Black.copy(alpha = 0.04f),
                        Color.Black.copy(alpha = 0f),
                    ),
                    startX = 0f,
                    endX = gutter,
                ),
                size = Size(gutter, size.height),
            )
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0f)),
                    start = Offset.Zero,
                    end = Offset(size.width / 2, size.height / 2),
                ),
            )
        },
    )
}

/**
 * The lift that makes a grid read as objects on a shelf rather than tiles in a table: black at 45%,
 * blurred about 10 dp, 5 dp down.
 *
 * @param cornerRadius Must match the [BookCover]'s so the shadow follows its corners.
 * @param strength Scales the opacity, blur and offset; the Continue cover uses 1.2.
 */
fun Modifier.coverShadow(cornerRadius: Dp, strength: Float = 1f): Modifier {
    val color = Color.Black.copy(alpha = (0.45f * strength).coerceAtMost(1f))
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawBehind {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    asFrameworkPaint().setShadowLayer(
                        (10.dp * strength).toPx(),
                        0f,
                        (5.dp * strength).toPx(),
                        color.toArgb(),
                    )
                }
                val radius = cornerRadius.toPx()
                canvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, paint)
            }
        }
    } else {
        // Before API 28 a blurred shadow layer only draws under text, so approximate with an elevation.
        shadow(
            elevation = 10.dp * strength,
            shape = RoundedCornerShape(cornerRadius),
            ambientColor = Color.Transparent,
            spotColor = color,
        )
    }
}

@PreviewLightDark
@Composable
private fun BookCoverPreview() {
    OmnibusTheme {
        Box(Modifier.width(140.dp)) {
            BookCover(
                identity = CoverIdentity(
                    uuid = "preview",
                    title = "The Left Hand of Darkness",
                    creator = "Ursula K. Le Guin",
                    hasCover = false,
                ),
                modifier = Modifier.coverShadow(5.dp),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun BookCoverLongTitlePreview() {
    OmnibusTheme {
        Box(Modifier.width(140.dp)) {
            BookCover(
                identity = CoverIdentity(
                    uuid = "preview",
                    title = "A Very Long Title That Keeps Going Well Past Four Lines " +
                        "Of Display Type And Must Clamp Rather Than Overflow The Plate",
                    creator = "A Creator With An Unusually Long Name Indeed",
                    hasCover = true,
                ),
                modifier = Modifier.coverShadow(5.dp),
            )
        }
    }
}
