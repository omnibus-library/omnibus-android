package com.omnibus.omnibus.feature.reader.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.feature.library.LibraryUIError
import com.omnibus.omnibus.feature.library.ui.libraryErrorText
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.feature.reader.ReaderCommand
import com.omnibus.omnibus.feature.reader.ReaderUIState
import com.omnibus.omnibus.feature.reader.ReaderUnavailableReason
import com.omnibus.omnibus.feature.reader.ReaderViewModel
import com.omnibus.omnibus.ui.components.ErrorStateView
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

/**
 * The reader's destination. It goes on the root back stack, not a tab stack, so the reader covers
 * the shell and leaving it returns to the shell with its tab stacks intact.
 *
 * @property bookUuid The book to open.
 */
@Serializable
data class ReaderRoute(val bookUuid: String) : NavKey

/**
 * The reader for one book, over the shell. The ViewModel is created for [bookUuid] and is the only
 * thing here that sees its state. [onClose] leaves the reader.
 */
@Composable
fun ReaderScreen(
    bookUuid: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel<ReaderViewModel, ReaderViewModel.Factory>(
        creationCallback = { it.create(bookUuid) },
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    // Starts at the beginning of the book. Resuming from a saved position is #49.
    val controller = rememberReaderController()

    ReaderScreenContent(
        uiState = uiState,
        bookUuid = bookUuid,
        controller = controller,
        assetLoaderFactory = viewModel.assetLoaderFactory,
        commands = viewModel.commands,
        onPageEvent = viewModel::onPageEvent,
        onSheetDismissed = viewModel::onSheetDismissed,
        onRetryBookRequested = viewModel::onRetryBookRequested,
        onReaderClosed = viewModel::onReaderClosed,
        onClose = onClose,
        modifier = modifier,
    )
}

/**
 * The reader's screen with no ViewModel: everything it shows comes from [uiState] and the
 * callbacks, so tests and previews can drive it directly.
 *
 * Nothing here pads the page for the system bars. The reader fills the window, and the page takes
 * the insets as CSS variables, so only the state views are inset.
 *
 * @param commands page commands from the ViewModel, each applied to [controller] once.
 * @param onPageEvent receives every event the page reports, and the host's load status.
 * @param onClose called after [onReaderClosed] when the reader is left.
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
    onRetryBookRequested: () -> Unit,
    onReaderClosed: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val leave = {
        onReaderClosed()
        onClose()
    }
    // Always enabled, so back takes this path in every state, and it outranks the root NavDisplay's handler.
    BackHandler {
        if (uiState.sheet != null) onSheetDismissed() else leave()
    }

    LaunchedEffect(controller, commands) {
        commands.collect { controller.execute(it) }
    }

    val theme = uiState.settings.theme
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
    OmnibusTheme(darkTheme = !theme.isLight) {
        Box(
            modifier
                .fillMaxSize()
                .background(ground)
                .testTag(ReaderScreenTags.ROOT),
        ) {
            // Inset so the message clears the bars, with the ground painted behind them.
            val stateModifier = Modifier.fillMaxSize().windowInsetsPadding(ReaderWebViewDefaults.insets)
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
                    if (LocalInspectionMode.current) {
                        // The WebView can't run in a preview, so the page is a plain ground.
                        Box(Modifier.fillMaxSize().background(ground).testTag(ReaderScreenTags.PREVIEW_PAGE))
                    } else {
                        ReaderWebView(
                            bookUuid = bookUuid,
                            settings = uiState.settings,
                            controller = controller,
                            assetLoaderFactory = assetLoaderFactory,
                            modifier = Modifier.fillMaxSize(),
                            onEvent = onPageEvent,
                        )
                    }
                    if (uiState.loadStatus == ReaderLoadStatus.Loading) {
                        LoadingStateView(
                            label = stringResource(R.string.reader_opening_title, book.title),
                            modifier = stateModifier,
                            background = ground,
                        )
                    }
                }
            }
        }
    }
}

/** Applies one [ReaderCommand] to the page. */
internal fun ReaderController.execute(command: ReaderCommand) = when (command) {
    is ReaderCommand.Display -> display(command.target)
}

/** Test tags on the reader's own nodes, so tests can find them without depending on the layout. */
internal object ReaderScreenTags {
    const val ROOT = "reader_root"
    const val PREVIEW_PAGE = "reader_preview_page"
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
        onRetryBookRequested = {},
        onReaderClosed = {},
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
