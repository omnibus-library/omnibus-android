package com.omnibus.omnibus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R

/** Cormorant Garamond, the display face shared with the iOS and web clients. Bundles Normal, Medium, and SemiBold. */
val DisplayFontFamily = FontFamily(
    Font(R.font.cormorant_garamond_regular, FontWeight.Normal),
    Font(R.font.cormorant_garamond_medium, FontWeight.Medium),
    Font(R.font.cormorant_garamond_semibold, FontWeight.SemiBold),
)

/** Instrument Sans, the UI face shared with the iOS and web clients. Bundles Normal, Medium, and SemiBold. */
val UiFontFamily = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_medium, FontWeight.Medium),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
)

/** Space Mono, for addresses, numerals, and keys. Only Regular is bundled. */
val MonoFontFamily = FontFamily(Font(R.font.space_mono_regular))

/**
 * A complete [TextStyle] in [DisplayFontFamily], so it replaces rather than merges with
 * `LocalTextStyle` (whose Material letter spacing would otherwise leak in). Display text uses
 * lining figures so numerals sit on the baseline next to the Garamond lowercase.
 *
 * @param size Font size in sp.
 */
fun displayTextStyle(size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = DisplayFontFamily,
    fontWeight = weight,
    fontSize = size,
    color = color,
    fontFeatureSettings = "lnum",
)

/** Whole-sp overload of [displayTextStyle]. */
fun displayTextStyle(size: Int, color: Color, weight: FontWeight = FontWeight.Normal) =
    displayTextStyle(size.sp, color, weight)

/**
 * A complete [TextStyle] in [UiFontFamily]; see [displayTextStyle].
 *
 * @param size Font size in sp. Fractional sizes are needed for the design's 12.5 and 13.5 labels.
 */
fun uiTextStyle(size: TextUnit, weight: FontWeight, color: Color) = TextStyle(
    fontFamily = UiFontFamily,
    fontWeight = weight,
    fontSize = size,
    color = color,
)

/** Whole-sp overload of [uiTextStyle]. */
fun uiTextStyle(size: Int, weight: FontWeight, color: Color) = uiTextStyle(size.sp, weight, color)

/**
 * A complete [TextStyle] in [MonoFontFamily]; see [displayTextStyle].
 *
 * @param size Font size in sp.
 */
fun monoTextStyle(size: TextUnit, color: Color) = TextStyle(
    fontFamily = MonoFontFamily,
    fontSize = size,
    color = color,
)

/** Whole-sp overload of [monoTextStyle]. */
fun monoTextStyle(size: Int, color: Color) = monoTextStyle(size.sp, color)

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)