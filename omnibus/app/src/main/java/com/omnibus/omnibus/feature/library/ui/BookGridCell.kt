package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.library.Book
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
 */
@Composable
internal fun BookGridCell(
    book: Book,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette

    Column(
        modifier = modifier
            .testTag(LibraryTestTags.cell(book.id))
            .width(width)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        Box(Modifier.testTag(LibraryTestTags.cover(book.id))) {
            BookCover(
                identity = book.coverIdentity(),
                modifier = Modifier.fillMaxWidth().coverShadow(5.dp),
                size = CoverSize.Md,
                cornerRadius = 5.dp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = book.displayTitle,
            style = uiTextStyle(12.5.sp, FontWeight.Medium, palette.ink0),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(LibraryTestTags.title(book.id)),
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = book.authorDisplay,
            style = uiTextStyle(11.sp, FontWeight.Normal, palette.ink3),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
