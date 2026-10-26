package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import com.omnibus.omnibus.ui.theme.DisplayFontFamily
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.UiFontFamily
import kotlin.math.max

/**
 * The generated cover for a book without art: a gradient in the book's [CoverIdentity.tone] with its
 * title set in the display face. [BookCover] always draws it behind the remote image, so a
 * transparent cover never shows an empty box.
 *
 * Everything scales with the width of the box it's given, so it reads the same at any size.
 *
 * @param identity The book whose tone and title the plate shows.
 * @param modifier Sizes the plate; it fills its parent when given [Modifier.fillMaxSize].
 */
@Composable
fun GeneratedCoverPlate(
    identity: CoverIdentity,
    modifier: Modifier = Modifier,
) {
    val tone = identity.tone
    val isLight = Oklch.fromSrgb(OmnibusTheme.palette.bg0).l > 0.5
    val top = Oklch(if (isLight) 0.62 else 0.42, tone.c * 0.85, tone.h).toColor()
    val bottom = Oklch(if (isLight) 0.48 else 0.26, tone.c * 0.65, tone.h).toColor()

    BoxWithConstraints(
        modifier = modifier.drawBehind {
            drawRect(Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset(size.width, size.height)))
            // A soft highlight keeps the plate from reading as a flat swatch at large sizes.
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0f)),
                    center = Offset(size.width * 0.3f, size.height * 0.16f),
                    radius = size.width * 0.85f,
                ),
            )
        },
    ) {
        // One unit is 1% of the cover's width.
        val unit = maxWidth / 100
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(unit * 9),
            verticalArrangement = Arrangement.spacedBy(unit * 3),
        ) {
            Box(
                Modifier
                    .width(unit * 22)
                    .height(max(1f, unit.value * 0.7f).dp)
                    .background(Color.White.copy(alpha = 0.5f)),
            )
            PlateTitle(identity.title, unit)
            identity.creator?.let { PlateCreator(it, unit) }
        }
    }
}

@Composable
private fun PlateTitle(title: String, unit: Dp) {
    val full = unit.asSp() * 11
    BasicText(
        text = title,
        style = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.94f),
            lineHeight = 1.1.em,
        ),
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        // Shrinks to fit the four lines, but no further than 70%.
        autoSize = TextAutoSize.StepBased(
            minFontSize = full * 0.7f,
            maxFontSize = full,
            stepSize = max(0.1f, full.value * 0.02f).sp,
        ),
    )
}

@Composable
private fun PlateCreator(creator: String, unit: Dp) {
    BasicText(
        text = creator.uppercase(),
        style = TextStyle(
            fontFamily = UiFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = unit.asSp() * 5,
            letterSpacing = unit.asSp() * 0.35f,
            color = Color.White.copy(alpha = 0.62f),
        ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Dp.asSp(): TextUnit = with(LocalDensity.current) { toSp() }

@PreviewLightDark
@Composable
private fun GeneratedCoverPlatePreview() {
    OmnibusTheme {
        Box(Modifier.size(width = 160.dp, height = 240.dp)) {
            GeneratedCoverPlate(
                identity = CoverIdentity(
                    uuid = "preview",
                    title = "The Left Hand of Darkness",
                    creator = "Ursula K. Le Guin",
                    hasCover = false,
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun GeneratedCoverPlateLongTitlePreview() {
    OmnibusTheme {
        Box(Modifier.size(width = 160.dp, height = 240.dp)) {
            GeneratedCoverPlate(
                identity = CoverIdentity(
                    uuid = "preview",
                    title = "A Very Long Title That Keeps Going Well Past Four Lines " +
                        "Of Display Type And Must Clamp Rather Than Overflow The Plate",
                    creator = "A Creator With An Unusually Long Name Indeed",
                    hasCover = false,
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
