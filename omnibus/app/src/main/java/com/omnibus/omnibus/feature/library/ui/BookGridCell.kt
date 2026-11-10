package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.transition.BookCoverOrigin
import com.omnibus.omnibus.feature.bookdetail.transition.bookCoverSourceModifier
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.coverShadow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The cover identity for this book, with the first creator drawn on the plate and art only when a cover URL exists. */
internal fun Book.coverIdentity(): CoverIdentity = CoverIdentity(
    uuid = uuid,
    title = displayTitle,
    creator = creators.firstOrNull()?.name,
    accent = accent,
    hasCover = coverUrl != null,
)

/**
 * One book in the All books grid: cover, then title and author.
 *
 * The whole column, gaps included, is the tap target and merges into one accessibility node, so a
 * screen reader announces the title and author together. Callers add row spacing through [modifier]
 * before the click target, so the gap above a row also opens its book.
 *
 * @param width The cell width from [LibraryGridMetrics.cellWidth].
 * @param onClick Called once per tap anywhere on the cell.
 * @param subtitle Shown instead of the author when set, such as a series volume.
 */
@Composable
internal fun BookGridCell(
    book: Book,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier
            .testTag(LibraryTestTags.cell(book.id))
            .width(width)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        BookCellContent(book, subtitle, modifier = Modifier, captionModifier = Modifier)
    }
}

/**
 * The cover, title and subtitle of a book cell, as a column's children. [BookGridCell] wraps it with the tap target.
 *
 * A ghost copy of a cell (see [SeriesUnfoldGhost]) sets [isGhost], so it carries no source for the Book Details cover
 * morph and no test tags of the real cell.
 *
 * @param subtitle The line under the title, from [memberSubtitle] or the book's author.
 * @param modifier Applied to the cover's box after the source and tag.
 * @param captionModifier Applied to the title and subtitle column.
 * @param coverShadow Whether the cover draws its shadow.
 * @param isGhost Whether this is a ghost copy.
 */
@Composable
internal fun ColumnScope.BookCellContent(
    book: Book,
    subtitle: String?,
    modifier: Modifier,
    captionModifier: Modifier,
    coverShadow: Boolean = true,
    isGhost: Boolean = false,
) {
    val palette = OmnibusTheme.palette

    val source = if (isGhost) {
        Modifier
    } else {
        bookCoverSourceModifier(book.uuid, BookCoverOrigin.Grid).testTag(LibraryTestTags.cover(book.id))
    }
    Box(source.then(modifier)) {
        BookCover(
            identity = book.coverIdentity(),
            modifier = Modifier.fillMaxWidth().then(if (coverShadow) Modifier.coverShadow(5.dp) else Modifier),
            size = CoverSize.Md,
            cornerRadius = 5.dp,
        )
    }
    Column(captionModifier) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = book.displayTitle,
            style = uiTextStyle(12.5.sp, FontWeight.Medium, palette.ink0),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = if (isGhost) Modifier else Modifier.testTag(LibraryTestTags.title(book.id)),
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = subtitle ?: book.authorDisplay,
            style = if (subtitle != null) {
                uiTextStyle(10.sp, FontWeight.Medium, palette.ink3).copy(letterSpacing = 1.sp)
            } else {
                uiTextStyle(11.sp, FontWeight.Normal, palette.ink3)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The subtitle of a series member in the grid: its volume, when it has an index, else null for the author. */
@Composable
internal fun memberSubtitle(book: Book): String? =
    book.seriesIndex?.trim()?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.library_series_volume, it) }
