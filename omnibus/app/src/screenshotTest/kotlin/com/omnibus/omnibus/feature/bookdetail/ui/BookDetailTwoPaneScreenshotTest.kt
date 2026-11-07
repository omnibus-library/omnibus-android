package com.omnibus.omnibus.feature.bookdetail.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.shotBook
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.screenshot.Shot

/** A landscape phone: the leading pane is under the bar, the cover at the 0.5 floor, and the strip on the right. */
@PreviewTest
@Preview(name = "Light", widthDp = 800, heightDp = 360)
@Preview(name = "Dark", widthDp = 800, heightDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BookDetailTwoPaneLandscapePhone() {
    TwoPaneShot()
}

/** A tablet in landscape, with the cover at full scale. */
@PreviewTest
@Preview(name = "Light", widthDp = 1280, heightDp = 800)
@Preview(name = "Dark", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BookDetailTwoPaneTablet() {
    TwoPaneShot()
}

/** A short window: the cover clamps to the 0.5 floor under the bar, and the title scrolls under the action row. */
@PreviewTest
@Preview(name = "Light", widthDp = 900, heightDp = 300)
@Preview(name = "Dark", widthDp = 900, heightDp = 300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BookDetailTwoPaneShortWindow() {
    TwoPaneShot()
}

/** A narrow leading pane (269 dp): the stage starts below the bar row, so the cover clears the Back disc. */
@PreviewTest
@Preview(name = "Light", widthDp = 640, heightDp = 360)
@Preview(name = "Dark", widthDp = 640, heightDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BookDetailTwoPaneNarrowLeading() {
    TwoPaneShot()
}

/** The page at the preview's own size. The forced layout comes from the screen's rule, not from this file. */
@Composable
private fun TwoPaneShot() {
    Shot {
        BookDetailScreenContent(
            uiState = BookDetailUIState(
                book = shotBook(
                    series = "Red Rising Saga",
                    seriesIndex = "3",
                    description = "Darrow would have lived in peace, but his enemies brought him war. The Gold overlords " +
                        "demanded his obedience, hanged his wife, and enslaved his people.",
                ),
                isLoading = false,
            ),
            onNavigate = {},
            onClose = {},
            onRetry = {},
            onRefresh = {},
            onReloadBook = {},
            onBookChanged = {},
            onTransientErrorShown = {},
            slots = previewSlots(),
            reducedMotion = true,
        )
    }
}

/** Stateless stand-ins for the real sections: a status pill, a progress line and an overview with a panel. */
private fun previewSlots(): BookDetailSlots = BookDetailSlots(
    stageStatusStart = listOf(BookDetailSection { _, modifier -> Text("Reading", modifier = modifier) }),
    stageProgress = listOf(BookDetailSection { _, modifier -> Text("In progress", modifier = modifier) }),
    overviewSections = listOf(
        BookDetailSection { _, modifier ->
            Text(
                "Darrow would have lived in peace, but his enemies brought him war. The Gold overlords demanded his " +
                    "obedience, hanged his wife, and enslaved his people. But Darrow is determined to fight back.",
                modifier = modifier,
            )
        },
        BookDetailSection { _, modifier ->
            Box(modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceVariant))
        },
    ),
)
