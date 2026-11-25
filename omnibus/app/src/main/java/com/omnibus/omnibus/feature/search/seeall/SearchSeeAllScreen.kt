package com.omnibus.omnibus.feature.search.seeall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.search.AuthorMatch
import com.omnibus.omnibus.data.search.GenreMatch
import com.omnibus.omnibus.data.search.LibraryHit
import com.omnibus.omnibus.data.search.TagMatch
import com.omnibus.omnibus.feature.search.SearchErrorState
import com.omnibus.omnibus.feature.search.SearchOpen
import com.omnibus.omnibus.feature.search.SearchSectionKind
import com.omnibus.omnibus.feature.search.SearchStateView
import com.omnibus.omnibus.feature.search.rows.LibraryHitRow
import com.omnibus.omnibus.feature.search.rows.SearchAuthorRow
import com.omnibus.omnibus.feature.search.rows.SearchGenreRow
import com.omnibus.omnibus.feature.search.rows.SearchTagRow
import com.omnibus.omnibus.feature.search.searchColumnSide
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.components.OmnibusBackButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The See all list for [route]: every match of its section, opened from the Search tab's section header. */
@Composable
fun SearchSeeAllScreen(
    route: SearchSeeAllRoute,
    onBack: () -> Unit,
    onOpen: (SearchOpen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<SearchSeeAllViewModel, SearchSeeAllViewModel.Factory>(
        creationCallback = { it.create(route) },
    )
    val uiState by viewModel.uiState.collectAsState()
    SearchSeeAllScreenContent(
        route = route,
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onOpen = onOpen,
        modifier = modifier,
    )
}

/** The See all list for [uiState]: a back header, the count line, then the rows in the same centred column as Search. */
@Composable
internal fun SearchSeeAllScreenContent(
    route: SearchSeeAllRoute,
    uiState: SearchSeeAllUIState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (SearchOpen) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val palette = OmnibusTheme.palette
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
                        stringResource(route.section.seeAllTitle, route.query),
                        style = uiTextStyle(20, FontWeight.Medium, palette.ink0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            when (val body = uiState.body) {
                is SeeAllBody.Loaded -> {
                    val total = body.matches.total
                    item(key = "count") {
                        Text(
                            if (total > SEE_ALL_CAP) {
                                stringResource(R.string.search_see_all_capped, total)
                            } else {
                                pluralStringResource(route.section.seeAllCount, total, total)
                            },
                            style = uiTextStyle(15, FontWeight.Normal, palette.ink2),
                            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = 12.dp),
                        )
                    }
                    when (route.section) {
                        SearchSectionKind.Books -> items(
                            body.matches.items.filterIsInstance<LibraryHit>(),
                            key = { it.uuid },
                        ) { hit ->
                            LibraryHitRow(hit, route.query, onOpen)
                        }
                        SearchSectionKind.Tags -> itemsIndexed(
                            body.matches.items.filterIsInstance<TagMatch>(),
                            key = { index, _ -> "tag-$index" },
                        ) { _, tag ->
                            SearchTagRow(tag, route.query, onOpen)
                        }
                        SearchSectionKind.Authors -> itemsIndexed(
                            body.matches.items.filterIsInstance<AuthorMatch>(),
                            key = { index, _ -> "author-$index" },
                        ) { _, author ->
                            SearchAuthorRow(author, route.query, onOpen)
                        }
                        SearchSectionKind.Genres -> itemsIndexed(
                            body.matches.items.filterIsInstance<GenreMatch>(),
                            key = { index, _ -> "genre-$index" },
                        ) { _, genre ->
                            SearchGenreRow(genre, route.query, onOpen)
                        }
                    }
                }
                SeeAllBody.Loading -> item(key = "state") {
                    LoadingStateView(stringResource(R.string.search_loading), Modifier.padding(top = 52.dp))
                }
                SeeAllBody.NoMatches -> item(key = "state") {
                    SearchStateView(
                        glyph = R.drawable.ic_search_no_matches,
                        message = stringResource(R.string.search_no_matches_message, route.query),
                        modifier = Modifier.padding(top = 96.dp),
                        title = stringResource(R.string.search_no_matches_title),
                    )
                }
                is SeeAllBody.Failed -> item(key = "state") {
                    SearchErrorState(body.failure, onRetry, Modifier.padding(top = 96.dp))
                }
                SeeAllBody.Ended -> Unit
            }
        }
    }
}
