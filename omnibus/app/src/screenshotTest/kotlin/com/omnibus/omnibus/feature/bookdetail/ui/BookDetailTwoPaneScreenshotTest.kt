package com.omnibus.omnibus.feature.bookdetail.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
    // The Read pill's 52 dp and its 10 dp padding above and below: the height the action area settles at.
    TwoPaneShot(actionAreaHeight = 72.dp)
}

/**
 * The page at the preview's own size. The forced layout comes from the screen's rule, not from this file.
 *
 * @param actionAreaHeight The action area's settled height, which a single-pass render never measures.
 */
@Composable
private fun TwoPaneShot(actionAreaHeight: Dp = 0.dp) {
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
            initialActionAreaHeight = actionAreaHeight,
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
