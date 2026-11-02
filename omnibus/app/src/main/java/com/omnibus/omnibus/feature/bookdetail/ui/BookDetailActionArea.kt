package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The area pinned above the shell's tab bar: the `actionBar` sections, or the interim Read pill. It draws its
 * own ground only while it shows something. The caller composes it only when [showsReadPill] or [sections] applies.
 */
@Composable
internal fun BookDetailActionArea(
    context: BookDetailSectionContext,
    sections: List<BookDetailSection>,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Column(
        modifier
            .fillMaxWidth()
            .testTag(BookDetailTestTags.ACTION_AREA)
            .drawBehind {
                drawRect(palette.bg0.copy(alpha = 0.74f))
                drawRect(palette.line.copy(alpha = 0.3f), size = Size(size.width, 1.dp.toPx()))
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (sections.isNotEmpty()) {
            sections.forEach { it.Content(context, Modifier.fillMaxWidth()) }
        } else {
            ReadPill(context.book, context.onNavigate)
        }
    }
}

/** Whether the interim Read pill shows: nothing fills the action bar yet and the book has an epub. */
internal fun showsReadPill(sections: List<BookDetailSection>, book: Book): Boolean =
    sections.isEmpty() && book.hasEpub

/**
 * The interim way into the reader, until #98 fills the `actionBar` slot. Once it does, this code is dead.
 */
@Composable
private fun ReadPill(book: Book, onNavigate: (BookDetailDestination) -> Unit) {
    val tone = book.coverIdentity().tone
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(Oklch(0.44, tone.c, tone.h).toColor())
            .clickable(role = Role.Button) { onNavigate(BookDetailDestination.Reader(book.uuid)) }
            .testTag(BookDetailTestTags.READ_PILL),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.book_detail_read),
            style = uiTextStyle(17, FontWeight.SemiBold, Color.White),
        )
    }
}
