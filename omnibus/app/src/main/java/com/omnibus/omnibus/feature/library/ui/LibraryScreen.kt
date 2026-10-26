package com.omnibus.omnibus.feature.library.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.library.LibraryGridMetrics
import com.omnibus.omnibus.feature.library.LibraryScreenState
import com.omnibus.omnibus.feature.library.LibraryUIState
import com.omnibus.omnibus.feature.library.LibraryViewModel
import com.omnibus.omnibus.ui.components.EmptyStateView
import com.omnibus.omnibus.ui.components.ErrorStateView
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.components.Masthead
import com.omnibus.omnibus.ui.components.MastheadAddButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Test tags for the Library screen's grid, heading and cells, so UI tests can find them. */
internal object LibraryTestTags {
    const val GRID = "library-grid"
    const val HEADING = "library-heading"
    const val FOOTER_SPINNER = "library-footer-spinner"

    fun cell(id: Long) = "library-cell-$id"
    fun cover(id: Long) = "library-cover-$id"
    fun title(id: Long) = "library-title-$id"
}

private const val HEADER_KEY = "header"
private const val FOOTER_KEY = "footer"

/** The masthead and heading occupy grid item 0, so book indices start at 1. */
private const val HEADER_ITEM_COUNT = 1

/**
 * The Library tab's root: the All books grid, with its loading, error and empty states.
 *
 * Stateful wrapper that feeds [LibraryScreenContent] from [LibraryViewModel]. The grid state is
 * hoisted here so the saveable-state decorator restores scroll on back.
 *
 * @param onOpenBook Called with the book's uuid when a cell is tapped.
 * @param onAddBooks Called when the masthead's add button is tapped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    onOpenBook: (uuid: String) -> Unit,
    onAddBooks: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    // The shell consumes the navigation bar above the tab bar. Only the part it did not consume
    // is added as bottom padding here, so the last row clears the tab bar without a doubled inset.
    var consumed by remember { mutableStateOf(WindowInsets(0)) }
    val bottomInset = WindowInsets.navigationBars.exclude(consumed).asPaddingValues().calculateBottomPadding()

    LibraryScreenContent(
        uiState = uiState,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMoreIfNeeded,
        onOpenBook = onOpenBook,
        onAddBooks = onAddBooks,
        onTransientErrorShown = viewModel::transientErrorShown,
        bottomInset = bottomInset,
        modifier = modifier.onConsumedWindowInsetsChanged { consumed = it },
        gridState = gridState,
    )
}

/**
 * The Library screen's state-driven layout, without the ViewModel.
 *
 * Internal rather than private so the instrumented tests can render each state with hand-built
 * [LibraryUIState]s.
 *
 * @param onLoadMore Called with the index of the last visible book whenever it changes.
 * @param bottomInset Navigation bar padding the caller could not consume, added below the grid.
 * @param gridState The grid's scroll state. Hoist it so scroll survives leaving the screen.
 * @param snackbarHostState Hosts the transient error snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreenContent(
    uiState: LibraryUIState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: (lastVisibleBookIndex: Int) -> Unit,
    onOpenBook: (uuid: String) -> Unit,
    onAddBooks: () -> Unit,
    onTransientErrorShown: () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scope = rememberCoroutineScope()

    // Clearing the message restarts this effect with null, so the snackbar is launched in a scope
    // that outlives the effect.
    LaunchedEffect(uiState.transientError) {
        val message = uiState.transientError ?: return@LaunchedEffect
        onTransientErrorShown()
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Box(modifier.fillMaxSize()) {
        when (val screen = uiState.screen) {
            LibraryScreenState.Loading -> LoadingStateView(
                label = stringResource(R.string.library_loading),
                modifier = Modifier.fillMaxSize(),
            )

            is LibraryScreenState.Error -> ErrorStateView(
                title = stringResource(R.string.library_error_title),
                message = screen.message,
                actionLabel = stringResource(R.string.library_try_again),
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )

            LibraryScreenState.Empty -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                EmptyLibrary()
            }

            LibraryScreenState.Content -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LibraryGrid(
                    uiState = uiState,
                    onOpenBook = onOpenBook,
                    onAddBooks = onAddBooks,
                    onLoadMore = onLoadMore,
                    gridState = gridState,
                    bottomInset = bottomInset,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        )
    }
}

/**
 * The empty state inside a scroll container, so a pull gesture still reaches the refresh box.
 */
@Composable
private fun EmptyLibrary() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxHeight
        Column(Modifier.verticalScroll(rememberScrollState())) {
            EmptyStateView(
                title = stringResource(R.string.library_empty_title),
                message = stringResource(R.string.library_empty_message),
                modifier = Modifier.height(height).fillMaxWidth(),
                kicker = stringResource(R.string.library_empty_kicker),
            )
        }
    }
}

/**
 * The masthead, the "All books" heading and the cover grid, as one scrolling surface.
 *
 * The column count and cell width come from [LibraryGridMetrics] for the width left after the
 * screen insets. Reports the last visible book index to [onLoadMore], which maps the grid index back
 * past the header and clamps it to the last book.
 */
