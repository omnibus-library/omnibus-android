package com.omnibus.omnibus.feature.shelves.detail.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.shelves.FilterField
import com.omnibus.omnibus.data.shelves.ShelvesError
import com.omnibus.omnibus.feature.bookdetail.transition.BookCoverOrigin
import com.omnibus.omnibus.feature.bookdetail.transition.rememberBookCoverOpener
import com.omnibus.omnibus.feature.library.ui.BookGrid
import com.omnibus.omnibus.feature.shelves.detail.ShelfContentState
import com.omnibus.omnibus.feature.shelves.detail.ShelfDetailScreenState
import com.omnibus.omnibus.feature.shelves.detail.ShelfDetailUIState
import com.omnibus.omnibus.feature.shelves.detail.ShelfDetailViewModel
import com.omnibus.omnibus.ui.components.ErrorStateView
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.components.OmnibusBackButton
import com.omnibus.omnibus.ui.components.bottomOverlay
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

/** Test tags for the shelf detail screen, so UI tests can find its bar, hero, grid and controls. */
internal object ShelfDetailTestTags {
    const val GRID = "shelf-detail-grid"
    const val TOP_BAR = "shelf-detail-top-bar"
    const val BAR_TITLE = "shelf-detail-bar-title"
    const val BACK = "shelf-detail-back"
    const val ADD_BOOKS = "shelf-detail-add-books"
    const val EMPTY_ADD_BOOKS = "shelf-detail-empty-add-books"
    const val EDIT = "shelf-detail-edit"
    const val MORE = "shelf-detail-more"
    const val HERO_NAME = "shelf-detail-hero-name"
    const val FACET_ROW = "shelf-detail-facet-row"
    const val COUNT_LINE = "shelf-detail-count-line"
    const val CLEAR_FACETS = "shelf-detail-clear-facets"

    fun facet(field: FilterField) = "shelf-detail-facet-${field.wire}"
}

/** The rounded corner of the hero's mosaic. */
private val MosaicCorner = 6.dp

/** The header's first line on screen: the top of the hero, below the bar. */
private val HeroTop = Spacing.sm

/**
 * One shelf's detail screen: its hero, its books in the Library's grid, and the states around them.
 *
 * Stateful wrapper that feeds [ShelfDetailContent] from [ShelfDetailViewModel]. Reloads each time it is shown.
 *
 * @param onBack Called when the back arrow is tapped.
 * @param onOpenBook Called with a book's uuid when a cell is tapped.
 * @param onOpenSeries Called with the series id and name when an unfolded series' "Series page" is tapped.
 * @param onAddBooks Called when the Add books action is tapped.
 */
@Composable
fun ShelfDetailScreen(
    shelfId: Long,
    onBack: () -> Unit,
    onOpenBook: (uuid: String) -> Unit,
    onOpenSeries: (seriesId: Long, name: String) -> Unit,
    onAddBooks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<ShelfDetailViewModel, ShelfDetailViewModel.Factory>(
        creationCallback = { it.create(shelfId) },
    )
    val uiState by viewModel.uiState.collectAsState()

    // Runs each time the entry is composed again, so returning to the shelf reloads it.
    LaunchedEffect(Unit) { viewModel.screenShown() }

    ShelfDetailContent(
        uiState = uiState,
        onBack = onBack,
        onOpenBook = onOpenBook,
        onOpenSeries = onOpenSeries,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onUnfoldSeries = viewModel::unfoldSeries,
        onFoldUpSeries = viewModel::foldUpSeries,
        onTransientErrorShown = viewModel::transientErrorShown,
        modifier = modifier,
        onAddBooks = onAddBooks,
    )
}

