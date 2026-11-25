package com.omnibus.omnibus.feature.search.facet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.FacetKind
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.feature.search.SearchErrorState
import com.omnibus.omnibus.feature.search.SearchOpen
import com.omnibus.omnibus.feature.search.SearchRetryButton
import com.omnibus.omnibus.feature.search.SearchStateView
import com.omnibus.omnibus.feature.search.rows.SearchBookRow
import com.omnibus.omnibus.feature.search.rows.bookByline
import com.omnibus.omnibus.feature.search.rows.formatBadges
import com.omnibus.omnibus.feature.search.rows.rowDescription
import com.omnibus.omnibus.feature.search.searchColumnSide
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.components.OmnibusBackButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/** The "Books tagged" list for [route], opened from a tag row on Search or a tag chip on Book Details. */
@Composable
fun FacetBooksScreen(
    route: FacetBooksRoute,
    onBack: () -> Unit,
    onOpen: (SearchOpen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<FacetBooksViewModel, FacetBooksViewModel.Factory>(
        creationCallback = { it.create(route) },
    )
    val uiState by viewModel.uiState.collectAsState()
    FacetBooksScreenContent(
        route = route,
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMoreIfNeeded,
        onRetryNextPage = viewModel::retryNextPage,
        onOpen = onOpen,
        modifier = modifier,
    )
}

/**
 * The facet list for [uiState]: a back header with the tag's name, the count line, then the books in the same centred
 * column as Search. [onLoadMore] gets how many rows follow the last visible one, whenever that changes.
 */
@Composable
internal fun FacetBooksScreenContent(
    route: FacetBooksRoute,
    uiState: FacetBooksUIState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: (itemsAfterLastVisible: Int) -> Unit,
    onRetryNextPage: () -> Unit,
    onOpen: (SearchOpen) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val palette = OmnibusTheme.palette
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow null
            (info.totalItemsCount - 1 - last).coerceAtLeast(0)
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { currentOnLoadMore(it) }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(palette.bg0)) {
        val side = searchColumnSide(maxWidth)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = side, bottom = 24.dp),
        ) {
            item(key = "header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = Spacing.screen, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OmnibusBackButton(onClick = onBack)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        route.name,
                        style = uiTextStyle(20, FontWeight.Medium, palette.ink0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            when (val body = uiState.body) {
                is FacetBody.Loaded -> {
                    body.total?.let { total ->
                        item(key = "count") { CountLine(route, total) }
                    }
                    items(body.books, key = { it.id }) { book ->
                        FacetBookRow(book, onOpen)
                    }
                    when (body.nextPage) {
                        NextPageState.Idle -> Unit
                        NextPageState.Loading -> item(key = "next-page-loading") { NextPageLoading() }
                        NextPageState.Failed -> item(key = "next-page-failed") {
                            NextPageFailedRow(onRetryNextPage)
                        }
                    }
                }
                FacetBody.Loading -> item(key = "state") {
                    LoadingStateView(stringResource(R.string.search_loading), Modifier.padding(top = 52.dp))
                }
                FacetBody.Empty -> item(key = "state") {
                    SearchStateView(
                        glyph = R.drawable.ic_search_tag,
                        message = when (route.kind) {
                            FacetKind.Tag -> stringResource(R.string.search_facet_tag_empty_message, route.name)
                        },
                        modifier = Modifier.padding(top = 96.dp),
                        title = stringResource(R.string.search_facet_empty_title),
                    )
                }
                is FacetBody.Failed -> item(key = "state") {
                    SearchErrorState(body.failure, onRetry, Modifier.padding(top = 96.dp))
                }
                FacetBody.Ended -> Unit
            }
        }
    }
}

/** The count line under the heading, from `X-Total-Count`: "N books tagged “name”". */
@Composable
private fun CountLine(route: FacetBooksRoute, total: Long) {
    val palette = OmnibusTheme.palette
    // The count is a plural quantity, which is an Int; a header above Int.MAX_VALUE is clamped for it.
    val count = total.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    Text(
        pluralStringResource(countPlural(route.kind), count, count, route.name),
        style = uiTextStyle(15, FontWeight.Normal, palette.ink2),
        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = 12.dp),
    )
}

/** The count-line plural for [kind]. #353 adds its own kind here. */
private fun countPlural(kind: FacetKind): Int = when (kind) {
    FacetKind.Tag -> R.plurals.search_facet_tag_count
}

/** One book in the facet list: the same row as a Search book, with no highlighting. */
@Composable
private fun FacetBookRow(book: Book, onOpen: (SearchOpen) -> Unit) {
    val year = book.year?.toString()
    val badges = formatBadges(book.formats, book.hasPhysical)
    val badgeWords = badges.map { stringResource(it.label).lowercase(Locale.ROOT) }
    val kind = stringResource(R.string.search_row_book)
    SearchBookRow(
        title = AnnotatedString(book.displayTitle),
        byline = bookByline(book.authorDisplay, year),
        badges = badges,
        coverIdentity = book.coverIdentity(),
        description = rowDescription(listOf(kind, book.displayTitle, book.authorDisplay, year) + badgeWords),
        onClick = { onOpen(SearchOpen.Destination(BookDetailDestination.Book(book.uuid))) },
    )
}

/** The spinner at the end of the list while the next page is on its way. */
@Composable
private fun NextPageLoading() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = OmnibusTheme.palette.ink2,
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** The end-of-list row when a later page failed: the books above stay, and "Try again" asks for that page again. */
@Composable
private fun NextPageFailedRow(onRetry: () -> Unit) {
    val palette = OmnibusTheme.palette
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.search_facet_next_page_failed),
            style = uiTextStyle(15, FontWeight.Normal, palette.ink2),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        SearchRetryButton(stringResource(R.string.search_try_again), onRetry)
    }
}
