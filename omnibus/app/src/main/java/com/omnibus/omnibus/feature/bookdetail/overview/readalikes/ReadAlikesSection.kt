package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.fetch.Fetcher
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.suggestions.BookSuggestion
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.overview.shelf.BookShelfRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The shelf of books that readers of this one also read, from Hardcover's curated lists. It renders nothing when the
 * book is wishlist-only or has no copy, before it creates its ViewModel, so such a book needs no Hilt.
 */
internal object ReadAlikesSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        if (!showsReadAlikes(context.book)) return
        val viewModel = hiltViewModel<ReadAlikesViewModel, ReadAlikesViewModel.Factory>(
            key = "read-alikes-${context.book.uuid}",
            creationCallback = { it.create(context.book.uuid) },
        )
        ReadAlikesContent(context, viewModel, modifier)
    }
}

/**
 * Runs [viewModel]'s load for each reload key while the section is composed, and draws its state. It is split out from
 * [ReadAlikesSection] so tests can pass a ViewModel built on a fake repository.
 */
@Composable
internal fun ReadAlikesContent(
    context: BookDetailSectionContext,
    viewModel: ReadAlikesViewModel,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(viewModel, context.reloadKey) { viewModel.load(context.reloadKey) }
    val state by viewModel.uiState.collectAsState()
    ReadAlikes(
        bookTitle = context.book.displayTitle,
        state = state,
        onOpen = { context.onNavigate(BookDetailDestination.ExternalUrl(it.hardcoverUrl)) },
        modifier = modifier,
        coverFetcher = viewModel.coverFetcher,
    )
}

/**
 * The heading, the caption, and the status line or shelf that [state] calls for. It draws nothing for
 * [ReadAlikesUiState.Hidden]. Searching and Unavailable share one status line, so the live region stays on screen when
 * the text changes and the change is announced.
 *
 * @param reducedMotion Whether the spinner stands still as a three-quarter ring. Defaults to the system setting.
 * @param coverFetcher Fetches the covers. Null draws the plates alone.
 */
@Composable
internal fun ReadAlikes(
    bookTitle: String,
    state: ReadAlikesUiState,
    onOpen: (ReadAlike) -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
    coverFetcher: Fetcher.Factory<SuggestionCoverRequest>? = null,
) {
    if (state == ReadAlikesUiState.Hidden) return
    val palette = OmnibusTheme.palette
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 6.dp)
            .testTag(ReadAlikesTestTags.SECTION),
    ) {
        Text(
            text = stringResource(R.string.book_detail_readalikes_heading, bookTitle),
            style = displayTextStyle(30, palette.ink0, FontWeight.SemiBold),
            modifier = Modifier.semantics { heading() }.testTag(ReadAlikesTestTags.HEADING),
        )
        Text(
            text = stringResource(R.string.book_detail_readalikes_source),
            style = uiTextStyle(14, FontWeight.Normal, palette.ink1),
            modifier = Modifier.padding(top = 6.dp).testTag(ReadAlikesTestTags.SOURCE),
        )
        when (state) {
            ReadAlikesUiState.Hidden -> Unit
            ReadAlikesUiState.Searching, ReadAlikesUiState.Unavailable -> StatusLine(
                spinning = state == ReadAlikesUiState.Searching,
                text = stringResource(
                    if (state == ReadAlikesUiState.Searching) {
                        R.string.book_detail_readalikes_searching
                    } else {
                        R.string.book_detail_readalikes_unavailable
                    },
                ),
                reducedMotion = reducedMotion,
                modifier = Modifier.padding(top = 14.dp),
            )
            is ReadAlikesUiState.Ready -> {
                val openLabel = stringResource(R.string.book_detail_readalikes_open)
                BookShelfRow(
                    content = {
                        items(state.items, key = { it.hardcoverId }) { item ->
                            SuggestionCard(
                                item = item,
                                onClick = { onOpen(item) },
                                onClickLabel = openLabel,
                                coverFetcher = coverFetcher,
                            )
                        }
                    },
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}

/**
 * The status line under the caption: a spinner while the server is searching, then one sentence. It is one merged node
 * with a polite live region, so the move from searching to unavailable is announced.
 */
@Composable
private fun StatusLine(spinning: Boolean, text: String, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    Row(
        modifier
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag(ReadAlikesTestTags.STATUS),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (spinning) {
            if (reducedMotion) {
                // A static three-quarter ring, so the spinner does not move for a reader who asked for no motion.
                CircularProgressIndicator(
                    progress = { 0.75f },
                    modifier = Modifier.size(16.dp).testTag(ReadAlikesTestTags.SPINNER),
                    color = palette.ink1,
                    strokeWidth = 2.dp,
                    trackColor = Color.Transparent,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp).testTag(ReadAlikesTestTags.SPINNER),
                    color = palette.ink1,
                    strokeWidth = 2.dp,
                )
            }
        }
        Text(
            text = text,
            style = uiTextStyle(15, FontWeight.Normal, palette.ink1),
            modifier = Modifier.testTag(ReadAlikesTestTags.STATUS_TEXT),
        )
    }
}

@PreviewLightDark
@Composable
private fun ReadAlikesSearchingPreview() {
    OmnibusTheme {
        Box(Modifier.padding(horizontal = 16.dp)) {
            ReadAlikes(
                bookTitle = "The Dispossessed",
                state = ReadAlikesUiState.Searching,
                onOpen = {},
                reducedMotion = true,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ReadAlikesUnavailablePreview() {
    OmnibusTheme {
        Box(Modifier.padding(horizontal = 16.dp)) {
            ReadAlikes(
                bookTitle = "The Dispossessed",
                state = ReadAlikesUiState.Unavailable,
                onOpen = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ReadAlikesReadyPreview() {
    val shelf = readAlikesOf(
        listOf(
            BookSuggestion(1, "https://hardcover.app/books/a", "Piranesi", "Susanna Clarke", null),
            BookSuggestion(
                2,
                "https://hardcover.app/books/b",
                "A Very Long Title That Keeps Going Well Past Two Lines Of Card Text",
                "Madeline Miller",
                null,
            ),
            BookSuggestion(3, "https://hardcover.app/books/c", "Circe", "Ursula K. Le Guin", null),
        ),
    )
    OmnibusTheme {
        Box(Modifier.padding(horizontal = 16.dp)) {
            ReadAlikes(
                bookTitle = "The Dispossessed",
                state = ReadAlikesUiState.Ready(shelf),
                onOpen = {},
            )
        }
    }
}
