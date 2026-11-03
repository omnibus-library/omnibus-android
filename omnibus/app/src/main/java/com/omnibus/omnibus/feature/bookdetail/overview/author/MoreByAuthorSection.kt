package com.omnibus.omnibus.feature.bookdetail.overview.author

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.overview.shelf.BookShelfCard
import com.omnibus.omnibus.feature.bookdetail.overview.shelf.BookShelfRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The shelf of the first author's other books. It renders nothing when that author has no id, before it creates its
 * ViewModel, so such a book needs no Hilt. It also renders nothing until the author has other books to show.
 */
internal object MoreByAuthorSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        val author = authorTargetOf(context.book) ?: return
        val viewModel = hiltViewModel<MoreByAuthorViewModel, MoreByAuthorViewModel.Factory>(
            key = "more-by-author-${context.book.uuid}-${author.id}",
            creationCallback = { it.create(author.id, context.book.id) },
        )
        LaunchedEffect(context.reloadKey) { viewModel.reload(context.reloadKey) }
        val state by viewModel.uiState.collectAsState()
        MoreByAuthor(
            authorName = author.name,
            books = state.books,
            onOpenAuthorPage = {
                context.onNavigate(BookDetailDestination.Author(author.id, author.name))
            },
            onOpenBook = { uuid -> context.onNavigate(BookDetailDestination.Book(uuid)) },
            modifier = modifier,
        )
    }
}

/**
 * The heading, the Author page link, and the shelf of [books]. Renders nothing for an empty [books]. The link is left
 * out when [onOpenAuthorPage] is null.
 */
@Composable
internal fun MoreByAuthor(
    authorName: String,
    books: List<AuthorShelfBook>,
    onOpenAuthorPage: (() -> Unit)?,
    onOpenBook: (uuid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (books.isEmpty()) return
    val palette = OmnibusTheme.palette
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 6.dp)
            .testTag(MoreByAuthorTestTags.SECTION),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.book_detail_author_heading, authorName),
                style = displayTextStyle(30, palette.ink0, FontWeight.SemiBold),
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
                    .testTag(MoreByAuthorTestTags.HEADING),
            )
            if (onOpenAuthorPage != null) {
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onOpenAuthorPage)
                        .testTag(MoreByAuthorTestTags.LINK),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.book_detail_author_link),
                        style = uiTextStyle(15, FontWeight.SemiBold, palette.ink1),
                    )
                    // The arrow is decoration; the link is announced by its label alone.
                    Text(
                        text = "›",
                        style = uiTextStyle(15, FontWeight.Bold, palette.ink1),
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
        val openLabel = stringResource(R.string.book_detail_author_open_book)
        BookShelfRow(
            content = {
                items(books, key = { it.uuid }) { book ->
                    BookShelfCard(
                        identity = book.identity,
                        title = book.title,
                        subtitle = book.year,
                        onClick = { onOpenBook(book.uuid) },
                        onClickLabel = openLabel,
                    )
                }
            },
        )
    }
}
