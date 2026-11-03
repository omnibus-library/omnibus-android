package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.TimeZone

/** What a row shows: its passage and note (blank text and note already trimmed to null), colour, chapter and date. */
data class HighlightRowModel(
    val id: Long,
    val text: String?,
    val note: String?,
    val color: HighlightColor,
    val chapterNumber: Int?,
    val createdAtMillis: Long?,
)

/**
 * One highlight: the passage (or the other-device line), the note, a footer and the colour ribbon, between a 1 dp rule
 * and the next row. [actions] fills the footer's trailing end; the Highlights list passes nothing, so no button is drawn.
 */
@Composable
internal fun HighlightRow(
    row: HighlightRowModel,
    nowMillis: Long,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val palette = OmnibusTheme.palette
    val locale = LocalConfiguration.current.locales[0]
    val chapterPattern = stringResource(R.string.book_detail_highlights_chapter)
    val footer = remember(row.chapterNumber, row.createdAtMillis, nowMillis, locale, chapterPattern) {
        highlightFooter(
            chapterNumber = row.chapterNumber,
            createdAtMillis = row.createdAtMillis,
            nowMillis = nowMillis,
            locale = locale,
            timeZone = TimeZone.getDefault(),
            chapterLabel = { String.format(locale, chapterPattern, it) },
        )
    }
    val lineColor = palette.line

    Box(
        modifier
            .fillMaxWidth()
            .testTag(HighlightsTestTags.row(row.id))
            .semantics(mergeDescendants = true) {}
            .drawBehind {
                val rule = 1.dp.toPx()
                drawRect(lineColor, topLeft = Offset(0f, size.height - rule), size = Size(size.width, rule))
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)) {
            val text = row.text
            if (text != null) {
                Text(
                    text,
                    style = displayTextStyle(size = 22, color = palette.ink0, weight = FontWeight.Medium)
                        .copy(lineHeight = 1.34.em),
                    modifier = Modifier.padding(end = 30.dp).testTag(HighlightsTestTags.text(row.id)),
                )
            } else {
                Text(
                    stringResource(R.string.book_detail_highlights_no_text),
                    style = uiTextStyle(size = 15, weight = FontWeight.Normal, color = palette.ink1),
                    modifier = Modifier.padding(end = 30.dp).testTag(HighlightsTestTags.noText(row.id)),
                )
            }
            row.note?.let { note ->
                Text(
                    note,
                    style = uiTextStyle(size = 15, weight = FontWeight.Normal, color = palette.ink1),
                    modifier = Modifier.padding(top = 8.dp, end = 30.dp).testTag(HighlightsTestTags.note(row.id)),
                )
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag(HighlightsTestTags.footerRow(row.id)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (footer != null) {
                    Text(
                        footer,
                        style = uiTextStyle(size = 14, weight = FontWeight.Normal, color = palette.ink1),
                        modifier = Modifier.weight(1f).testTag(HighlightsTestTags.footer(row.id)),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                actions()
            }
        }
        HighlightRibbon(
            row.color,
            Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = (-1).dp).testTag(HighlightsTestTags.ribbon(row.id)),
        )
    }
}
