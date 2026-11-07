package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.feature.library.LibraryUIError
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.feature.reader.ReaderUIState
import com.omnibus.omnibus.feature.reader.ReaderUnavailableReason
import com.omnibus.omnibus.screenshot.Shot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow

/** A ready book still opening: "Opening Moby Dick", once. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReaderScreenOpening() {
    ScreenShot(ReaderBookState.Ready(title = "Moby Dick"))
}

@PreviewTest
@PreviewLightDark
@Composable
fun ReaderScreenNotFound() {
    ScreenShot(ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound))
}

/** The library's message for an unreachable server, with Try again. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReaderScreenFailed() {
    ScreenShot(ReaderBookState.Failed(LibraryUIError.Failed(LibraryError.Unreachable)))
}

/** The screen on a page theme that follows the preview's light or dark mode. */
@Composable
private fun ScreenShot(book: ReaderBookState) {
    val theme = if (isSystemInDarkTheme()) ReaderTheme.Dark else ReaderTheme.Light
    Shot {
        ReaderScreenContent(
            uiState = ReaderUIState(book = book, loadStatus = ReaderLoadStatus.Loading, settings = ReaderSettings(theme = theme)),
            bookUuid = "book-1",
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
}
