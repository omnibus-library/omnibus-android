package com.omnibus.omnibus.feature.reader.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayer
import com.omnibus.omnibus.feature.library.LibraryUIError
import com.omnibus.omnibus.feature.library.ui.libraryErrorText
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.feature.reader.ReaderCommand
import com.omnibus.omnibus.feature.reader.ReaderMenuRow
import com.omnibus.omnibus.feature.reader.ReaderOpening
import com.omnibus.omnibus.feature.reader.ReaderSheet
import com.omnibus.omnibus.feature.reader.ReaderUIState
import com.omnibus.omnibus.feature.reader.ReaderUnavailableReason
import com.omnibus.omnibus.feature.reader.ReaderViewModel
import com.omnibus.omnibus.feature.reader.bootCfi
import com.omnibus.omnibus.ui.components.ErrorStateView
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

/**
 * The reader's destination. It goes on the root back stack, not a tab stack, so the reader covers
 * the shell and leaving it returns to the shell with its tab stacks intact.
 *
 * @property bookUuid The book to open.
 * @property startCfi An EPUB CFI to open at instead of the saved position, or null to resume.
 */
@Serializable
data class ReaderRoute(val bookUuid: String, val startCfi: String? = null) : NavKey

/**
 * The reader for one book, over the shell. The ViewModel is created for [bookUuid] and is the only
 * thing here that sees its state. [onClose] leaves the reader. [startCfi] opens at that place instead
 * of the saved position. [onOpenPlayer] docks the mini player at the bottom and expands it into the player; null shows
 * no dock.
 */
