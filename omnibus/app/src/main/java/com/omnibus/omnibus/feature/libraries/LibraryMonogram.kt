package com.omnibus.omnibus.feature.libraries

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.MonogramFontFamily
import java.text.Normalizer

/** What a library's tile shows: a letter, or the Maltese cross when its name has no letter (C5). */
sealed interface Monogram {

    /** The first letter of the name, upper-cased as one code point. */
    data class Letter(val letter: String) : Monogram

    /** The Maltese cross, for a name with no letter. */
    data object MalteseCross : Monogram
}

/** The first letter anywhere in [name], NFC-normalized and upper-cased as one code point, else the Maltese cross. */
fun monogramOf(name: String): Monogram {
    val normalized = Normalizer.normalize(name, Normalizer.Form.NFC)
    var i = 0
    while (i < normalized.length) {
        val codePoint = normalized.codePointAt(i)
        if (Character.isLetter(codePoint)) {
            return Monogram.Letter(String(Character.toChars(Character.toUpperCase(codePoint))))
        }
        i += Character.charCount(codePoint)
    }
    return Monogram.MalteseCross
}

/**
 * The glyph of [monogram], [size] square. It draws only the glyph: the caller draws the tile and sets the colour
 * with `CompositionLocalProvider(LocalContentColor provides …)`, so the glyph has no semantics of its own.
 */
@Composable
fun LibraryMonogram(monogram: Monogram, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        when (monogram) {
            is Monogram.Letter -> {
                // Converting from Dp divides out the font scale, so the glyph keeps its size in the tile.
                val fontSize = with(LocalDensity.current) { (size * GLYPH_RATIO).toSp() }
                Text(
                    text = monogram.letter,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(
                        color = LocalContentColor.current,
                        fontFamily = MonogramFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fontSize,
                        lineHeight = fontSize,
                        textAlign = TextAlign.Center,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                    ),
                )
            }
            Monogram.MalteseCross -> Icon(
                painter = painterResource(R.drawable.ic_maltese_cross),
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(size * CROSS_RATIO),
            )
        }
    }
}

/** A 24sp letter in a 40dp tile. */
private const val GLYPH_RATIO = 0.6f

/** The cross's share of the tile, so it weighs about the same as a letter. */
private const val CROSS_RATIO = 0.5f
