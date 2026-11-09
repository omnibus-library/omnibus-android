package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.library.SeriesGroup
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The cell at the head of an unfolded series: the kicker, name, book count and author, then "Series page →" and
 * "Fold up". It is as tall as a cover, so the captions of its row line up with its neighbours.
 *
 * The texts and both buttons stay separate nodes, so each button is reachable on its own.
 *
 * @param group The unfolded series.
 * @param width The cell width from [LibraryGridMetrics.cellWidth].
 * @param onOpenSeries Called when "Series page →" is tapped.
 * @param onFoldUp Called when "Fold up" is tapped.
 */
@Composable
internal fun SeriesSummaryCell(
    group: SeriesGroup,
    width: Dp,
    onOpenSeries: () -> Unit,
    onFoldUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette

    Column(modifier.testTag(LibraryTestTags.seriesSummary(group.seriesId)).width(width)) {
        Column(Modifier.fillMaxWidth().aspectRatio(2f / 3f), verticalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(R.string.library_series_kicker),
                style = uiTextStyle(9.sp, FontWeight.Medium, palette.ink3).copy(letterSpacing = 1.5.sp),
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = group.name,
                style = displayTextStyle(16, palette.ink0),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = pluralStringResource(R.plurals.library_series_in_library, group.count, group.count),
                style = uiTextStyle(10.5.sp, FontWeight.Normal, palette.ink2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = group.author,
                style = uiTextStyle(10.5.sp, FontWeight.Normal, palette.ink2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            SeriesActionButton(
                label = stringResource(R.string.library_series_page),
                tag = LibraryTestTags.seriesPage(group.seriesId),
                filled = true,
                onClick = onOpenSeries,
            )
            Spacer(Modifier.height(6.dp))
            SeriesActionButton(
                label = stringResource(R.string.library_series_fold_up),
                tag = LibraryTestTags.foldUp(group.seriesId),
                filled = false,
                onClick = onFoldUp,
            )
        }
    }
}

/** A full-width pill button: accent-filled when [filled], otherwise outlined. */
@Composable
private fun SeriesActionButton(label: String, tag: String, filled: Boolean, onClick: () -> Unit) {
    val palette = OmnibusTheme.palette
    val shape = CircleShape
    val base = Modifier.fillMaxWidth().height(28.dp).clip(shape)
    Box(
        modifier = (if (filled) base.background(palette.accent) else base.border(0.5.dp, palette.line2, shape))
            .testTag(tag)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = uiTextStyle(11.sp, FontWeight.SemiBold, if (filled) palette.accentInk else palette.ink1),
            maxLines = 1,
        )
    }
}
