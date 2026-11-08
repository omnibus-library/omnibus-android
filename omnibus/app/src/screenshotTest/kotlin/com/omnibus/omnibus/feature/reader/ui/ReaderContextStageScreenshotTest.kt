package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.ReaderMenuRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme

// The page is a plain ground with its labels, since a WebView can't run in a preview. Each shot is the zoomed-out card
// with the top bar, and the Black shot also has the dropdown open.

/** A mid-book location, as the chrome's shots use. */
private val MidBookLocation = RelocateData(page = 361, totalPages = 661, pct = 54, chapter = 3, totalChapters = 12, chapterPagesLeft = 7)

/** The first page of the book, so there is no previous neighbour to draw. */
private val FirstPageLocation = RelocateData(page = 1, totalPages = 661, pct = 0, chapter = 1, totalChapters = 12, chapterPagesLeft = 40)

private val StageRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Bookmarks, ReaderMenuRow.Settings)

/** A phone-shaped card with both neighbours peeking in, in the Light reading theme. */
@PreviewTest
@Preview(widthDp = 393, heightDp = 852)
@Composable
fun ReaderContextPhoneLight() {
    ContextShot(theme = ReaderTheme.Light, location = MidBookLocation)
}

/** A square-shaped card on the first page, so only the next neighbour shows, in Sepia. */
@PreviewTest
@Preview(widthDp = 700, heightDp = 728)
@Composable
fun ReaderContextSquareSepiaFirstPage() {
    ContextShot(theme = ReaderTheme.Sepia, location = FirstPageLocation)
}

/** A phone-shaped card with the flat dropdown open under the more button, in the Black reading theme. */
@PreviewTest
@Preview(widthDp = 393, heightDp = 852)
@Composable
fun ReaderContextBlackMenuOpen() {
    ContextShot(theme = ReaderTheme.Black, location = MidBookLocation, menuOpen = true)
}

/** The zoomed-out stage over a plain page in [theme], with the chrome up and reduced motion so nothing is mid-flight. */
@Composable
private fun ContextShot(
    theme: ReaderTheme,
    location: RelocateData,
    menuOpen: Boolean = false,
) {
    val colors = readerChromeColors(theme)
    OmnibusTheme(darkTheme = !theme.isLight) {
        Box(Modifier.fillMaxSize()) {
            ReaderContextStage(
                contextMode = true,
                peeks = contextPeeks(location),
                colors = colors,
                ground = Color(theme.groundArgb),
                reduceMotion = true,
                insets = WindowInsets(0),
                onLeave = {},
            ) {
                Box(Modifier.fillMaxSize().background(Color(theme.groundArgb))) {
                    ReaderPageLabels(
                        indicators = ReaderIndicators(location = location, title = "Moby-Dick", chromeVisible = false),
                        colors = colors,
                        reduceMotion = true,
                        insets = WindowInsets(0),
                    )
                }
            }
            ReaderChrome(
                title = "Moby-Dick",
                chromeVisible = true,
                menuOpen = menuOpen,
                menuRows = StageRows,
                colors = colors,
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
