package com.omnibus.omnibus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R

/** Cormorant Garamond, the display face shared with the iOS and web clients. Only Regular is bundled. */
val DisplayFontFamily = FontFamily(Font(R.font.cormorant_garamond_regular))

/** Instrument Sans, the UI face shared with the iOS and web clients. Bundles Normal, Medium, and SemiBold. */
val UiFontFamily = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_medium, FontWeight.Medium),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
)

/**
 * A complete [TextStyle] in [DisplayFontFamily], so it replaces rather than merges with
 * `LocalTextStyle` (whose Material letter spacing would otherwise leak in).
 *
 * @param size Font size in sp.
 */
fun displayTextStyle(size: Int, color: Color) = TextStyle(
    fontFamily = DisplayFontFamily,
    fontSize = size.sp,
    color = color,
)

/**
 * A complete [TextStyle] in [UiFontFamily]; see [displayTextStyle].
 *
 * @param size Font size in sp.
 */
fun uiTextStyle(size: Int, weight: FontWeight, color: Color) = TextStyle(
    fontFamily = UiFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    color = color,
)

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