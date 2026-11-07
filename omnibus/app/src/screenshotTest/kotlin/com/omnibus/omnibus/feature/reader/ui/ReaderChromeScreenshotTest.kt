package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.ReaderMenuRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme

// The chrome takes its colours from the reading theme, never the app's, so each state has one shot per page theme.

/** A mid-book location: page 361 of 661, seven pages left in chapter 3 of 12, 54%. */
private val Reading = RelocateData(page = 361, totalPages = 661, pct = 54, chapter = 3, totalChapters = 12, chapterPagesLeft = 7)

private val AllRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Bookmarks, ReaderMenuRow.Settings)

/** Only the title and the bare page, with no buttons. */
@PreviewTest
@Preview
@Composable
fun ReaderChromeHidden() {
    ChromeShot(chromeVisible = false, menuRows = listOf(ReaderMenuRow.Contents))
}

/** Close and menu buttons, with the pages left in the chapter and the page of total. */
@PreviewTest
@Preview
@Composable
fun ReaderChromeVisible() {
    ChromeShot(chromeVisible = true, menuRows = listOf(ReaderMenuRow.Contents))
}

/** No menu rows, so no menu button. */
@PreviewTest
@Preview
@Composable
fun ReaderChromeVisibleWithoutMenu() {
    ChromeShot(chromeVisible = true, menuRows = emptyList())
}

/** Rows stacked above the menu button in list order, Contents with 54%, Bookmarks with its count, and Add bookmark. */
@PreviewTest
@Preview
@Composable
fun ReaderChromeMenuOpen() {
    ChromeShot(chromeVisible = true, menuOpen = true, menuRows = AllRows, bookmarkCount = 3)
}

/** A reversed list reverses the rows. Contents reads 0%, Bookmarks has no count, and the page is bookmarked. */
@PreviewTest
@Preview
@Composable
fun ReaderChromeMenuOpenReversed() {
    ChromeShot(
        chromeVisible = true,
        menuOpen = true,
        menuRows = AllRows.reversed(),
        percent = 0,
        pageBookmarked = true,
    )
}

@PreviewTest
@Preview
@Composable
fun ReaderChromeMenuOpenDark() {
    ChromeShot(chromeVisible = true, menuOpen = true, menuRows = AllRows, bookmarkCount = 3, theme = ReaderTheme.Dark)
}

/** The chrome over a plain page in [theme], as the reader draws it, with reduced motion so nothing is mid-flight. */
@Composable
private fun ChromeShot(
    chromeVisible: Boolean,
    menuRows: List<ReaderMenuRow>,
    menuOpen: Boolean = false,
    percent: Int = Reading.pct,
    bookmarkCount: Int = 0,
    pageBookmarked: Boolean = false,
    theme: ReaderTheme = ReaderTheme.Light,
) {
    OmnibusTheme(darkTheme = !theme.isLight) {
        Box(Modifier.background(Color(theme.groundArgb))) {
            ReaderChrome(
                indicators = ReaderIndicators(location = Reading, title = "Nineteen Eighty-Four", chromeVisible = chromeVisible),
                chromeVisible = chromeVisible,
                menuOpen = menuOpen,
                menuRows = menuRows,
                percent = percent,
                colors = readerChromeColors(theme),
                reduceMotion = true,
                insets = WindowInsets(0),
                onClose = {},
                onMenuOpened = {},
                onMenuDismissed = {},
                onMenuRowChosen = {},
                bookmarkCount = bookmarkCount,
                pageBookmarked = pageBookmarked,
            )
        }
    }
}
