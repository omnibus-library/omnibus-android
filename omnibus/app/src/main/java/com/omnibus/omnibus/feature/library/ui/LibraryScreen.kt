package com.omnibus.omnibus.feature.library.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.omnibus.omnibus.data.library.LibraryCategory
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.LibrarySort
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.SortDirection
import com.omnibus.omnibus.feature.bookdetail.transition.BookCoverOrigin
import com.omnibus.omnibus.feature.bookdetail.transition.rememberBookCoverOpener
import com.omnibus.omnibus.feature.library.ContinueCard
import com.omnibus.omnibus.feature.library.LibraryGridMetrics
import com.omnibus.omnibus.feature.library.LibraryScreenState
import com.omnibus.omnibus.feature.library.LibraryUIError
import com.omnibus.omnibus.feature.library.LibraryUIState
import com.omnibus.omnibus.feature.library.LibraryViewModel
import com.omnibus.omnibus.feature.library.PositionLabel
import com.omnibus.omnibus.feature.library.RelativeAge
import com.omnibus.omnibus.feature.library.ResumeTarget
import com.omnibus.omnibus.feature.library.UnavailableBook
import com.omnibus.omnibus.ui.components.EdgeFade
import com.omnibus.omnibus.ui.components.EmptyStateView
import com.omnibus.omnibus.ui.components.ErrorStateView
import com.omnibus.omnibus.ui.components.FadeEdge
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.components.Masthead
import com.omnibus.omnibus.ui.components.MastheadAddButton
import com.omnibus.omnibus.ui.components.OfflinePill
import com.omnibus.omnibus.ui.components.bottomOverlay
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
    const val FILTER_BUTTON = "library-filter-button"
    const val MENU_DIRECTION = "library-menu-direction"

    fun cell(id: Long) = "library-cell-$id"
    fun cover(id: Long) = "library-cover-$id"
    fun title(id: Long) = "library-title-$id"
    fun menuCategory(category: LibraryCategory) = "library-menu-category-${category.name}"
    fun menuSort(sort: LibrarySort) = "library-menu-sort-${sort.name}"
    fun directionIcon(direction: SortDirection) = "library-menu-direction-icon-${direction.name}"
}

private const val HEADER_KEY = "header"
private const val FOOTER_KEY = "footer"

/** The masthead and heading occupy grid item 0, so book indices start at 1. */
private const val HEADER_ITEM_COUNT = 1

/**
 * The scrim at the top of the window covers the status bar plus [TOP_SCRIM_EXTRA]. Its top [TOP_SCRIM_SOLID] of the
 * status bar holds [TOP_SCRIM_PEAK] behind the icons and the rest eases to clear, so the grid shows through as it
 * scrolls under the bar.
 */
private const val TOP_SCRIM_SOLID = 0.4f
private const val TOP_SCRIM_PEAK = 0.7f
private val TOP_SCRIM_EXTRA = 12.dp

/** The space below the last row, on top of the bottom overlay (#11). */
private val GRID_BOTTOM_GAP = 40.dp

/**
 * The Library tab's root: the All books grid, with its loading, error and empty states.
 *
 * Stateful wrapper that feeds [LibraryScreenContent] from [LibraryViewModel]. The grid state is
 * hoisted here so the saveable-state decorator restores scroll on back. It fills the window: the grid scrolls under
 * the status bar and the shell's bottom overlay, behind a `bg0` scrim over the status bar.
 *
 * @param onOpenBook Called with the book's uuid when a cell is tapped.
 * @param onAddBooks Called when the masthead's add button is tapped.
 * @param onOpenReader Called with the book's uuid when a Continue card resumes reading.
 * @param onOpenPlayer Called with the book's uuid and saved file id when a Continue card resumes listening.
 */
@Composable
fun LibraryScreen(
    onOpenBook: (uuid: String) -> Unit,
    onAddBooks: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenReader: (bookUuid: String) -> Unit = {},
    onOpenPlayer: (bookUuid: String, bookFileId: Long?) -> Unit = { _, _ -> },
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    val continueState = rememberContinueCarouselState(cardCount = { uiState.continueCards.size })

    // Runs each time the entry is composed again, so returning from the reader, the player or detail reloads the cards.
    LaunchedEffect(Unit) { viewModel.libraryShown() }

    LibraryScreenContent(
        uiState = uiState,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMoreIfNeeded,
        onOpenBook = onOpenBook,
        onAddBooks = onAddBooks,
        onTransientErrorShown = viewModel::transientErrorShown,
        modifier = modifier,
        onSelectCategory = viewModel::selectCategory,
        onSelectSort = viewModel::selectSort,
        onToggleDirection = viewModel::toggleDirection,
        onScrolledToTop = viewModel::scrolledToTop,
        gridState = gridState,
        continueState = continueState,
        onContinueSettled = viewModel::continueCardSettled,
        onResume = viewModel::resume,
        onOpenReader = onOpenReader,
        onOpenPlayer = onOpenPlayer,
        onResumeHandled = viewModel::resumeHandled,
        onUnavailableDismissed = viewModel::unavailableDismissed,
    )
}

