package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.ReaderMenuRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme

// The chrome takes its colours from the reading theme, never the app's, so each state has one shot per page theme.

/** A mid-book location: page 361 of 661, seven pages left in chapter 3 of 12, 54%. */
private val Reading = RelocateData(page = 361, totalPages = 661, pct = 54, chapter = 3, totalChapters = 12, chapterPagesLeft = 7)

private val AllRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Bookmarks, ReaderMenuRow.Settings)

/** The top bar in context mode: back, title, bookmark toggle and more, with the page labels under it. */
@PreviewTest
@Preview
@Composable
fun ReaderContextBar() {
    ChromeShot(chromeVisible = true, menuRows = AllRows)
}

/** The flat dropdown open under the more button, in the Dark reading theme. */
@PreviewTest
@Preview
@Composable
fun ReaderContextMenuOpen() {
    ChromeShot(chromeVisible = true, menuOpen = true, menuRows = AllRows, theme = ReaderTheme.Dark)
}

/** The same dropdown in a right-to-left layout, in Sepia: the back arrow mirrors and the dropdown hangs from the start. */
@PreviewTest
@Preview
@Composable
fun ReaderContextMenuOpenReversed() {
    ChromeShot(chromeVisible = true, menuOpen = true, menuRows = AllRows, theme = ReaderTheme.Sepia, rtl = true)
}

/** The page labels and the chrome over a plain page in [theme], as the reader draws them, with reduced motion so nothing is mid-flight. */
@Composable
private fun ChromeShot(
    chromeVisible: Boolean,
    menuRows: List<ReaderMenuRow>,
    menuOpen: Boolean = false,
    location: RelocateData = Reading,
    theme: ReaderTheme = ReaderTheme.Light,
    rtl: Boolean = false,
) {
    val direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        OmnibusTheme(darkTheme = !theme.isLight) {
            Box(Modifier.background(Color(theme.groundArgb))) {
                ReaderPageLabels(
                    indicators = ReaderIndicators(location = location, title = "Nineteen Eighty-Four", chromeVisible = false),
                    colors = readerChromeColors(theme),
                    reduceMotion = true,
                    insets = WindowInsets(0),
                )
                ReaderChrome(
                    title = "Nineteen Eighty-Four",
                    chromeVisible = chromeVisible,
                    menuOpen = menuOpen,
                    menuRows = menuRows,
                    colors = readerChromeColors(theme),
                    reduceMotion = true,
                    insets = WindowInsets(0),
                    onClose = {},
                    onMenuOpened = {},
                    onMenuDismissed = {},
                    onMenuRowChosen = {},
                    pageBookmarked = false,
                )
            }
        }
    }
}
