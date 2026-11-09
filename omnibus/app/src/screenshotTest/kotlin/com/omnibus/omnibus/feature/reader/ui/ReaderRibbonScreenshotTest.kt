package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.reader.ReaderMargins
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.ui.theme.OmnibusTheme

private const val SAMPLE_PROSE = "It was a bright cold day in April, and the clocks were striking thirteen. " +
    "Winston Smith, his chin nuzzled into his breast, slipped quickly through the glass doors."

private val TealCover = ReaderContentsPreviewCover.copy(accent = "oklch(0.55 0.09 195)")

/** The ribbon over text starting clear of it at a wide margin: drawn opaque. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReaderRibbonOpaque() {
    RibbonShot(margins = ReaderMargins.Wide, textLeft = 78.5.dp)
}

/** The ribbon over text starting under it at a narrow margin: drawn at 0.68 alpha, so the first letters show through. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReaderRibbonTranslucent() {
    RibbonShot(margins = ReaderMargins.Narrow, textLeft = 31.5.dp)
}

/** The ribbon over a text sample on the reading theme's ground, in the book's accent, drawn at rest. */
@Composable
private fun RibbonShot(margins: ReaderMargins, textLeft: Dp) {
    val theme = if (isSystemInDarkTheme()) ReaderTheme.Dark else ReaderTheme.Light
    OmnibusTheme(darkTheme = !theme.isLight) {
        Box(Modifier.size(360.dp, 220.dp).background(Color(theme.groundArgb))) {
            Text(
                text = SAMPLE_PROSE,
                modifier = Modifier.fillMaxSize().padding(start = textLeft, end = textLeft, top = 58.dp),
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = readerChromeColors(theme).ink,
                ),
            )
            ReaderRibbon(
                frame = RibbonFrame("p", shown = true, tapped = false),
                color = readerPageAccent(ReaderBookState.Ready("The Long Road Home", TealCover), theme),
                placement = ribbonPlacement(360f, 0f, 0f, margins, "none"),
                reduceMotion = true,
            )
        }
    }
}