/**
 * The Library screen's state-driven layout, without the ViewModel.
 *
 * Internal rather than private so the instrumented tests can render each state with hand-built
 * [LibraryUIState]s.
 *
 * @param onLoadMore Called with the index of the last visible book whenever it changes.
 * @param onSelectCategory Called with the category picked in the filter menu.
 * @param onSelectSort Called with the sort key picked in the filter menu.
 * @param onToggleDirection Called when the Direction row is tapped.
 * @param onScrolledToTop Called once the grid has scrolled to its first item for a query change.
 * @param gridState The grid's scroll state. Hoist it so scroll survives leaving the screen.
 * @param snackbarHostState Hosts the transient error snackbar.
 * @param continueState The Continue carousel's pager and draw-in state. Hoist it outside the grid.
 * @param onContinueSettled Called with the card id the carousel settled on.
 * @param onResume Called with the card id when a Continue card is resumed.
 * @param onShowContextMenu Called on a long press of a Continue card. A no-op until the book menu lands.
 * @param onOpenReader Called with the uuid when a resume opens the reader.
 * @param onOpenPlayer Called with the uuid and saved file id when a resume opens the player stub.
 * @param onResumeHandled Called once a pending resume has been passed to [onOpenReader] or [onOpenPlayer].
 * @param onUnavailableDismissed Called when the "can't open offline" alert is dismissed.
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
    modifier: Modifier = Modifier,
    onSelectCategory: (LibraryCategory) -> Unit = {},
    onSelectSort: (LibrarySort) -> Unit = {},
    onToggleDirection: () -> Unit = {},
    onScrolledToTop: () -> Unit = {},
    gridState: LazyGridState = rememberLazyGridState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    continueState: ContinueCarouselState = rememberContinueCarouselState(cardCount = { uiState.continueCards.size }),
    onContinueSettled: (cardId: String) -> Unit = {},
    onResume: (cardId: String) -> Unit = {},
    onShowContextMenu: (Book) -> Unit = {},
    onOpenReader: (bookUuid: String) -> Unit = {},
    onOpenPlayer: (bookUuid: String, bookFileId: Long?) -> Unit = { _, _ -> },
    onResumeHandled: () -> Unit = {},
    onUnavailableDismissed: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    val transientMessage = uiState.transientError?.let { libraryErrorText(it) }

    // Clearing the message restarts this effect with null, so the snackbar is launched in a scope
    // that outlives the effect.
    LaunchedEffect(uiState.transientError) {
        val message = transientMessage ?: return@LaunchedEffect
        onTransientErrorShown()
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Keyed on the one-shot flag, not a counter, so returning from a book can't re-fire it (#17 AC8).
    LaunchedEffect(uiState.scrollToTop) {
        if (uiState.scrollToTop) {
            gridState.scrollToItem(0)
            onScrolledToTop()
        }
    }

    // A pending resume goes to the reader or the player once, then is cleared, so a recomposition can't open it twice.
    LaunchedEffect(uiState.pendingResume) {
        when (val target = uiState.pendingResume ?: return@LaunchedEffect) {
            is ResumeTarget.Reader -> onOpenReader(target.bookUuid)
            is ResumeTarget.Player -> onOpenPlayer(target.bookUuid, target.bookFileId)
        }
        onResumeHandled()
    }

    // The grid runs under the status bar and the bottom overlay. The other states and the snackbar keep clear of both.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val overlay = WindowInsets.bottomOverlay
    val overlayBottom = overlay.asPaddingValues().calculateBottomPadding()
    val sides = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    val stateInsets = WindowInsets.statusBars.union(overlay).union(sides)

    Box(modifier.fillMaxSize()) {
        when (val screen = uiState.screen) {
            LibraryScreenState.Loading -> LoadingStateView(
                label = stringResource(R.string.library_loading),
                modifier = Modifier.fillMaxSize().windowInsetsPadding(stateInsets),
            )

            is LibraryScreenState.Error -> ErrorStateView(
                title = stringResource(R.string.library_error_title),
                message = libraryErrorText(screen.error),
                actionLabel = stringResource(R.string.library_try_again),
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(stateInsets),
            )

            LibraryScreenState.Empty -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(stateInsets),
            ) {
                EmptyLibrary()
            }

            LibraryScreenState.Content -> {
                val refreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                    state = refreshState,
                    // The default indicator sits at the top of the box, which here is under the status bar.
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = refreshState,
                            isRefreshing = uiState.isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = statusTop),
                        )
                    },
                ) {
                    LibraryGrid(
                        uiState = uiState,
                        onOpenBook = onOpenBook,
                        onAddBooks = onAddBooks,
                        onLoadMore = onLoadMore,
                        onSelectCategory = onSelectCategory,
                        onSelectSort = onSelectSort,
                        onToggleDirection = onToggleDirection,
                        gridState = gridState,
                        topInset = statusTop,
                        bottomInset = overlayBottom,
                        modifier = Modifier.windowInsetsPadding(sides),
                        continueState = continueState,
                        onContinueSettled = onContinueSettled,
                        onResume = onResume,
                        onShowContextMenu = onShowContextMenu,
                    )
                    // Keeps the status bar icons legible over the content scrolling under them. Drawn after the grid, so
                    // the indicator (drawn after the content) stays above it.
                    EdgeFade(
                        FadeEdge.Top,
                        solid = statusTop * TOP_SCRIM_SOLID,
                        fade = statusTop * (1f - TOP_SCRIM_SOLID) + TOP_SCRIM_EXTRA,
                        modifier = Modifier.align(Alignment.TopCenter),
                        peak = TOP_SCRIM_PEAK,
                    )
                }
            }
        }

        uiState.unavailableBook?.let { ContinueUnavailableDialog(book = it, onDismiss = onUnavailableDismissed) }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(overlay.union(sides)).padding(12.dp),
        )
    }
}

/** The user-facing text for [error]. The data layer only says what went wrong; the wording lives in string resources. */
@Composable
internal fun libraryErrorText(error: LibraryUIError): String = when (error) {
    LibraryUIError.NoServer -> stringResource(R.string.library_error_no_server)
    is LibraryUIError.Failed -> when (val cause = error.error) {
        LibraryError.Unreachable -> stringResource(R.string.library_error_unreachable)
        LibraryError.UnexpectedResponse -> stringResource(R.string.library_error_unexpected_response)
        LibraryError.SessionExpired -> stringResource(R.string.library_error_session_expired)
        is LibraryError.Http -> cause.serverMessage ?: stringResource(R.string.library_error_http, cause.code)
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
 * The masthead, the category heading with its filter control, and the cover grid, as one scrolling
 * surface.
 *
 * The column count and cell width come from [LibraryGridMetrics] for the width left after the
 * screen insets. Reports the last visible book index to [onLoadMore], which maps the grid index back
 * past the header and clamps it to the last book. The filter menu's choices go to the three
 * `onSelect*`/`onToggleDirection` callbacks. The Continue carousel sits between the masthead and the
 * heading when there are cards.
 *
 * [topInset] and [bottomInset] are the status bar and the bottom overlay the grid scrolls under: they pad the
 * content so it starts and ends clear of them.
 */
@Composable
private fun LibraryGrid(
    uiState: LibraryUIState,
    onOpenBook: (uuid: String) -> Unit,
    onAddBooks: () -> Unit,
    onLoadMore: (lastVisibleBookIndex: Int) -> Unit,
    onSelectCategory: (LibraryCategory) -> Unit,
    onSelectSort: (LibrarySort) -> Unit,
    onToggleDirection: () -> Unit,
    gridState: LazyGridState,
    topInset: Dp,
    bottomInset: Dp,
    modifier: Modifier,
    continueState: ContinueCarouselState,
    onContinueSettled: (cardId: String) -> Unit,
    onResume: (cardId: String) -> Unit,
    onShowContextMenu: (Book) -> Unit,
) {
    val palette = OmnibusTheme.palette
    // Record which cover was tapped before the page opens, so the page's cover morphs from it.
    val openFromGrid = rememberBookCoverOpener(BookCoverOrigin.Grid, onOpenBook)
    val openFromContinue = rememberBookCoverOpener(BookCoverOrigin.Continue, onOpenBook)

    LaunchedEffect(gridState, uiState.books.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filterNotNull()
            .map { (it - HEADER_ITEM_COUNT).coerceAtMost(uiState.books.lastIndex) }
            .filter { it >= 0 }
            .distinctUntilChanged()
            .collect { onLoadMore(it) }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val available = maxWidth - Spacing.screen * 2
        val columns = LibraryGridMetrics.columns(available.value)
        val cellWidth = LibraryGridMetrics.cellWidth(available.value).dp

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = topInset,
                bottom = GRID_BOTTOM_GAP + bottomInset,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().testTag(LibraryTestTags.GRID),
        ) {
            item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) {
                // The masthead and heading carry their own 20 dp inset, so the header undoes the grid's padding.
                Column(Modifier.bleedHorizontally(Spacing.screen)) {
                    Masthead(title = stringResource(R.string.library_title)) {
                        OfflinePill(visible = uiState.isOffline)
                        MastheadAddButton(onAddBooks = onAddBooks)
                    }
                    if (uiState.continueCards.isNotEmpty()) {
                        // 30 dp from the masthead's own bottom padding, and 30 dp from the card or its dots to the heading,
                        // as iOS LibraryView's VStack(spacing: 30).
                        Spacer(Modifier.height(30.dp))
                        ContinueCarousel(
                            cards = uiState.continueCards,
                            selectedId = uiState.continueSelectedId,
                            state = continueState,
                            onSettled = onContinueSettled,
                            onOpenBook = openFromContinue,
                            onResume = onResume,
                            onShowContextMenu = onShowContextMenu,
                        )
                        Spacer(Modifier.height(30.dp))
                    }
                    val railShelves = uiState.railShelves
                    if (railShelves.isNotEmpty()) {
                        ShelvesRail(shelves = railShelves, viewerId = uiState.viewerId)
                        // 30 dp to the heading, as between the Continue card and the heading.
                        Spacer(Modifier.height(30.dp))
                    }
                    Column(Modifier.padding(horizontal = Spacing.screen)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                text = stringResource(uiState.category.headingRes()),
                                style = displayTextStyle(27, palette.ink0),
                                modifier = Modifier.weight(1f).testTag(LibraryTestTags.HEADING),
                            )
                            LibraryFilterControl(
                                category = uiState.category,
                                sort = uiState.sort,
                                direction = uiState.direction,
                                isFiltered = uiState.isFiltered,
                                onSelectCategory = onSelectCategory,
                                onSelectSort = onSelectSort,
                                onToggleDirection = onToggleDirection,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            itemsIndexed(uiState.books, key = { _, book -> book.id }) { index, book ->
                BookGridCell(
                    book = book,
                    width = cellWidth,
                    onClick = { openFromGrid(book.uuid) },
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
 * The "can't open offline" alert for a Continue card. Back and an outside tap dismiss it, as OK does.
 */
@Composable
private fun ContinueUnavailableDialog(book: UnavailableBook, onDismiss: () -> Unit) {
    val palette = OmnibusTheme.palette
    val reading = book.medium == ProgressFormat.Reading
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = palette.ink0),
            ) {
                Text(stringResource(R.string.continue_ok))
            }
        },
        title = {
            Text(
                stringResource(
                    if (reading) R.string.continue_cant_read_offline else R.string.continue_cant_play_offline,
                ),
            )
        },
        text = {
            Text(
                stringResource(
                    if (reading) R.string.continue_offline_book_message else R.string.continue_offline_audiobook_message,
                    book.title,
                ),
            )
        },
        containerColor = palette.bg2,
        titleContentColor = palette.ink0,
        textContentColor = palette.ink1,
    )
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
    LibraryPreviewContent(LibraryUIState(error = LibraryUIError.Failed(LibraryError.Unreachable), hasLoaded = true))
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

@PreviewLightDark
@Composable
private fun LibraryFilteredPreview() {
    LibraryPreviewContent(
        LibraryUIState(
            books = previewBooks,
            category = LibraryCategory.Ebooks,
            direction = SortDirection.Asc,
        ),
    )
}

@PreviewLightDark
@Composable
private fun LibraryFilteredEmptyPreview() {
    LibraryPreviewContent(LibraryUIState(hasLoaded = true, category = LibraryCategory.Audiobooks))
}

@PreviewLightDark
@Composable
private fun LibraryContinuePreview() {
    LibraryPreviewContent(
        LibraryUIState(
            books = previewBooks,
            nextCursor = "c2",
            continueCards = listOf(
                ContinueCard(
                    id = "1:epub",
                    book = previewBooks[0],
                    medium = ProgressFormat.Reading,
                    fraction = 0.42,
                    position = PositionLabel.PercentRead(42),
                    lastOpened = RelativeAge.Hours(3),
                    resumeFileId = null,
                ),
                ContinueCard(
                    id = "2:epub",
                    book = previewBooks[1],
                    medium = ProgressFormat.Reading,
                    fraction = null,
                    position = PositionLabel.InProgress,
                    lastOpened = RelativeAge.Days(3),
                    resumeFileId = null,
                ),
                ContinueCard(
                    id = "3:audio",
                    book = previewBooks[2],
                    medium = ProgressFormat.Listening,
                    fraction = 0.6,
                    position = PositionLabel.Chapter(7, 12),
                    lastOpened = RelativeAge.Minutes(20),
                    resumeFileId = 9L,
                ),
            ),
            continueSelectedId = "1:epub",
        ),
    )
}