/**
 * The shelf detail screen's state-driven layout, without the ViewModel.
 *
 * Internal rather than private so the instrumented tests can render each state with hand-built [ShelfDetailUIState]s.
 *
 * @param onRefresh Called when the shelf is pulled down.
 * @param onRetry Called when an error's Try again is tapped.
 * @param onTransientErrorShown Called once the snackbar for [ShelfDetailUIState.transientError] has been launched.
 * @param onAddBooks Called when the Add books action is tapped.
 * @param gridState The grid's scroll state. The fading title reads it.
 * @param snackbarHostState Hosts the transient error snackbar.
 * @param reducedMotion Whether the system asks for no animation. Tests pass it to force either path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShelfDetailContent(
    uiState: ShelfDetailUIState,
    onBack: () -> Unit,
    onOpenBook: (uuid: String) -> Unit,
    onOpenSeries: (seriesId: Long, name: String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onUnfoldSeries: (seriesId: Long) -> Unit,
    onFoldUpSeries: (seriesId: Long) -> Unit,
    onTransientErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
    onAddBooks: () -> Unit = {},
    gridState: LazyGridState = rememberLazyGridState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val palette = OmnibusTheme.palette
    val scope = rememberCoroutineScope()
    val screen = uiState.screen
    val identity = uiState.identity

    val hasTransient = uiState.transientError != null || uiState.transientNoServer
    val transientMessage = if (hasTransient) shelfErrorText(uiState.transientError) else null
    // Clearing the message restarts this effect with null, so the snackbar is launched in a scope that outlives it.
    LaunchedEffect(transientMessage) {
        val message = transientMessage ?: return@LaunchedEffect
        onTransientErrorShown()
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // The fading title: it appears once the hero's name has scrolled under the bar, measured from the layout so no
    // threshold is fixed in dp.
    var headerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var nameCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val nameBottomPx by remember {
        derivedStateOf {
            val header = headerCoords
            val name = nameCoords
            if (header == null || name == null || !header.isAttached || !name.isAttached) {
                Int.MAX_VALUE
            } else {
                (header.localPositionOf(name, Offset.Zero).y + name.size.height).toInt()
            }
        }
    }
    val showTitle by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset >= nameBottomPx
        }
    }
    val titleAlpha by animateFloatAsState(
        targetValue = if (showTitle) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else Motion.crossfade(),
        label = "barTitle",
    )

    val openBook = rememberBookCoverOpener(BookCoverOrigin.Grid, onOpenBook)
    val content = uiState.content

    Box(modifier.fillMaxSize().background(palette.bg0)) {
        Column(Modifier.fillMaxSize()) {
            val showsContent = screen == ShelfDetailScreenState.Content && identity != null
            ShelfDetailTopBar(
                title = if (showsContent) identity?.name.orEmpty() else "",
                titleAlpha = titleAlpha,
                onBack = onBack,
                actions = {
                    if (showsContent && uiState.actions.canAddBooks) {
                        IconButton(onClick = onAddBooks, modifier = Modifier.testTag(ShelfDetailTestTags.ADD_BOOKS)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_reader_plus),
                                contentDescription = stringResource(R.string.shelf_detail_add_books_description),
                            )
                        }
                    }
                },
            )
            when (screen) {
                ShelfDetailScreenState.Loading -> LoadingStateView(
                    label = stringResource(R.string.shelf_detail_loading),
                    modifier = Modifier.weight(1f),
                )

                is ShelfDetailScreenState.Error -> ErrorStateView(
                    title = stringResource(R.string.library_error_title),
                    message = shelfErrorText(screen.error),
                    actionLabel = stringResource(R.string.library_try_again),
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )

                ShelfDetailScreenState.Gone -> Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    ShelfGoneMessage(onBack = onBack)
                }

                ShelfDetailScreenState.Content -> PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.weight(1f),
                ) {
                    identity?.let { shelf ->
                        BookGrid(
                            items = if (content == ShelfContentState.Books) uiState.gridItems else emptyList(),
                            unfolded = uiState.unfoldedSeries,
                            groupBySeries = true,
                            onOpenBook = openBook,
                            onUnfoldSeries = onUnfoldSeries,
                            onFoldUpSeries = onFoldUpSeries,
                            onOpenSeries = onOpenSeries,
                            gridState = gridState,
                            topInset = 0.dp,
                            bottomInset = 0.dp,
                            isLoadingMore = false,
                            reducedMotion = reducedMotion,
                            gridTestTag = ShelfDetailTestTags.GRID,
                            header = {
                                Column(Modifier.onGloballyPositioned { headerCoords = it }) {
                                    ShelfDetailHero(
                                        shelf = shelf,
                                        description = uiState.detail?.description,
                                        rules = uiState.detail?.rules,
                                        covers = uiState.mosaicCovers,
                                        onNamePositioned = { nameCoords = it },
                                        modifier = Modifier.padding(top = HeroTop),
                                    )
                                    ShelfDetailHeaderBody(
                                        content = content,
                                        canAddBooks = uiState.actions.canAddBooks,
                                        onRetry = onRetry,
                                        onAddBooks = onAddBooks,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.bottomOverlay)
                .padding(12.dp),
        )
    }
}

/** What sits under the hero: a spinner while the page loads, a state's message, or the failure with a retry. */
@Composable
private fun ShelfDetailHeaderBody(
    content: ShelfContentState,
    canAddBooks: Boolean,
    onRetry: () -> Unit,
    onAddBooks: () -> Unit,
) {
    val palette = OmnibusTheme.palette
    when (content) {
        is ShelfContentState.Empty -> ShelfEmptyMessage(
            kind = content.kind,
            canAddBooks = canAddBooks,
            onAddBooks = onAddBooks,
        )

        is ShelfContentState.Unreachable -> ShelfUnreachableMessage(count = content.count)

        ShelfContentState.Loading -> Box(
            Modifier.fillMaxWidth().padding(top = 26.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = palette.ink3,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        }

        is ShelfContentState.Failed -> ErrorStateView(
            title = stringResource(R.string.library_error_title),
            message = shelfErrorText(content.error),
            actionLabel = stringResource(R.string.library_try_again),
            onRetry = onRetry,
            modifier = Modifier.heightIn(min = 320.dp),
        )

        // The filter states come with the facets.
        else -> Unit
    }
}

/**
 * The shelf detail's top bar: a small bar with the back arrow and the shelf's name as its title. The title fades in
 * with [titleAlpha] and is hidden from TalkBack while it is transparent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShelfDetailTopBar(
    title: String,
    titleAlpha: Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val palette = OmnibusTheme.palette
    val visibleTitle = if (titleAlpha > 0f) {
        Modifier.testTag(ShelfDetailTestTags.BAR_TITLE)
    } else {
        Modifier.clearAndSetSemantics {}
    }
    TopAppBar(
        modifier = modifier.testTag(ShelfDetailTestTags.TOP_BAR),
        title = {
            Text(
                text = title,
                style = displayTextStyle(22, palette.ink0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = titleAlpha }.then(visibleTitle),
            )
        },
        navigationIcon = {
            OmnibusBackButton(onClick = onBack, modifier = Modifier.testTag(ShelfDetailTestTags.BACK))
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = palette.bg0,
            scrolledContainerColor = palette.bg0,
            navigationIconContentColor = palette.ink0,
            titleContentColor = palette.ink0,
            actionIconContentColor = palette.ink0,
        ),
    )
}

/** The user-facing text for a shelf failure. Null means no server is saved. */
@Composable
internal fun shelfErrorText(error: ShelvesError?): String = when (error) {
    null -> stringResource(R.string.library_error_no_server)
    ShelvesError.Unreachable -> stringResource(R.string.library_error_unreachable)
    ShelvesError.SessionExpired -> stringResource(R.string.library_error_session_expired)
    is ShelvesError.Http -> error.serverMessage ?: stringResource(R.string.library_error_http, error.code)
    ShelvesError.UnexpectedResponse, ShelvesError.Forbidden, ShelvesError.NotFound ->
        stringResource(R.string.library_error_unexpected_response)
}
