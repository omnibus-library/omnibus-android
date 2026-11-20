package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.HighlightMenu
import com.omnibus.omnibus.feature.reader.HighlightMenuAction
import com.omnibus.omnibus.feature.reader.HighlightMenuEntry
import com.omnibus.omnibus.ui.theme.tint

/** The highlight's two lines, in dp on a 360 dp tile. */
private val SHARE_RECTS = listOf(PageRect(20.0, 160.0, 280.0, 22.0), PageRect(20.0, 182.0, 200.0, 22.0))

/**
 * The tap menu with Share before Copy (#330), over one highlight on each of the four reading themes. The actions are
 * fixed here, not read from the enum, so actions other tickets add never change this reference.
 */
@PreviewTest
@Preview(widthDp = 360, heightDp = 1440)
@Composable
fun ReaderHighlightMenuShareFourThemes() {
    Column(Modifier.size(360.dp, 1440.dp)) {
        ReaderTheme.entries.forEachIndexed { index, theme ->
            val color = HighlightColor.entries[index]
            val entry = HighlightMenuEntry(
                cfiRange = "epubcfi(/6/2!/4/2/1:${color.ordinal},/4/2/1:9)",
                rects = SHARE_RECTS,
                color = color,
                text = "Every room held its breath",
                hasNote = false,
            )
            ShareTile(theme, entry)
        }
    }
}

/** One theme's tile: its ground, a tint standing in for the page's mark, and the menu over it. */
@Composable
private fun ShareTile(theme: ReaderTheme, entry: HighlightMenuEntry) {
    Box(Modifier.size(360.dp, 360.dp).background(Color(theme.groundArgb))) {
        entry.rects.forEach { rect ->
            Box(
                Modifier
                    .offset(rect.x.toFloat().dp, rect.y.toFloat().dp)
                    .size(rect.width.toFloat().dp, rect.height.toFloat().dp)
                    .background(entry.color.tint().copy(alpha = 0.45f)),
            )
        }
        ReaderHighlightMenuOverlay(
            menu = HighlightMenu(listOf(entry)),
            actions = listOf(HighlightMenuAction.Share, HighlightMenuAction.Copy, HighlightMenuAction.Delete),
            theme = theme,
            onStep = {},
            onColor = {},
            onAction = {},
            onDismiss = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
