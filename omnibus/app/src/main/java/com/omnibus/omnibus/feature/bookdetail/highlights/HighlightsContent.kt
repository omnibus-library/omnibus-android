package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.components.StateActionButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The Highlights list: the heading with its count, then the content for the state. Rows compose a page at a time as the
 * reader scrolls, because the host is a plain scrolling column. [rowActions] fills each row's footer and is empty until #111.
 */
@Composable
internal fun HighlightsContent(
    state: HighlightsUIState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    rowActions: @Composable RowScope.(HighlightRowModel) -> Unit = {},
) {
    val palette = OmnibusTheme.palette
    val content = state.content
    val rows = (content as? HighlightsUIContent.Loaded)?.rows.orEmpty()

    Column(modifier.fillMaxWidth().testTag(HighlightsTestTags.SECTION)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.book_detail_highlights_heading),
                style = displayTextStyle(size = 30, color = palette.ink0, weight = FontWeight.SemiBold),
                modifier = Modifier
                    .alignByBaseline()
                    .semantics { heading() }
                    .testTag(HighlightsTestTags.HEADING),
            )
            if (content is HighlightsUIContent.Loaded && rows.isNotEmpty()) {
                Text(
                    rows.size.toString(),
                    style = uiTextStyle(size = 17, weight = FontWeight.Medium, color = palette.ink2),
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = 4.dp)
                        .testTag(HighlightsTestTags.COUNT),
                )
            }
        }

        when (content) {
            HighlightsUIContent.Loading -> Unit
            HighlightsUIContent.Failed -> {
                Text(
                    stringResource(R.string.book_detail_highlights_failed),
                    style = uiTextStyle(size = 16, weight = FontWeight.Normal, color = palette.ink1),
                    modifier = Modifier.padding(top = 8.dp).testTag(HighlightsTestTags.FAILED),
                )
                StateActionButton(
                    label = stringResource(R.string.book_detail_highlights_retry),
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 12.dp).testTag(HighlightsTestTags.RETRY),
                )
            }
            is HighlightsUIContent.Loaded -> {
                if (rows.isEmpty()) {
                    Text(
                        stringResource(R.string.book_detail_highlights_empty),
                        style = uiTextStyle(size = 16, weight = FontWeight.Normal, color = palette.ink1)
                            .copy(lineHeight = 1.45.em),
                        modifier = Modifier.padding(top = 8.dp).testTag(HighlightsTestTags.EMPTY),
                    )
                } else {
                    HighlightsList(rows, state.nowMillis, rowActions)
                }
            }
        }
    }
}

/**
 * The rows, composed a page at a time. When the end of the composed rows comes within one window height of the bottom
 * of the window, the next page is composed. Scrolling the page moves the sentinel, so composition follows the reader.
 */
@Composable
private fun HighlightsList(
    rows: List<HighlightRowModel>,
    nowMillis: Long,
    rowActions: @Composable RowScope.(HighlightRowModel) -> Unit,
) {
    var shown by rememberSaveable { mutableIntStateOf(HIGHLIGHTS_PAGE) }
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height.takeIf { it > 0 }
        ?: with(density) { LocalConfiguration.current.screenHeightDp.dp.roundToPx() }

    rows.take(shown).forEach { row ->
        key(row.id) {
            HighlightRow(row, nowMillis, actions = { rowActions(row) })
        }
    }
    if (shown < rows.size) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .testTag(HighlightsTestTags.MORE)
                .onGloballyPositioned { coordinates ->
                    if (coordinates.isAttached && coordinates.positionInWindow().y <= windowHeightPx * 2f) {
                        shown = nextShownCount(shown, rows.size)
                    }
                },
        )
    }
}
