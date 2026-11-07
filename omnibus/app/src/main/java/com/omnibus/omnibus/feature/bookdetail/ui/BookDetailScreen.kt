package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailError
import com.omnibus.omnibus.feature.bookdetail.BookDetailMessage
import com.omnibus.omnibus.feature.bookdetail.BookDetailScreenState
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.BookDetailViewModel
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.stage.BookStage
import com.omnibus.omnibus.feature.bookdetail.stage.BookStageWash
import com.omnibus.omnibus.feature.bookdetail.stage.LocalBookStageWashPose
import com.omnibus.omnibus.feature.bookdetail.stage.rememberBookAccent
import com.omnibus.omnibus.feature.bookdetail.tabs.BookDetailTabHost
import com.omnibus.omnibus.feature.bookdetail.tabs.rememberBookDetailTabController
import com.omnibus.omnibus.feature.bookdetail.tabs.rememberTabWashPose
import com.omnibus.omnibus.feature.bookdetail.tabs.tabHostMinHeight
import com.omnibus.omnibus.feature.library.LibraryUIError
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.feature.library.ui.isLightGround
import com.omnibus.omnibus.feature.library.ui.libraryErrorText
import com.omnibus.omnibus.ui.components.ErrorStateView
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

/**
 * The Book Detail page for the book with [uuid]. It reloads when the screen is shown again, such as after the
 * reader or a pushed screen is popped.
 */
@Composable
fun BookDetailScreen(
    uuid: String,
    onNavigate: (BookDetailDestination) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookDetailViewModel = hiltViewModel<BookDetailViewModel, BookDetailViewModel.Factory>(
        creationCallback = { it.create(uuid) },
    ),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Runs each time the entry is composed again, as LibraryScreen's libraryShown() does.
    LaunchedEffect(Unit) { viewModel.screenShown() }

    BookDetailScreenContent(
        uiState = uiState,
        onNavigate = onNavigate,
        onClose = onClose,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onReloadBook = viewModel::reloadBook,
        onBookChanged = viewModel::bookChanged,
        onTransientErrorShown = viewModel::transientErrorShown,
        modifier = modifier,
    )
}