@Composable
fun ReaderScreen(
    bookUuid: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    startCfi: String? = null,
    viewModel: ReaderViewModel = hiltViewModel<ReaderViewModel, ReaderViewModel.Factory>(
        creationCallback = { it.create(bookUuid, startCfi) },
    ),
    onOpenPlayer: ((bookUuid: String) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val controller = rememberReaderController()

    ReaderScreenContent(
        uiState = uiState,
        bookUuid = bookUuid,
        controller = controller,
        assetLoaderFactory = viewModel.assetLoaderFactory,
        commands = viewModel.commands,
        onPageEvent = viewModel::onPageEvent,
        onSheetDismissed = viewModel::onSheetDismissed,
        onTocEntryChosen = viewModel::onTocEntryChosen,
        onMenuOpened = viewModel::onMenuOpened,
        onMenuDismissed = viewModel::onMenuDismissed,
        onMenuRowChosen = viewModel::onMenuRowChosen,
        onRetryBookRequested = viewModel::onRetryBookRequested,
        onReaderClosed = viewModel::onReaderClosed,
        onReaderStopped = viewModel::onReaderStopped,
        onSyncOfferAccepted = viewModel::onSyncOfferAccepted,
        onSyncOfferDismissed = viewModel::onSyncOfferDismissed,
        onSettingsChanged = viewModel::onSettingsChanged,
        onBookmarkToggled = viewModel::onBookmarkToggled,
        onBookmarkChosen = viewModel::onBookmarkChosen,
        onBookmarksRetryRequested = viewModel::onBookmarksRetryRequested,
        onClose = onClose,
        modifier = modifier,
        bottomDock = {
            if (onOpenPlayer != null) {
                MiniPlayer(
                    onExpand = onOpenPlayer,
                    contentWindowInsets = ReaderWebViewDefaults.insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                )
            }
        },
    )
}

/**
 * The reader's screen with no ViewModel: everything it shows comes from [uiState] and the
 * callbacks, so tests and previews can drive it directly.
 *
 * Nothing here pads the page for the system bars. The reader fills the window, and the page takes
 * the insets as CSS variables, so only the state views are inset.
 *
 * The chrome is an overlay sibling of the page and never resizes it. Sheets opened from the menu
 * (#47, #48) must use the app theme, so host them outside this function's
 * `OmnibusTheme(darkTheme = !theme.isLight)` or re-wrap them in `OmnibusTheme()`.
 *
 * @param commands page commands from the ViewModel, each applied to [controller] once.
 * @param onSheetDismissed a sheet was dismissed by any means, including back.
 * @param onTocEntryChosen a contents entry was chosen.
 * @param onPageEvent receives every event the page reports, and the host's load status.
 * @param onMenuOpened the menu button was tapped.
 * @param onMenuDismissed a tap or back dismissed the open menu.
 * @param onMenuRowChosen a menu row was chosen.
 * @param onReaderClosed called when the reader is left, or its composition is.
 * @param onReaderStopped called when the activity stops, so the position is flushed before the app is backgrounded.
 * @param onSyncOfferAccepted the reader chose "Go" on the sync offer.
 * @param onSyncOfferDismissed the reader dismissed the sync offer.
 * @param onClose called after [onReaderClosed] when the reader is left.
 * @param onSettingsChanged the reader changed a setting in the settings sheet.
 * @param onBookmarkToggled the quick action was tapped: bookmark the page, or remove its bookmarks.
 * @param onBookmarkChosen a bookmark was chosen on the Bookmarks segment.
 * @param onBookmarksRetryRequested the Bookmarks segment's Try again was tapped.
 * @param bottomDock drawn below the page, which ends at its top edge (the mini player dock, P-MINI-4). Empty by default.
 */
@Composable
internal fun ReaderScreenContent(
    uiState: ReaderUIState,
    bookUuid: String,
    controller: ReaderController,
    assetLoaderFactory: ReaderAssetLoaderFactory,
    commands: Flow<ReaderCommand>,
    onPageEvent: (ReaderEvent) -> Unit,
    onSheetDismissed: () -> Unit,
    onTocEntryChosen: (TocEntry) -> Unit,
    onMenuOpened: () -> Unit,
    onMenuDismissed: () -> Unit,
    onMenuRowChosen: (ReaderMenuRow) -> Unit,
    onRetryBookRequested: () -> Unit,
    onReaderClosed: () -> Unit,
    onReaderStopped: () -> Unit,
    onSyncOfferAccepted: () -> Unit,
    onSyncOfferDismissed: () -> Unit,
    onClose: () -> Unit,
    onSettingsChanged: (ReaderSettings) -> Unit = {},
    onBookmarkToggled: () -> Unit = {},
    onBookmarkChosen: (Bookmark) -> Unit = {},
    onBookmarksRetryRequested: () -> Unit = {},
    modifier: Modifier = Modifier,
    bottomDock: @Composable () -> Unit = {},
) {
    val leave = {
        onReaderClosed()
        onClose()
    }
    // Always enabled, so back takes this path in every state, and it outranks the root NavDisplay's handler.
    BackHandler {
        when {
            uiState.sheet != null -> onSheetDismissed()
            uiState.menuOpen -> onMenuDismissed()
            else -> leave()
        }
    }

    LaunchedEffect(controller, commands) {
        commands.collect { controller.execute(it) }
    }

    val currentOnStopped by rememberUpdatedState(onReaderStopped)
    val currentOnClosed by rememberUpdatedState(onReaderClosed)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) currentOnStopped() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Leaving composition flushes too, for a dismissal that bypasses back. Repeated flushes are no-ops.
    DisposableEffect(Unit) { onDispose { currentOnClosed() } }

    // Read outside the reading theme, so sheets follow the app's own dark setting.
    val appDark = isSystemInDarkTheme()
    val theme = uiState.settings.theme
    val reduceMotion = rememberReducedMotion()
    val window = if (LocalInspectionMode.current) null else LocalActivity.current?.window
    if (window != null) {
        DisposableEffect(window, theme) {
            val bars = WindowCompat.getInsetsController(window, window.decorView)
            val previousStatus = bars.isAppearanceLightStatusBars
            val previousNavigation = bars.isAppearanceLightNavigationBars
            bars.isAppearanceLightStatusBars = theme.isLight
            bars.isAppearanceLightNavigationBars = theme.isLight
            onDispose {
                bars.isAppearanceLightStatusBars = previousStatus
                bars.isAppearanceLightNavigationBars = previousNavigation
            }
        }
    }

    val ground = Color(theme.groundArgb)
    // Whether the dock below the page is showing, measured from its height.
    var dockShowing by remember { mutableStateOf(false) }
    OmnibusTheme(darkTheme = !theme.isLight) {
        // With a dock below, the page ends at its top edge, and the dock pads the bottom inset itself.
        val pageInsets = if (dockShowing) {
            ReaderWebViewDefaults.insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        } else {
            ReaderWebViewDefaults.insets
        }
        Box(
            modifier
                .fillMaxSize()
                .background(ground)
                .testTag(ReaderScreenTags.ROOT),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().testTag(ReaderScreenTags.PAGE_AREA)) {
                    // Inset so the message clears the bars, with the ground painted behind them.
                    val stateModifier = Modifier.fillMaxSize().windowInsetsPadding(pageInsets)
                    when (val book = uiState.book) {
                        ReaderBookState.Loading -> LoadingStateView(
                            label = stringResource(R.string.reader_loading),
                            modifier = stateModifier,
                            background = ground,
                        )

                        is ReaderBookState.Unavailable -> ErrorStateView(
                            title = stringResource(R.string.reader_error_title),
                            message = stringResource(book.reason.messageRes()),
                            actionLabel = stringResource(R.string.reader_back),
                            onRetry = leave,
                            modifier = stateModifier,
                            background = ground,
                        )

                        is ReaderBookState.Failed -> ErrorStateView(
                            title = stringResource(R.string.reader_error_title),
                            message = libraryErrorText(book.error),
                            actionLabel = stringResource(R.string.reader_try_again),
                            onRetry = onRetryBookRequested,
                            modifier = stateModifier,
                            background = ground,
                        )

                        is ReaderBookState.Ready -> {
                            val opening = uiState.opening
                            if (LocalInspectionMode.current) {
                                // The WebView can't run in a preview, so the page is a plain ground.
                                Box(Modifier.fillMaxSize().background(ground).testTag(ReaderScreenTags.PREVIEW_PAGE))
                            } else if (opening is ReaderOpening.Resolved && uiState.settingsLoaded) {
                                // The page boots at the opening position and the stored settings, so it waits for both.
                                SideEffect { controller.startAt(opening.start.bootCfi) }
                                ReaderWebView(
                                    bookUuid = bookUuid,
                                    settings = uiState.settings,
                                    controller = controller,
                                    assetLoaderFactory = assetLoaderFactory,
                                    modifier = Modifier.fillMaxSize(),
                                    onEvent = onPageEvent,
                                    insets = pageInsets,
                                )
                            }
                            if (uiState.loadStatus == ReaderLoadStatus.Loading) {
                                LoadingStateView(
                                    label = stringResource(R.string.reader_opening_title, book.title),
                                    modifier = stateModifier,
                                    background = ground,
                                )
                            }
                            if (uiState.loadStatus == ReaderLoadStatus.Ready) {
                                // Anchored above the bottom band whatever the chrome does, so it never moves when the chrome toggles.
                                ChromeVisibility(
                                    visible = uiState.syncOffer != null,
                                    reduceMotion = reduceMotion,
                                    enter = fadeIn(Motion.settle()) + slideInVertically { it / 2 },
                                    exit = fadeOut(Motion.settle()),
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                ) {
                                    ReaderSyncOfferBanner(
                                        title = stringResource(R.string.reader_sync_offer_title),
                                        detail = stringResource(R.string.reader_sync_offer_detail),
                                        goLabel = stringResource(R.string.reader_sync_offer_go),
                                        dismissLabel = stringResource(R.string.reader_sync_offer_dismiss),
                                        colors = readerChromeColors(theme),
                                        actionColor = readerSyncOfferActionColor(theme),
                                        onAccept = onSyncOfferAccepted,
                                        onDismiss = onSyncOfferDismissed,
                                        modifier = Modifier
                                            .windowInsetsPadding(
                                                pageInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                                            )
                                            .padding(start = Spacing.md, end = Spacing.md, bottom = ReaderBands.Bottom + Spacing.sm)
                                            .widthIn(max = 560.dp)
                                            .fillMaxWidth(),
                                    )
                                }
                                ReaderChrome(
                                    indicators = ReaderIndicators(uiState.location, book.title, uiState.chromeVisible),
                                    chromeVisible = uiState.chromeVisible,
                                    menuOpen = uiState.menuOpen,
                                    menuRows = uiState.menuRows,
                                    percent = uiState.menuPercent,
                                    bookmarkCount = uiState.bookmarkCount,
                                    pageBookmarked = uiState.isPageBookmarked,
                                    canBookmark = uiState.location?.cfi?.isNotBlank() == true,
                                    colors = readerChromeColors(theme),
                                    reduceMotion = reduceMotion,
                                    insets = pageInsets,
                                    onClose = leave,
                                    onMenuOpened = onMenuOpened,
                                    onMenuDismissed = onMenuDismissed,
                                    onMenuRowChosen = onMenuRowChosen,
                                    onBookmarkToggled = onBookmarkToggled,
                                )
                            }
                            if (uiState.sheet == ReaderSheet.Contents) {
                                // The app theme, not the reading theme: a dark-app reader on a Light page gets a dark sheet.
                                OmnibusTheme(darkTheme = appDark) {
                                    ReaderContentsSheet(
                                        title = book.title,
                                        cover = book.cover,
                                        accent = readerSheetAccent(book, OmnibusTheme.palette),
                                        toc = uiState.toc,
                                        location = uiState.location,
                                        onEntryChosen = onTocEntryChosen,
                                        onDismiss = onSheetDismissed,
                                        initialTab = uiState.contentsTab,
                                        bookmarks = uiState.bookmarks,
                                        bookmarksStatus = uiState.bookmarksStatus,
                                        onBookmarkChosen = onBookmarkChosen,
                                        onBookmarksRetry = onBookmarksRetryRequested,
                                    )
                                }
                            }
                        }
                    }
                    // The sheet is app-themed, never the reading theme (R13), so it re-wraps outside the reader's theme.
                    if (uiState.sheet == ReaderSheet.Settings) {
                        OmnibusTheme {
                            ReaderSettingsSheet(
                                settings = uiState.settings,
                                accent = readerSheetAccent(uiState.book, OmnibusTheme.palette),
                                onSettingsChanged = onSettingsChanged,
                                onDismiss = onSheetDismissed,
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { dockShowing = it.height > 0 }
                        .testTag(ReaderScreenTags.DOCK),
                ) {
                    bottomDock()
                }
            }
        }
    }
}

/** Applies one [ReaderCommand] to the page. */
internal fun ReaderController.execute(command: ReaderCommand) = when (command) {
    is ReaderCommand.Display -> display(command.target)
    is ReaderCommand.Seek -> seek(command.fraction)
}

/** Test tags on the reader's own nodes, so tests can find them without depending on the layout. */
internal object ReaderScreenTags {
    const val ROOT = "reader_root"
    const val PREVIEW_PAGE = "reader_preview_page"
    const val PAGE_AREA = "reader_page_area"
    const val DOCK = "reader_dock"
}

private fun ReaderUnavailableReason.messageRes(): Int =
    when (this) {
        ReaderUnavailableReason.NotFound -> R.string.reader_error_book_missing
        ReaderUnavailableReason.NoEpub -> R.string.reader_error_no_epub
    }

@Composable
private fun ReaderPreview(book: ReaderBookState, loadStatus: ReaderLoadStatus = ReaderLoadStatus.Loading) {
    val dark = isSystemInDarkTheme()
    ReaderScreenContent(
        uiState = ReaderUIState(
            book = book,
            loadStatus = loadStatus,
            settings = ReaderSettings(theme = if (dark) ReaderTheme.Dark else ReaderTheme.Light),
        ),
        bookUuid = "preview",
        controller = rememberReaderController(),
        assetLoaderFactory = ReaderAssetLoaderFactory(
            LocalContext.current,
            Dispatchers.Unconfined,
            BookFileSource { BookFileResult.NotFound },
        ),
        commands = emptyFlow(),
        onPageEvent = {},
        onSheetDismissed = {},
        onTocEntryChosen = {},
        onMenuOpened = {},
        onMenuDismissed = {},
        onMenuRowChosen = {},
        onRetryBookRequested = {},
        onReaderClosed = {},
        onReaderStopped = {},
        onSyncOfferAccepted = {},
        onSyncOfferDismissed = {},
        onClose = {},
    )
}

@PreviewLightDark
@Composable
private fun ReaderScreenLoadingPreview() {
    ReaderPreview(book = ReaderBookState.Loading)
}

@PreviewLightDark
@Composable
private fun ReaderScreenNotFoundPreview() {
    ReaderPreview(book = ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound))
}

@PreviewLightDark
@Composable
private fun ReaderScreenNoEpubPreview() {
    ReaderPreview(book = ReaderBookState.Unavailable(ReaderUnavailableReason.NoEpub))
}

@PreviewLightDark
@Composable
private fun ReaderScreenFailedPreview() {
    ReaderPreview(book = ReaderBookState.Failed(LibraryUIError.Failed(LibraryError.Unreachable)))
}

@PreviewLightDark
@Composable
private fun ReaderScreenReadyPreview() {
    ReaderPreview(
        book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
        loadStatus = ReaderLoadStatus.Ready,
    )
}
