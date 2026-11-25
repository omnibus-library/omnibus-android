package com.omnibus.omnibus.feature.series.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.feature.search.rows.FormatBadges
import com.omnibus.omnibus.feature.search.rows.formatBadges
import com.omnibus.omnibus.feature.series.SeriesEntry
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.Locale

/**
 * A book's row: its series number, cover, title, authors and format badges, and a chevron. Tapping it calls [onClick].
 * It is one element for TalkBack, announcing the number, title, authors and formats.
 */
@Composable
internal fun BookRow(book: Book, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    val number = book.seriesIndex?.trim()?.takeIf { it.isNotEmpty() }
    val badges = formatBadges(book.formats, book.hasPhysical)
    val description = buildList {
        if (number != null) add(stringResource(R.string.series_details_a11y_book_number, number))
        add(book.displayTitle)
        add(book.authorDisplay)
        badges.forEach { add(stringResource(it.label).lowercase(Locale.ROOT)) }
    }.joinToString(", ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(SeriesDetailsTestTags.book(book.uuid))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberCell(
            text = number ?: stringResource(R.string.series_details_no_number),
            color = if (number != null) palette.accent else palette.ink2,
        )
        BookCover(
            identity = book.coverIdentity(),
            modifier = Modifier.width(38.dp),
            size = CoverSize.Sm,
            cornerRadius = 3.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = book.displayTitle,
                style = uiTextStyle(17, FontWeight.SemiBold, palette.ink0),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.authorDisplay,
                style = uiTextStyle(15, FontWeight.Normal, palette.ink2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FormatBadges(badges)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_reader_chevron_right),
            contentDescription = null,
            tint = palette.ink2,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * A placeholder for one missing number, or a run of them: a dashed cover and "Book n" or "Books a–b", with no tap action
 * and no chevron. It is one element for TalkBack.
 */
@Composable
internal fun MissingRow(entry: SeriesEntry.Missing, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    val single = entry.first == entry.last
    val title = if (single) {
        stringResource(R.string.series_details_missing_one, entry.first)
    } else {
        stringResource(R.string.series_details_missing_range, entry.first, entry.last)
    }
    val description = if (single) {
        stringResource(R.string.series_details_a11y_missing_one, entry.first)
    } else {
        stringResource(R.string.series_details_a11y_missing_range, entry.first, entry.last)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(SeriesDetailsTestTags.missing(entry.first))
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberCell(text = entry.first.toString(), color = palette.ink2)
        DashedCover(Modifier.width(38.dp).aspectRatio(2f / 3f))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = uiTextStyle(17, FontWeight.Normal, palette.ink2),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.series_details_missing_subtitle),
                style = uiTextStyle(15, FontWeight.Normal, palette.ink2),
            )
        }
    }
}

/** The fixed-width number column, so titles line up whatever the number's length. */
@Composable
private fun NumberCell(text: String, color: Color) {
    Text(
        text = text,
        modifier = Modifier.width(40.dp),
        style = monoTextStyle(14.sp, color),
        maxLines = 1,
    )
}

/** The dashed outline a placeholder shows where a cover would be. */
@Composable
private fun DashedCover(modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    Box(
        modifier.drawBehind {
            drawRoundRect(
                color = palette.ink2,
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                ),
            )
        },
    )
}