/**
 * The page's content for [uiState], with the callbacks and slots injected. Internal so instrumented tests can
 * render each state, as `LibraryScreenContent`. Column and two-pane modes share one composition tree, so a layout
 * swap keeps state: see [BookDetailPaneLayout].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookDetailScreenContent(
    uiState: BookDetailUIState,
    onNavigate: (BookDetailDestination) -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onReloadBook: () -> Unit,
    onBookChanged: (Book) -> Unit,
    onTransientErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
    slots: BookDetailSlots = BookDetailSlots.Registered,
    scrollState: ScrollState = rememberScrollState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    reducedMotion: Boolean = rememberReducedMotion(),
    stageScrollState: ScrollState = rememberScrollState(),
    panelScrollState: ScrollState = rememberScrollState(),
    paneSplit: (width: Dp, height: Dp) -> PaneSplit? = ::bookDetailPaneSplit,
) {
    val palette = OmnibusTheme.palette
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val content = uiState.screen as? BookDetailScreenState.Content
    // Saved, so a restored scroll is not clamped to the range the stage has before the action area is measured.
    var actionAreaHeightPx by rememberSaveable { mutableIntStateOf(0) }

    // Section messages and the refresh error share one host. Launched in a scope that outlives the caller.
    val showMessage: (BookDetailMessage) -> Unit = remember(scope, snackbarHostState) {
        { msg ->
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = msg.text,
                    actionLabel = msg.actionLabel,
                    withDismissAction = msg.holdUntilDismissed,
                    duration = when {
                        msg.holdUntilDismissed -> SnackbarDuration.Indefinite
                        msg.actionLabel != null -> SnackbarDuration.Long
                        else -> SnackbarDuration.Short
                    },
                )
                if (result == SnackbarResult.ActionPerformed) msg.onAction?.invoke()
            }
        }
    }
    val transientText = uiState.transientError?.let { bookDetailErrorText(it) }
    LaunchedEffect(uiState.transientError) {
        val text = transientText ?: return@LaunchedEffect
        onTransientErrorShown()
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    // One context per (book, accent, reloadKey, tabs); callbacks go through rememberUpdatedState so the context never
    // changes on recomposition or scroll.
    val tabState = if (content != null) rememberBookDetailTabController(scrollState, reducedMotion, panelScrollState) else null
    val handoff = rememberSaveable(saver = BookDetailScrollHandoff.Saver) { BookDetailScrollHandoff() }
    val context: BookDetailSectionContext? = content?.let { c ->
        val accent = rememberBookAccent(c.book)
        val tabs = checkNotNull(tabState)
        val navigate by rememberUpdatedState(onNavigate)
        val changed by rememberUpdatedState(onBookChanged)
        val reload by rememberUpdatedState(onReloadBook)
        val close by rememberUpdatedState(onClose)
        remember(c.book, accent, uiState.reloadKey, tabs) {
            BookDetailSectionContext(
                book = c.book,
                accent = accent,
                reloadKey = uiState.reloadKey,
                tabs = tabs,
                onNavigate = { navigate(it) },
                onShowMessage = showMessage,
                onBookChanged = { changed(it) },
                onReloadBook = { reload() },
                onClose = { close() },
            )
        }
    }

    val hasActionArea = context != null && (slots.actionBar.isNotEmpty() || showsReadPill(slots.actionBar, context.book))
    LaunchedEffect(hasActionArea) {
        if (!hasActionArea) actionAreaHeightPx = 0
    }
    val actionAreaDp: Dp = with(density) { actionAreaHeightPx.toDp() }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val thresholdPx = with(density) { TopBarFillThreshold.toPx() }
    val filled by remember(scrollState, thresholdPx) {
        derivedStateOf { isTopBarFilled(scrollState.value, thresholdPx) }
    }

    // The layout is chosen from this screen's own size, the window minus the shell's tab bar and any side cutout.
    BoxWithConstraints(modifier.fillMaxSize().background(palette.bg0)) {
        val layoutDirection = LocalLayoutDirection.current
        val cutout = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
        val cutoutPadding = cutout.asPaddingValues()
        val cutoutWidth = cutoutPadding.calculateLeftPadding(layoutDirection) +
            cutoutPadding.calculateRightPadding(layoutDirection)
        val viewportHeight = maxHeight
        val split = if (content != null) paneSplit(maxWidth - cutoutWidth, viewportHeight) else null
        val twoPane = split != null
        val underBar = split?.let { stageUnderBar(it.leading) } ?: true
        val coverScale = if (split != null) stageCoverScale(viewportHeight - statusTop, underBar) else 1f
        if (tabState != null) {
            BookDetailScrollHandoffEffect(handoff, twoPane, tabState, scrollState, panelScrollState)
        }

        when (val screen = uiState.screen) {
            BookDetailScreenState.Loading -> LoadingStateView(
                label = stringResource(R.string.book_detail_loading),
                modifier = Modifier.fillMaxSize(),
            )

            is BookDetailScreenState.Error -> ErrorStateView(
                title = stringResource(R.string.book_detail_error_title),
                message = bookDetailErrorText(screen.error),
                actionLabel = stringResource(R.string.book_detail_retry),
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )

            is BookDetailScreenState.Content -> {
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                    state = pullState,
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullState,
                            isRefreshing = uiState.isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = statusTop + TopBarRowHeight),
                        )
                    },
                ) {
                    if (context != null && tabState != null) {
                        val pinTop = statusTop + TopBarRowHeight
                        // Both scrolls are built here and chosen below, so the mode never changes their groups.
                        val pageScroll = Modifier.verticalScroll(scrollState)
                        val stageScroll = Modifier.verticalScroll(stageScrollState)
                        CompositionLocalProvider(
                            LocalBookStageWashPose provides rememberTabWashPose({ tabState.selected }, reducedMotion),
                        ) {
                            if (twoPane) {
                                BookStageWash(
                                    context.book.coverIdentity(),
                                    isLightGround(palette.bg0),
                                    Modifier.fillMaxSize(),
                                    reducedMotion = reducedMotion,
                                )
                            }
                            BookDetailPaneLayout(
                                split = split,
                                trailingSpace = actionAreaDp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (twoPane) Modifier.windowInsetsPadding(cutout) else pageScroll)
                                    .testTag(BookDetailTestTags.SCROLL),
                                stagePane = {
                                    val stagePaneModifier = if (twoPane) {
                                        Modifier
                                            .edgeFade(
                                                top = stageTopFadeBand(statusTop, underBar),
                                                bottom = stageBottomFadeBand(viewportHeight, actionAreaDp),
                                            )
                                            .then(stageScroll)
                                            .padding(horizontal = StageHaloOverhang)
                                            .padding(bottom = actionAreaDp)
                                    } else {
                                        Modifier
                                    }
                                    Box(stagePaneModifier.testTag(BookDetailTestTags.STAGE_PANE)) {
                                        BookStage(
                                            context,
                                            slots,
                                            reducedMotion,
                                            backdrop = !twoPane,
                                            underBar = underBar,
                                            coverScale = coverScale,
                                        )
                                    }
                                },
                                tabHost = {
                                    BookDetailTabHost(
                                        state = tabState,
                                        context = context,
                                        slots = slots,
                                        scrollState = scrollState,
                                        pinTop = pinTop,
                                        minHeight = tabHostMinHeight(viewportHeight, pinTop, actionAreaDp),
                                        reducedMotion = reducedMotion,
                                        twoPane = twoPane,
                                        panelScrollState = panelScrollState,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        if (context != null && hasActionArea) {
            BookDetailActionArea(
                context = context,
                sections = slots.actionBar,
                backed = !twoPane,
                modifier = (split?.let { Modifier.align(Alignment.BottomStart).windowInsetsPadding(cutout).width(it.leading) }
                    ?: Modifier.align(Alignment.BottomCenter))
                    .onSizeChanged { actionAreaHeightPx = it.height },
            )
        }

        BookDetailTopBar(
            title = if (twoPane) null else content?.book?.displayTitle,
            filled = content != null && !twoPane && filled,
            onBack = onClose,
            reducedMotion = reducedMotion,
            modifier = Modifier.align(Alignment.TopStart).then(if (twoPane) Modifier.windowInsetsPadding(cutout) else Modifier),
            actions = {
                if (context != null && slots.topBarActions.isNotEmpty()) {
                    slots.topBarActions.forEach { it.Content(context, Modifier) }
                } else {
                    Spacer(Modifier.size(44.dp))
                }
            },
        )

        if (context != null) {
            Box(Modifier.fillMaxSize()) {
                slots.screenOverlays.forEach { it.Content(context, Modifier) }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = actionAreaDp + 12.dp),
        )
    }
}

/** The user-facing text for [error]. */
@Composable
internal fun bookDetailErrorText(error: BookDetailError): String = when (error) {
    BookDetailError.NotFound -> stringResource(R.string.book_detail_error_not_found)
    BookDetailError.NoServer -> libraryErrorText(LibraryUIError.NoServer)
    is BookDetailError.Failed -> libraryErrorText(LibraryUIError.Failed(error.error))
}

@PreviewLightDark
@Composable
private fun BookDetailScreenContentPreview() {
    val book = Book(
        id = 1,
        uniqueIdentifier = "uuid-1",
        filename = "the-great-gatsby.epub",
        title = "The Great Gatsby",
        creators = listOf(Creator("F. Scott Fitzgerald", "author", null, 7L)),
        coverUrl = null,
        accent = null,
        formats = listOf("epub"),
        series = "Fitzgerald Classics",
        seriesIndex = "1",
        hasPhysical = false,
        addedAt = null,
        lastInteractedAt = null,
        bookFiles = emptyList(),
    )
    OmnibusTheme {
        BookDetailScreenContent(
            uiState = BookDetailUIState(book = book, isLoading = false),
            onNavigate = {},
            onClose = {},
            onRetry = {},
            onRefresh = {},
            onReloadBook = {},
            onBookChanged = {},
            onTransientErrorShown = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun BookDetailScreenLoadingPreview() {
    OmnibusTheme {
        BookDetailScreenContent(
            uiState = BookDetailUIState(),
            onNavigate = {},
            onClose = {},
            onRetry = {},
            onRefresh = {},
            onReloadBook = {},
            onBookChanged = {},
            onTransientErrorShown = {},
        )
    }
}
