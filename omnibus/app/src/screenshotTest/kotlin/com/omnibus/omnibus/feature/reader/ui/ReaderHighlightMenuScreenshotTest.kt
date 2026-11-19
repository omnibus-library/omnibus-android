package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.HighlightMenu
import com.omnibus.omnibus.feature.reader.HighlightMenuEntry
import com.omnibus.omnibus.ui.theme.tint

private const val SAMPLE_LINE = 22.0
private const val SAMPLE_LEFT = 20.0
private const val SAMPLE_RIGHT = 300.0

private val SAMPLE_LINES = listOf(
    "She had not expected the house to",
    "be quiet. Every room held its",
    "breath, as if the walls had been",
    "listening long before she arrived.",
    "A single door stood open, and the",
)

/** One sample line's rect, [index] lines below [textTop], from [left] to [right] dp. */
private fun lineRect(index: Int, textTop: Double, left: Double = SAMPLE_LEFT, right: Double = SAMPLE_RIGHT): PageRect =
    PageRect(left, textTop + index * SAMPLE_LINE, right - left, SAMPLE_LINE)

private fun sampleEntry(color: HighlightColor, rects: List<PageRect>): HighlightMenuEntry = HighlightMenuEntry(
    cfiRange = "epubcfi(/6/2!/4/2/1:${color.ordinal},/4/2/1:9)",
    rects = rects,
    color = color,
    text = "Every room held its breath",
)

/**
 * One reading theme's tile: the sample prose on its ground, translucent tints behind the highlighted lines to stand in
 * for the page's marks, and the highlight menu over them, with its text [textTop] dp down the tile.
 */
@Composable
private fun MenuTile(theme: ReaderTheme, menu: HighlightMenu, textTop: Double = 64.0) {
    Box(Modifier.size(360.dp, 300.dp).background(Color(theme.groundArgb))) {
        menu.entries.forEach { entry ->
            entry.rects.forEach { rect ->
                Box(
                    Modifier
                        .offset(rect.x.toFloat().dp, rect.y.toFloat().dp)
                        .size(rect.width.toFloat().dp, rect.height.toFloat().dp)
                        .background(entry.color.tint().copy(alpha = 0.45f)),
                )
            }
        }
        // Each line sits in a box one line tall, so the text lines up with the tints whatever the font's own line height.
        SAMPLE_LINES.forEachIndexed { index, line ->
            Box(
                Modifier
                    .offset(SAMPLE_LEFT.toFloat().dp, (textTop + index * SAMPLE_LINE).toFloat().dp)
                    .height(SAMPLE_LINE.toFloat().dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = line,
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 15.sp,
                        color = readerChromeColors(theme).ink,
                    ),
                )
            }
        }
        ReaderHighlightMenuOverlay(
            menu = menu,
            theme = theme,
            onStep = {},
            onCopy = {},
            onDismiss = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The menu over one highlight on each of the four reading themes, each with a different active colour. */
@PreviewTest
@Preview(widthDp = 360, heightDp = 1200)
@Composable
fun ReaderHighlightMenuFourThemes() {
    Column(Modifier.size(360.dp, 1200.dp)) {
        ReaderTheme.entries.forEachIndexed { index, theme ->
            val rects = listOf(lineRect(1, 64.0), lineRect(2, 64.0), lineRect(3, 64.0))
            MenuTile(theme, HighlightMenu(listOf(sampleEntry(HighlightColor.entries[index], rects))))
        }
    }
}

/**
 * Two overlapping highlights on a Sepia page, with the second (blue) active: the switcher reads "2 of 2" and the blue
 * highlight is outlined.
 */
@PreviewTest
@Preview(widthDp = 360, heightDp = 300)
@Composable
fun ReaderHighlightMenuOverlapSwitcher() {
    val textTop = 40.0
    val amber = sampleEntry(
        HighlightColor.Amber,
        listOf(lineRect(1, textTop), lineRect(2, textTop, right = 260.0)),
    )
    val blue = sampleEntry(HighlightColor.Blue, listOf(lineRect(2, textTop, left = 120.0, right = 280.0)))
    MenuTile(ReaderTheme.Sepia, HighlightMenu(listOf(amber, blue), active = 1), textTop = textTop)
}
