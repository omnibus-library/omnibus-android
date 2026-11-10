package com.omnibus.omnibus.feature.shelves.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.feature.library.LibraryUIError
import com.omnibus.omnibus.feature.shelves.ShelvesUIState
import com.omnibus.omnibus.screenshot.Shot

/**
 * A phone-width admin's view: the viewer's own group first, then another reader's group with a private shelf, so the
 * census, the notice, both headings and the tiles all show.
 */
@PreviewTest
@Preview(name = "Light", widthDp = 390, heightDp = 1100)
@Preview(name = "Dark", widthDp = 390, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ShelvesScreenContentPreview() {
    Shot {
        ShelvesScreenContent(
            uiState = ShelvesUIState(
                previews = listOf(
                    shelf(1L, "Compiler corner", ShelfKind.Manual, ShelfVisibility.Private, 3L, covers = 3),
                    shelf(2L, "Kobo library", ShelfKind.Smart, ShelfVisibility.Private, 62L, covers = 4),
                    shelf(3L, "To lend out", ShelfKind.Manual, ShelfVisibility.Private, 0L, covers = 0),
                    shelf(4L, "Weekend reads", ShelfKind.Manual, ShelfVisibility.Public, 5L, covers = 4),
                    shelf(5L, "Cozy mysteries", ShelfKind.Manual, ShelfVisibility.Private, 12L, covers = 4, ownerId = 8L, ownerName = "maya"),
                    shelf(6L, "Maya’s wishlist", ShelfKind.Wishlist, ShelfVisibility.Private, 0L, covers = 0, ownerId = 8L, ownerName = "maya"),
                ),
                viewerId = 7L,
            ),
            onBack = {},
            onRefresh = {},
            onRetry = {},
            onTransientErrorShown = {},
        )
    }
}

/**
 * The three states under the top bar: loading, the full-screen error with its retry, and the empty view with no action.
 */
@PreviewTest
@Preview(name = "Light", widthDp = 1000, heightDp = 560)
@Preview(name = "Dark", widthDp = 1000, heightDp = 560, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ShelvesScreenStates() {
    Shot {
        Row {
            Box(Modifier.weight(1f)) {
                ShelvesScreenContent(
                    uiState = ShelvesUIState(isInitialLoading = true),
                    onBack = {},
                    onRefresh = {},
                    onRetry = {},
                    onTransientErrorShown = {},
                )
            }
            Box(Modifier.weight(1f)) {
                ShelvesScreenContent(
                    uiState = ShelvesUIState(error = LibraryUIError.Failed(LibraryError.Unreachable)),
                    onBack = {},
                    onRefresh = {},
                    onRetry = {},
                    onTransientErrorShown = {},
                )
            }
            Box(Modifier.weight(1f)) {
                ShelvesScreenContent(
                    uiState = ShelvesUIState(),
                    onBack = {},
                    onRefresh = {},
                    onRetry = {},
                    onTransientErrorShown = {},
                )
            }
        }
    }
}

/** A shelf for the screenshots, with [covers] cover uuids that load nothing, so each cover draws as a plate. */
private fun shelf(
    id: Long,
    name: String,
    kind: ShelfKind,
    visibility: ShelfVisibility,
    bookCount: Long,
    covers: Int,
    ownerId: Long = 7L,
    ownerName: String = "hunter",
) = ShelfPreview(
    shelf = Shelf(
        id = id,
        ownerUserId = ownerId,
        ownerUsername = ownerName,
        kind = kind,
        name = name,
        visibility = visibility,
        accent = null,
        bookCount = bookCount,
    ),
    coverUuids = List(covers) { "preview-$id-$it" },
)

/**
 * The large bar collapsed into its row over three shelves, so the 22sp display title sits beside the back arrow.
 */
@PreviewTest
@Preview(name = "Light", widthDp = 390, heightDp = 500)
@Preview(name = "Dark", widthDp = 390, heightDp = 500, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ShelvesScreenCollapsed() {
    Shot {
        val limit = with(LocalDensity.current) {
            -(shelvesBarExpandedHeight() - TopAppBarDefaults.LargeAppBarCollapsedHeight).toPx()
        }
        val topBarState = remember(limit) { TopAppBarState(limit, limit, 0f) }
        ShelvesScreenContent(
            uiState = ShelvesUIState(
                previews = listOf(
                    shelf(1L, "Compiler corner", ShelfKind.Manual, ShelfVisibility.Private, 3L, covers = 3),
                    shelf(2L, "Kobo library", ShelfKind.Smart, ShelfVisibility.Private, 62L, covers = 4),
                    shelf(4L, "Weekend reads", ShelfKind.Manual, ShelfVisibility.Public, 5L, covers = 4),
                ),
                viewerId = 7L,
            ),
            onBack = {},
            onRefresh = {},
            onRetry = {},
            onTransientErrorShown = {},
            topBarState = topBarState,
        )
    }
}
