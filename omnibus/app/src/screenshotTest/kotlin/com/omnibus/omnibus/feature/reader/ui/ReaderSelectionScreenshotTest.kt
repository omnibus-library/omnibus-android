package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.reader.ReaderTheme

private const val SAMPLE_TEXT_TOP = 184.0
private const val SAMPLE_LINE = 22.0

private val SAMPLE_LINES = listOf(
    "It was a bright cold day in April,",
    "and the clocks were striking thirteen.",
    "Winston Smith slipped quietly through",
    "the glass doors of Victory Mansions,",
    "a little too fast for the wind.",
)

/** The selection over the second to fourth sample lines, with the menu above it. */
private fun tileSelection(): SelectionData = SelectionData(
    cfiRange = "epubcfi(/6/2!/4/2/1:0,/4/2/1:9)",
    text = "and the clocks were striking thirteen. Winston Smith slipped quietly through",
    rects = listOf(
        PageRect(20.0, SAMPLE_TEXT_TOP + SAMPLE_LINE, 300.0, SAMPLE_LINE),
        PageRect(20.0, SAMPLE_TEXT_TOP + 2 * SAMPLE_LINE, 300.0, SAMPLE_LINE),
        PageRect(20.0, SAMPLE_TEXT_TOP + 3 * SAMPLE_LINE, 250.0, SAMPLE_LINE),
    ),
    start = SelectionHandle(20.0, SAMPLE_TEXT_TOP + SAMPLE_LINE, SAMPLE_LINE),
    end = SelectionHandle(270.0, SAMPLE_TEXT_TOP + 3 * SAMPLE_LINE, SAMPLE_LINE),
)

/** One reading theme's tile: sample prose on its ground, with the selection and its menu above it. */
@Composable
private fun SelectionTile(theme: ReaderTheme) {
    Box(Modifier.size(360.dp, 300.dp).background(Color(theme.groundArgb))) {
        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = SAMPLE_TEXT_TOP.toFloat().dp)) {
            SAMPLE_LINES.forEach { line ->
                Text(
                    text = line,
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 15.sp,
                        lineHeight = SAMPLE_LINE.toFloat().sp,
                        color = readerChromeColors(theme).ink,
                    ),
                )
            }
        }
        ReaderSelectionOverlay(
            selection = tileSelection(),
            menuShown = true,
            theme = theme,
            onGrab = {},
            onDrag = { _, _, _ -> },
            onRelease = {},
            onColorChosen = {},
            onCopy = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The selection overlay on each of the four reading themes, one above the other. */
@PreviewTest
@Preview(widthDp = 360, heightDp = 1200)
@Composable
fun ReaderSelectionFourThemes() {
    Column(Modifier.size(360.dp, 1200.dp)) {
        ReaderTheme.entries.forEach { SelectionTile(it) }
    }
}

/** A selection near the right edge of a dark page at the top, so its menu flips below and is clamped inside the width. */
@PreviewTest
@Preview(widthDp = 320, heightDp = 260)
@Composable
fun ReaderSelectionMenuBelowAtTheEdge() {
    val selection = SelectionData(
        cfiRange = "epubcfi(/6/2!/4/2/1:0,/4/2/1:9)",
        text = "sound",
        rects = listOf(PageRect(180.0, 70.0, 120.0, 22.0)),
        start = SelectionHandle(180.0, 70.0, 22.0),
        end = SelectionHandle(300.0, 70.0, 22.0),
    )
    Box(Modifier.size(320.dp, 260.dp).background(Color(ReaderTheme.Dark.groundArgb))) {
        ReaderSelectionOverlay(
            selection = selection,
            menuShown = true,
            theme = ReaderTheme.Dark,
            onGrab = {},
            onDrag = { _, _, _ -> },
            onRelease = {},
            onColorChosen = {},
            onCopy = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
