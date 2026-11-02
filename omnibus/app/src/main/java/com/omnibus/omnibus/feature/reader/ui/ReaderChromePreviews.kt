package com.omnibus.omnibus.feature.reader.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.feature.reader.ReaderMenuRow
import com.omnibus.omnibus.feature.reader.ReaderUIState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow

private val PreviewLocation = RelocateData(
    page = 361,
    totalPages = 661,
    pct = 54,
    chapter = 3,
    totalChapters = 12,
    chapterPagesLeft = 7,
)

/** Before epub.js has its locations: no page numbers, so the bottom label falls back to the chapter and percent. */
private val FallbackLocation = RelocateData(
    page = 1,
    totalPages = 0,
    pct = 18,
    chapter = 3,
    totalChapters = 12,
    chapterPagesLeft = 0,
)

/**
 * The reader's chrome over a plain page, for previews and tests. The page is a flat ground, since the
 * WebView doesn't run in a preview.
 */
@Composable
internal fun ReaderChromePreview(
    theme: ReaderTheme,
    chromeVisible: Boolean,
    menuOpen: Boolean = false,
    menuRows: List<ReaderMenuRow> = emptyList(),
    location: RelocateData = PreviewLocation,
    appDark: Boolean = false,
) {
    OmnibusTheme(darkTheme = appDark) {
        ReaderScreenContent(
            uiState = ReaderUIState(
                book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
                loadStatus = ReaderLoadStatus.Ready,
                location = location,
                chromeVisible = chromeVisible,
                menuOpen = menuOpen,
                menuRows = menuRows,
                settings = ReaderSettings(theme = theme),
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
            onMenuOpened = {},
            onMenuDismissed = {},
            onMenuRowChosen = {},
            onRetryBookRequested = {},
            onReaderClosed = {},
            onClose = {},
        )
    }
}

@Preview(name = "Light hidden", widthDp = 393, heightDp = 852)
@Composable
private fun LightHiddenPreview() = ReaderChromePreview(ReaderTheme.Light, chromeVisible = false)

@Preview(name = "Light visible", widthDp = 393, heightDp = 852)
@Composable
private fun LightVisiblePreview() = ReaderChromePreview(ReaderTheme.Light, chromeVisible = true)

@Preview(name = "Sepia hidden", widthDp = 393, heightDp = 852)
@Composable
private fun SepiaHiddenPreview() = ReaderChromePreview(ReaderTheme.Sepia, chromeVisible = false)

@Preview(name = "Sepia visible", widthDp = 393, heightDp = 852)
@Composable
private fun SepiaVisiblePreview() = ReaderChromePreview(ReaderTheme.Sepia, chromeVisible = true)

@Preview(name = "Dark hidden", widthDp = 393, heightDp = 852)
@Composable
private fun DarkHiddenPreview() = ReaderChromePreview(ReaderTheme.Dark, chromeVisible = false)

@Preview(name = "Dark visible", widthDp = 393, heightDp = 852)
@Composable
private fun DarkVisiblePreview() = ReaderChromePreview(ReaderTheme.Dark, chromeVisible = true)

@Preview(name = "Black hidden", widthDp = 393, heightDp = 852)
@Composable
private fun BlackHiddenPreview() = ReaderChromePreview(ReaderTheme.Black, chromeVisible = false)

@Preview(name = "Black visible", widthDp = 393, heightDp = 852)
@Composable
private fun BlackVisiblePreview() = ReaderChromePreview(ReaderTheme.Black, chromeVisible = true)

@Preview(name = "Menu open Light", widthDp = 393, heightDp = 852)
@Composable
private fun MenuOpenLightPreview() = ReaderChromePreview(
    ReaderTheme.Light,
    chromeVisible = true,
    menuOpen = true,
    menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Settings),
)

@Preview(name = "Menu open Dark", widthDp = 393, heightDp = 852)
@Composable
private fun MenuOpenDarkPreview() = ReaderChromePreview(
    ReaderTheme.Dark,
    chromeVisible = true,
    menuOpen = true,
    menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Settings),
)

@Preview(name = "Light page, app dark", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LightPageAppDarkPreview() = ReaderChromePreview(
    ReaderTheme.Light,
    chromeVisible = true,
    menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Settings),
    appDark = true,
)

@Preview(name = "Fallback hidden", widthDp = 393, heightDp = 852)
@Composable
private fun FallbackHiddenPreview() = ReaderChromePreview(
    ReaderTheme.Light,
    chromeVisible = false,
    location = FallbackLocation,
)

@Preview(name = "Fallback visible", widthDp = 393, heightDp = 852)
@Composable
private fun FallbackVisiblePreview() = ReaderChromePreview(
    ReaderTheme.Light,
    chromeVisible = true,
    location = FallbackLocation,
)