@Composable
private fun LibraryGrid(
    uiState: LibraryUIState,
    onOpenBook: (uuid: String) -> Unit,
    onAddBooks: () -> Unit,
    onLoadMore: (lastVisibleBookIndex: Int) -> Unit,
    gridState: LazyGridState,
    bottomInset: Dp,
) {
    val palette = OmnibusTheme.palette

    LaunchedEffect(gridState, uiState.books.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filterNotNull()
            .map { (it - HEADER_ITEM_COUNT).coerceAtMost(uiState.books.lastIndex) }
            .filter { it >= 0 }
            .distinctUntilChanged()
            .collect { onLoadMore(it) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val available = maxWidth - Spacing.screen * 2
        val columns = LibraryGridMetrics.columns(available.value)
        val cellWidth = LibraryGridMetrics.cellWidth(available.value).dp

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                bottom = 40.dp + bottomInset,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().testTag(LibraryTestTags.GRID),
        ) {
            item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) {
                // The masthead and heading carry their own 20 dp inset, so the header undoes the grid's padding.
                Column(Modifier.bleedHorizontally(Spacing.screen)) {
                    Masthead(title = stringResource(R.string.library_title)) {
                        MastheadAddButton(onAddBooks = onAddBooks)
                    }
                    Column(Modifier.padding(horizontal = Spacing.screen)) {
                        Text(
                            text = stringResource(R.string.library_all_books),
                            style = displayTextStyle(27, palette.ink0),
                            modifier = Modifier.testTag(LibraryTestTags.HEADING),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            itemsIndexed(uiState.books, key = { _, book -> book.id }) { index, book ->
                BookGridCell(
                    book = book,
                    width = cellWidth,
                    onClick = { onOpenBook(book.uuid) },
                    // Rows after the first get 26 dp above them, from the cells, so the heading gap stays 12 dp.
                    modifier = Modifier.padding(top = if (index >= columns) 26.dp else 0.dp),
                )
            }

            if (uiState.isLoadingMore) {
                item(key = FOOTER_KEY, span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 26.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = palette.ink3,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp).testTag(LibraryTestTags.FOOTER_SPINNER),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Widens the measured width by [inset] on each side and places the content back at the edge, so a
 * child can draw full-bleed while its own padding keeps the content inset.
 */
private fun Modifier.bleedHorizontally(inset: Dp) = layout { measurable, constraints ->
    val extra = (inset * 2).roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = extra))
    layout(placeable.width - extra, placeable.height) {
        placeable.place(-extra / 2, 0)
    }
}

private fun previewBook(id: Long, title: String, author: String) = Book(
    id = id,
    uniqueIdentifier = "preview-$id",
    filename = "$id.epub",
    title = title,
    creators = listOf(Creator(name = author, role = null, fileAs = null, id = null)),
    coverUrl = null,
    accent = null,
    formats = listOf("epub"),
    series = null,
    seriesIndex = null,
    hasPhysical = false,
    addedAt = null,
    lastInteractedAt = null,
    bookFiles = emptyList(),
)

private val previewBooks = listOf(
    previewBook(1, "The Left Hand of Darkness", "Ursula K. Le Guin"),
    previewBook(2, "Piranesi", "Susanna Clarke"),
    previewBook(3, "A Memory Called Empire", "Arkady Martine"),
    previewBook(4, "The Long Way to a Small, Angry Planet: A Novel of the Wayfarers", "Becky Chambers"),
    previewBook(5, "Network Effect", "Martha Wells"),
    previewBook(6, "Tomorrow, and Tomorrow, and Tomorrow", "Gabrielle Zevin"),
)

@Composable
private fun LibraryPreviewContent(uiState: LibraryUIState) {
    OmnibusTheme {
        LibraryScreenContent(
            uiState = uiState,
            onRetry = {},
            onRefresh = {},
            onLoadMore = {},
            onOpenBook = {},
            onAddBooks = {},
            onTransientErrorShown = {},
            bottomInset = 0.dp,
        )
    }
}

@PreviewLightDark
@Composable
private fun LibraryContentPreview() {
    LibraryPreviewContent(LibraryUIState(books = previewBooks, nextCursor = "c2"))
}

@Preview(name = "Light", widthDp = 840, heightDp = 900)
@Preview(name = "Dark", widthDp = 840, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryContentWidePreview() {
    LibraryPreviewContent(LibraryUIState(books = previewBooks, nextCursor = "c2"))
}

@PreviewLightDark
@Composable
private fun LibraryLoadingPreview() {
    LibraryPreviewContent(LibraryUIState(isInitialLoading = true))
}

@PreviewLightDark
@Composable
private fun LibraryErrorPreview() {
    LibraryPreviewContent(LibraryUIState(error = "Could not connect to the server.", hasLoaded = true))
}

@PreviewLightDark
@Composable
private fun LibraryEmptyPreview() {
    LibraryPreviewContent(LibraryUIState(hasLoaded = true))
}

@PreviewLightDark
@Composable
private fun LibraryLoadingMorePreview() {
    LibraryPreviewContent(LibraryUIState(books = previewBooks, nextCursor = "c2", isLoadingMore = true))
}
