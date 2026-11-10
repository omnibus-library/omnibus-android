package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.feature.library.SeriesGroup
import com.omnibus.omnibus.feature.library.SeriesUnfoldTiming
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.coverShadow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** How far each cover behind the front one is offset up and to the right, and how much narrower it is. */
private val STACK_STEP = SeriesUnfoldTiming.STACK_STEP_DP.dp

/**
 * A folded series in the All books grid: its top book in front, up to two more covers behind it, then the series
 * name and "N books · Author".
 *
 * The whole column is one node that reads the series name and count and unfolds on click, so a screen reader
 * announces it as one item.
 *
 * @param group The series to show. Its first book is on top.
 * @param width The cell width from [LibraryGridMetrics.cellWidth].
 * @param onUnfold Called once per tap anywhere on the cell.
 */
@Composable
internal fun SeriesStackCell(
    group: SeriesGroup,
    width: Dp,
    onUnfold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val unfoldLabel = stringResource(R.string.library_unfold_series)
    val countText = pluralStringResource(R.plurals.library_series_book_count, group.count, group.count)
    val description = stringResource(R.string.library_series_stack_description, group.name, countText)
    val frontWidth = width - STACK_STEP * 2

    Column(
        modifier = modifier
            .testTag(LibraryTestTags.stack(group.seriesId))
            .width(width)
            .clickable(onClickLabel = unfoldLabel, role = Role.Button, onClick = onUnfold)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            for (k in minOf(2, group.count - 1) downTo 1) {
                BookCover(
                    identity = group.books[k].coverIdentity(),
                    modifier = Modifier
                        .width(frontWidth)
                        .align(Alignment.BottomStart)
                        .offset(x = STACK_STEP * k, y = -STACK_STEP * k)
                        .dimmed(palette.bg0.copy(alpha = SeriesUnfoldTiming.VEIL_ALPHA)),
                    size = CoverSize.Md,
                    cornerRadius = 5.dp,
                )
            }
            BookCover(
                identity = group.top.coverIdentity(),
                modifier = Modifier.width(frontWidth).align(Alignment.BottomStart).coverShadow(5.dp),
                size = CoverSize.Md,
                cornerRadius = 5.dp,
            )
        }
        Spacer(Modifier.height(8.dp))
        SeriesStackCaption(group)
    }
}

/** The series name and "N books · Author" under a folded stack. Its texts are cleared from semantics. */
@Composable
internal fun SeriesStackCaption(group: SeriesGroup, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    val countText = pluralStringResource(R.plurals.library_series_book_count, group.count, group.count)
    val subtitle = stringResource(R.string.library_series_stack_subtitle, countText, group.author)

    Column(modifier) {
        Text(
            text = group.name,
            style = uiTextStyle(12.5.sp, FontWeight.Medium, palette.ink0),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = subtitle,
            style = uiTextStyle(11.sp, FontWeight.Normal, palette.ink3),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** Veils the content with [color] over its own bounds, so a cover behind the front one reads as receding. */
private fun Modifier.dimmed(color: Color) = drawWithContent {
    drawContent()
    drawRoundRect(color, cornerRadius = CornerRadius(5.dp.toPx()))
}
